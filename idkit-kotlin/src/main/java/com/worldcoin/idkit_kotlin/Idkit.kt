package com.worldcoin.idkit_kotlin

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.json.Json
import org.kotlincrypto.hash.sha3.Keccak256
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.UUID
import java.util.Base64
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

object UUIDSerializer : KSerializer<UUID> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("UUID", PrimitiveKind.STRING)

    override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: UUID) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): UUID {
        return UUID.fromString(decoder.decodeString())
    }
}


sealed class Status {
    object WaitingForConnection : Status()
    object AwaitingConfirmation : Status()
    data class Confirmed(val proof: Proof) : Status()
    data class Failed(val error: AppError) : Status()

    // Optional: Override equals to match Swift's equality behavior
    override fun equals(other: Any?): Boolean {
        return when {
            this === other -> true
            this::class != other?.let { it::class } -> false
            else -> true
        }
    }

    override fun hashCode(): Int {
        return this::class.hashCode()
    }
}

@Serializable
class Session(
    @Serializable(with = UUIDSerializer::class)  private val requestID: UUID,
    private val key: SecretKey,
    private val bridgeURL: BridgeURL
) {

    /// The URL that the user should be directed to in order to connect their World App to the client.
    val connectUrl: URL
        get() {
            val queryParams = mutableListOf<Pair<String, String>>(
                "t" to "wld",
                "i" to requestID.toString(),
                "k" to Base64.getEncoder().encodeToString(key.encoded)
            )

            if (bridgeURL != BridgeURL.default) {
                queryParams.add("b" to bridgeURL.rawURL)
            }

            val queryString = queryParams.joinToString("&") { (key, value) ->
                "${URLEncoder.encode(key, StandardCharsets.UTF_8.toString())}=${URLEncoder.encode(value, StandardCharsets.UTF_8.toString())}"
            }

            return URL("https://worldcoin.org/verify?$queryString")
        }

    companion object {
        suspend fun create(
            appID: AppID,
            action: String,
            verificationLevel: VerificationLevel = VerificationLevel.ORB,
            bridgeURL: BridgeURL = BridgeURL.default,
            signal: String = "",
            actionDescription: String? = null
        ): Session {
            val keyBytes = ByteArray(32).apply { SecureRandom().nextBytes(this) }
            val key: SecretKey = SecretKeySpec(keyBytes, "AES")

            val iv = ByteArray(12).apply { SecureRandom().nextBytes(this) }

            val payload = CreateRequestPayload(
                appID = appID,
                action = action,
                signal = encodeSignal(signal),
                actionDescription = actionDescription,
                verificationLevel = verificationLevel
            )

            val response = BridgeClient.createRequest(payload.encrypt(key, iv), bridgeURL)

            return Session(response.request_id, key, bridgeURL)
        }
    }

    fun status(): Flow<Status> = flow {
        var currentStatus: Status = Status.WaitingForConnection
        emit(currentStatus)

        val requestUrl = URL("${bridgeURL.rawURL}/response/$requestID")

        while (true) {
            try {
                val connection = requestUrl.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"

                val responseStream = connection.inputStream.bufferedReader().readText()

                val bridgeResponse = Json.decodeFromString<BridgeQueryResponse>(responseStream)
                if (bridgeResponse.status == "completed") {
                    val payload = bridgeResponse.response ?: throw AppError.UnexpectedResponse
                    when (val decryptedResponse = payload.decrypt(key)) {
                        is BridgeResponse.Error -> {
                            emit(Status.Failed(decryptedResponse.error))
                            break
                        }
                        is BridgeResponse.Success -> {
                            emit(Status.Confirmed(decryptedResponse.proof))
                            break
                        }

                        else -> {}
                    }
                }

                val status = when (bridgeResponse.status) {
                    "retrieved" -> Status.AwaitingConfirmation
                    "initialized" -> Status.WaitingForConnection
                    else -> throw AppError.UnexpectedResponse
                }

                if (status != currentStatus) {
                    currentStatus = status
                    emit(currentStatus)
                }

                delay(3000)  // Wait for 3 seconds before polling again
            } catch (e: Exception) {
                emit(Status.Failed(AppError.GenericError))
                break
            }
        }
    }.flowOn(Dispatchers.IO)

}

fun encodeSignal(signal: String): String {
    val bytes = signal.toByteArray()
    val keccak256 = bytes.keccak256()
    return "0x" + BigInteger(1, keccak256).shiftRight(8).toString(16)
}

// Placeholder for cryptographic hashing
fun ByteArray.keccak256(): ByteArray {
    return Keccak256().digest(this) // Placeholder
}
