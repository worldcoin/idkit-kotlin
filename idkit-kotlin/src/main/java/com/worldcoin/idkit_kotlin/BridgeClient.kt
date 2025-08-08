package com.worldcoin.idkit_kotlin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

@Serializable(with = BridgeResponseSerializer::class)
sealed class BridgeResponse {
    @Serializable
    data class Success(val proof: Proof) : BridgeResponse()

    @Serializable
    data class Error(val error: AppError) : BridgeResponse()
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
