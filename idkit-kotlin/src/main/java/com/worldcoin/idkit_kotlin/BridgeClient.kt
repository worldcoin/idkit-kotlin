package com.worldcoin.idkit_kotlin

import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.util.*
import java.net.URL
import java.net.HttpURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encodeToString
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = BridgeResponseSerializer::class)
sealed class BridgeResponse {
    @Serializable
    data class Success(val proof: Proof) : BridgeResponse()

    @Serializable
    data class Error(val error: AppError) : BridgeResponse()
}

object BridgeResponseSerializer : KSerializer<BridgeResponse> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("BridgeResponse") {
        element<Proof>("proof", isOptional = true)
        element<AppError>("error_code", isOptional = true)
    }

    override fun serialize(encoder: Encoder, value: BridgeResponse) {

    }

    override fun deserialize(decoder: Decoder): BridgeResponse {
        val jsonDecoder = decoder as? JsonDecoder ?: throw SerializationException("This class can be loaded only by JSON")
        val jsonObject = jsonDecoder.decodeJsonElement().jsonObject

        return when {
            "error_code" in jsonObject -> {
                val error = jsonDecoder.json.decodeFromJsonElement<AppError>(jsonObject["error_code"]!!)
                BridgeResponse.Error(error)
            }
            "proof" in jsonObject -> {
                val proof = jsonDecoder.json.decodeFromJsonElement<Proof>(jsonObject)
                BridgeResponse.Success(proof)
            }
            else -> throw SerializationException("BridgeResponse doesn't match any expected type")
        }
    }
}

@Serializable
data class CreateRequestResponse(@Serializable(with = UUIDSerializer::class) val request_id: UUID)

@Serializable
data class BridgeQueryResponse(val status: String, val response: Payload?)

object BridgeClient {
    suspend fun createRequest(data: Payload, bridgeURL: BridgeURL): CreateRequestResponse {
        return withContext(Dispatchers.IO) {
            val url = URL("${bridgeURL.rawURL}/request")
            val request = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("User-Agent", "idkit-kotlin")
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
            }

            request.outputStream.use { os ->
                os.write(Json.encodeToString(data).toByteArray())
            }

            val response = request.inputStream.bufferedReader().use { it.readText() }
            Json.decodeFromString(response)
        }
    }
}

