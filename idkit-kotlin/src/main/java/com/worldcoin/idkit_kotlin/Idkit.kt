package com.worldcoin.idkit_kotlin

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
import java.io.IOException
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

private const val TAG = "IdKit-Kotlin"
private const val POLLING_INTERVAL_3000_MS = 3000L
private const val POLLING_ERROR_INTERVAL_5000_MS = 5000L
// Session remains active on the Bridge for 15 minutes before expiring
private const val SESSION_TIMEOUT_15_MIN = 15 * 60 * 1000L
private const val CONNECTION_TIMEOUT_MS = 30_000
private const val READ_TIMEOUT_MS = 30_000

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
    private val bridgeURL: BridgeURL,
    private val connectUrlType: ConnectUrlType,
) {
    /// The URL that the user should be directed to in order to connect their World App to the client.
    val connectUrl: URL
        get() {
            val queryParams = mutableListOf<Pair<String, String>>(
                "t" to connectUrlType.type,
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
            val payload = CreateRequestPayload(
                appID = appID,
                action = action,
                signal = encodeSignal(signal),
                actionDescription = actionDescription,
                verificationLevel = verificationLevel
            )

            return createSessionInternal(payload, bridgeURL, ConnectUrlType.WLD)
        }

        suspend fun createCredentialCategorySession(
            appID: AppID,
            action: String,
            credentialCategory: Set<CredentialCategory>,
            bridgeURL: BridgeURL = BridgeURL.default,
            signal: String = "",
            actionDescription: String? = null
        ): Session {
            val payload = CreateCredentialCategoryRequestPayload(
                appID = appID,
                action = action,
                signal = encodeSignal(signal),
                actionDescription = actionDescription,
                credentialCategory = credentialCategory,
            )

            return createSessionInternal(payload, bridgeURL, ConnectUrlType.CREDENTIAL_CATEGORY)
        }

        private suspend fun createSessionInternal(
            payload: EncryptablePayload,
            bridgeURL: BridgeURL,
            connectUrlType: ConnectUrlType,
        ): Session {
            val keyBytes = ByteArray(32).apply { SecureRandom().nextBytes(this) }
            val key: SecretKey = SecretKeySpec(keyBytes, "AES")

            val iv = ByteArray(12).apply { SecureRandom().nextBytes(this) }

            val encryptedPayload = payload.encryptPayload(key, iv)
            val response = BridgeClient.createRequest(encryptedPayload, bridgeURL)

            return Session(response.request_id, key, bridgeURL, connectUrlType)
        }
    }

    /**
     * Returns a Flow that emits the current status of the verification session.
     *
     * Note: Avoid collecting this Flow multiple times on the same Session instance,
     * as each collector will create a separate polling loop to the Bridge.
     */
    fun status(): Flow<Status> = flow {
        var currentStatus: Status = Status.WaitingForConnection
        emit(currentStatus)

        val requestUrl = URL("${bridgeURL.rawURL}/response/$requestID")
        val sessionStartTime = System.currentTimeMillis()

        while (true) {
            if (isSessionTimedOut(sessionStartTime)) {
                Log.w(TAG, "Session timed out")
                emit(Status.Failed(AppError.ConnectionFailed))
                break
            }

            when (val pollResult = pollBridgeOnce(requestUrl)) {
                is PollResult.Success -> {
                    val newStatus = pollResult.status
                    if (newStatus != currentStatus) {
                        currentStatus = newStatus
                        emit(currentStatus)
                    }

                    if (newStatus is Status.Confirmed || newStatus is Status.Failed) {
                        break
                    }
                    delay(POLLING_INTERVAL_3000_MS)
                }

                is PollResult.RecoverableError -> {
                    // Network/HTTP error - retry silently (Android 15+ background restrictions)
                    Log.d(TAG, "Network error (will retry): ${pollResult.message}")
                    delay(POLLING_ERROR_INTERVAL_5000_MS)
                }

                is PollResult.FatalError -> {
                    Log.w(TAG, "Fatal error: ${pollResult.message}")
                    emit(Status.Failed(AppError.GenericError(pollResult.message)))
                    break
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun isSessionTimedOut(sessionStartTime: Long): Boolean {
        return System.currentTimeMillis() - sessionStartTime > SESSION_TIMEOUT_15_MIN
    }

    private fun pollBridgeOnce(requestUrl: URL): PollResult {
        var connection: HttpURLConnection? = null
        return try {
            connection = (requestUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECTION_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
            }

            val responseStream = connection.inputStream.bufferedReader().readText()
            val bridgeResponse = Json.decodeFromString<BridgeQueryResponse>(responseStream)

            parseBridgeResponse(bridgeResponse)

        } catch (ex: IOException) {
            PollResult.RecoverableError(ex.message)
        } catch (ex: Exception) {
            PollResult.FatalError(ex.message)
        } finally {
            connection?.disconnect()
        }
    }

    private fun parseBridgeResponse(bridgeResponse: BridgeQueryResponse): PollResult {
        if (bridgeResponse.status == "completed") {
            val payload = bridgeResponse.response
                ?: return PollResult.FatalError("Unexpected response: missing payload")

            return when (val decryptedResponse = payload.decrypt(key)) {
                is BridgeResponse.Error -> PollResult.Success(Status.Failed(decryptedResponse.error))
                is BridgeResponse.Success -> PollResult.Success(Status.Confirmed(decryptedResponse.proof))
            }
        }

        val status = when (bridgeResponse.status) {
            "retrieved" -> Status.AwaitingConfirmation
            "initialized" -> Status.WaitingForConnection
            else -> return PollResult.FatalError("Unexpected status: ${bridgeResponse.status}")
        }

        return PollResult.Success(status)
    }

    private sealed class PollResult {
        data class Success(val status: Status) : PollResult()
        data class RecoverableError(val message: String?) : PollResult()
        data class FatalError(val message: String?) : PollResult()
    }
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
