package com.worldcoin.idkit_kotlin

import kotlinx.serialization.*
import java.net.URL
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.serialization.json.Json
import java.util.Base64

@Serializable
data class Proof(
    val proof: String,
    @SerialName("merkle_root") val merkleRoot: String,
    @SerialName("nullifier_hash") val nullifierHash: String,
    @SerialName("credential_type") val credentialType: CredentialType
) {
    enum class CredentialType {
        @SerialName("orb") ORB,
        @SerialName("device") DEVICE
    }
}

enum class VerificationLevel {
    @SerialName("orb") ORB,
    @SerialName("device") DEVICE
}

@Serializable
sealed class AppError(override val message: String) : Throwable(message) {
    @Serializable
    @SerialName("connection_failed")
    object ConnectionFailed : AppError("Failed to connect to the World App. Please create a new session and try again.")

    @Serializable
    @SerialName("verification_rejected")
    object VerificationRejected : AppError("The user rejected the verification request in the World App.")

    @Serializable
    @SerialName("max_verifications_reached")
    object MaxVerificationsReached : AppError("The user already verified the maximum number of times for this action.")

    @Serializable
    @SerialName("credential_unavailable")
    object CredentialUnavailable : AppError("The user does not have the verification level required by this app.")

    @Serializable
    @SerialName("malformed_request")
    object MalformedRequest : AppError("There was a problem with this request. Please try again or contact the app owner.")

    @Serializable
    @SerialName("invalid_network")
    object InvalidNetwork : AppError("Invalid network. If you are the app owner, visit docs.worldcoin.org/test for details.")

    @Serializable
    @SerialName("inclusion_proof_failed")
    object InclusionProofFailed : AppError("There was an issue fetching the user's credential. Please try again.")

    @Serializable
    @SerialName("inclusion_proof_pending")
    object InclusionProofPending : AppError("The user's identity is still being registered. Please wait a few minutes and try again.")

    @Serializable
    @SerialName("unexpected_response")
    object UnexpectedResponse : AppError("Unexpected response from the user's World App. Please try again.")

    @Serializable
    @SerialName("failed_by_host_app")
    object FailedByHostApp : AppError("Verification failed by the app. Please contact the app owner for details.")

    @Serializable
    @SerialName("generic_error")
    object GenericError : AppError("Something unexpected went wrong. Please try again.")
}



@Serializable
data class CreateRequestPayload(
    @SerialName("app_id") val appId: String,
    val action: String,
    val signal: String,
    @SerialName("action_description") val actionDescription: String?,
    @SerialName("verification_level") val verificationLevel: VerificationLevel,
    @SerialName("credential_types") val credentialTypes: List<Proof.CredentialType>
) {
    constructor(
        appID: AppID,
        action: String,
        signal: String,
        actionDescription: String?,
        verificationLevel: VerificationLevel
    ) : this(
        appId = appID.rawId,
        action = action,
        signal = signal,
        actionDescription = actionDescription,
        verificationLevel = verificationLevel,
        credentialTypes = if (verificationLevel == VerificationLevel.ORB)
            listOf(Proof.CredentialType.ORB)
        else
            listOf(Proof.CredentialType.ORB, Proof.CredentialType.DEVICE)
    )

    @Throws(Exception::class)
    fun encrypt(key: SecretKey, nonce: ByteArray): Payload {
        val jsonString = Json.encodeToString(this)
        val data = jsonString.toByteArray()

        // Initialize the cipher with AES/GCM/NoPadding
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, nonce)  // 128-bit authentication tag
        cipher.init(Cipher.ENCRYPT_MODE, key, spec)

        // Encrypt the data
        val sealedBox = cipher.doFinal(data)

        // Extract the ciphertext and the tag
        val ciphertext = sealedBox.copyOfRange(0, sealedBox.size - 16)
        val tag = sealedBox.copyOfRange(sealedBox.size - 16, sealedBox.size)

        // Combine ciphertext and tag
        val payload = ciphertext + tag

        // Return the Payload object containing the IV and the encrypted data
        return Payload(
            iv = Base64.getEncoder().encodeToString(nonce),
            payload = Base64.getEncoder().encodeToString(payload)
        )
    }
}

@Serializable
data class Payload(
    val iv: String,
    val payload: String
) {
    @Throws(Exception::class)
    fun decrypt(key: SecretKey): BridgeResponse {
        // Decode the Base64 encoded payload and IV (nonce)
        val decodedPayload = Base64.getDecoder().decode(payload)
        val decodedIV = Base64.getDecoder().decode(iv)

        // Extract ciphertext and authentication tag from the payload
        val ciphertext = decodedPayload.copyOfRange(0, decodedPayload.size - 16)
        val authTag = decodedPayload.copyOfRange(decodedPayload.size - 16, decodedPayload.size)

        // Initialize the cipher for decryption
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, decodedIV)  // 128-bit authentication tag
        cipher.init(Cipher.DECRYPT_MODE, key, spec)

        // Combine ciphertext and authentication tag for decryption
        val combined = ciphertext + authTag
        val decryptedData = cipher.doFinal(combined)

        // Decode the decrypted data back to an object
        return Json.decodeFromString<BridgeResponse>(String(decryptedData))
    }
}

class AppID(val rawId: String) {
    init {
        require(rawId.startsWith("app_")) { "Invalid App ID" }
    }

    val isStaging: Boolean
        get() = rawId.startsWith("app_staging_")
}

@Serializable
data class BridgeURL(val rawURL: String) {
    companion object {
        val default = BridgeURL("https://bridge.worldcoin.org")
    }

    init {
        val url = URL(rawURL)
        when {
            url.host == "localhost" || url.host == "127.0.0.1" -> {}
            url.protocol != "https" -> throw IllegalArgumentException("Bridge URL must use HTTPS.")
            url.port != -1 -> throw IllegalArgumentException("Bridge URL must use the default port.")
            url.path != "" && url.path != "/" -> throw IllegalArgumentException("Bridge URL must not contain a path.")
            url.query != null -> throw IllegalArgumentException("Bridge URL must not contain a query.")
            url.ref != null -> throw IllegalArgumentException("Bridge URL must not contain a fragment.")
        }
    }
}

