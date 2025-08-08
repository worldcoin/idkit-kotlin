package com.worldcoin.idkit_kotlin

import android.util.Log
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal object BridgeResponseSerializer : KSerializer<BridgeResponse> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("BridgeResponse") {
        element<Proof>("proof", isOptional = true)
        element<AppError>("error_code", isOptional = true)
    }

    override fun serialize(encoder: Encoder, value: BridgeResponse) {

    }

    override fun deserialize(decoder: Decoder): BridgeResponse {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw SerializationException("This class can be loaded only by JSON")
        val jsonObject = jsonDecoder.decodeJsonElement().jsonObject

        return when {
            "error_code" in jsonObject -> {
                val error =
                    jsonDecoder.json.decodeFromJsonElement<AppError>(jsonObject["error_code"]!!)
                BridgeResponse.Error(error)
            }

            "proof" in jsonObject -> {
                val proof = if ("verification_credential_result" in jsonObject) {
                    jsonDecoder.json.decodeFromJsonElement<Proof.CredentialCategory>(jsonObject)
                } else {
                    jsonDecoder.json.decodeFromJsonElement<Proof.Default>(jsonObject)
                }

                BridgeResponse.Success(proof)
            }

            else -> throw SerializationException("BridgeResponse doesn't match any expected type")
        }
    }
}

internal object AppErrorSerializer : KSerializer<AppError> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("AppError", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: AppError) {
       encoder.encodeString(value.message)
    }

    override fun deserialize(decoder: Decoder): AppError {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw SerializationException("This class can be loaded only by JSON")
        val jsonPrimitive = jsonDecoder.decodeJsonElement().jsonPrimitive

        val errorString = jsonPrimitive.content

        return when (errorString) {
            "connection_failed" -> AppError.ConnectionFailed
            "verification_rejected" -> AppError.VerificationRejected
            "max_verifications_reached" -> AppError.MaxVerificationsReached
            "credential_unavailable" -> AppError.CredentialUnavailable
            "malformed_request" -> AppError.MalformedRequest
            "invalid_network" -> AppError.InvalidNetwork
            "inclusion_proof_failed" -> AppError.InclusionProofFailed
            "inclusion_proof_pending" -> AppError.InclusionProofPending
            "unexpected_response" -> AppError.UnexpectedResponse
            "failed_by_host_app" -> AppError.FailedByHostApp
            "generic_error" -> AppError.GenericError
            else -> {
                Log.w("IdKit-Kotlin", "Unknown error: $errorString")
                GenericAppError(errorString)
            }
        }
    }
}
