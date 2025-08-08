package com.worldcoin.idkit_kotlin

import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import java.net.URL
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@Serializable
sealed interface Proof {

    @Serializable
    enum class CredentialType {
        @SerialName("orb")
        ORB,
        @SerialName("secure_document")
        SECURE_DOCUMENT,
        @SerialName("document")
        DOCUMENT,
        @SerialName("device")
        DEVICE
    }

    @Serializable
    data class Default(
        val proof: String,
        @SerialName("merkle_root") val merkleRoot: String,
        @SerialName("nullifier_hash") val nullifierHash: String,
        @SerialName("credential_type") val credentialType: CredentialType,
        @SerialName("verification_level") val verificationLevel: CredentialType,
    ) : Proof

    @Serializable
    data class CredentialCategory(
        @SerialName("proof") val proof: String,
        @SerialName("merkle_root") val merkleRoot: String,
        @SerialName("nullifier_hash") val nullifierHash: String,
        @SerialName("verification_credential_result")
        val verificationCredentialResult:  Map<String, String>,
    ) : Proof
}

enum class VerificationLevel {
    @SerialName("orb")
    ORB,
    @SerialName("secure_document")
    SECURE_DOCUMENT,
    @SerialName("document")
    DOCUMENT,
    @SerialName("device")
    DEVICE
}

@Serializable(with = AppErrorSerializer::class)
sealed interface AppError {
    val message: String

    @Serializable
    @SerialName("connection_failed")
    object ConnectionFailed : AppError {
        override val message =
            "Failed to connect to the World App. Please create a new session and try again."
    }

    @Serializable
    @SerialName("verification_rejected")
    object VerificationRejected : AppError {
        override val message =
            "The user rejected the verification request in the World App."
    }

    @Serializable
    @SerialName("max_verifications_reached")
    object MaxVerificationsReached : AppError {
        override val message =
            "The user already verified the maximum number of times for this action."
    }

    @Serializable
    @SerialName("credential_unavailable")
    object CredentialUnavailable : AppError {
        override val message =
            "The user does not have the verification level required by this app."
    }

    @Serializable
    @SerialName("malformed_request")
    object MalformedRequest : AppError {
        override val message =
            "There was a problem with this request. Please try again or contact the app owner."
    }

    @Serializable
    @SerialName("invalid_network")
    object InvalidNetwork : AppError {
        override val message =
            "Invalid network. If you are the app owner, visit docs.worldcoin.org/test for details."
    }

    @Serializable
    @SerialName("inclusion_proof_failed")
    object InclusionProofFailed : AppError {
        override val message =
            "There was an issue fetching the user's credential. Please try again."
    }

    @Serializable
    @SerialName("inclusion_proof_pending")
    object InclusionProofPending : AppError {
        override val message =
            "The user's identity is still being registered. Please wait a few minutes and try again."
    }

    @Serializable
    @SerialName("unexpected_response")
    object UnexpectedResponse : AppError {
        override val message =
            "Unexpected response from the user's World App. Please try again."
    }

    @Serializable
    @SerialName("failed_by_host_app")
    object FailedByHostApp : AppError {
        override val message =
            "Verification failed by the app. Please contact the app owner for details."
    }

    @Serializable
    @SerialName("generic_error")
    object GenericError : AppError {
        override val message = "Something unexpected went wrong. Please try again."
    }
}

internal class AppErrorThrowable(appError: AppError) : Throwable(appError.message)

@Serializable
sealed interface EncryptablePayload

@Serializable
data class CreateRequestPayload(
    @SerialName("app_id") val appId: String,
    @SerialName("action") val action: String,
    @SerialName("signal") val signal: String,
    @SerialName("action_description") val actionDescription: String?,
    @SerialName("verification_level") val verificationLevel: VerificationLevel,
    @SerialName("credential_types") val credentialTypes: List<Proof.CredentialType>
) : EncryptablePayload {
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
        credentialTypes = when (verificationLevel) {
            VerificationLevel.ORB -> listOf(Proof.CredentialType.ORB)
            VerificationLevel.SECURE_DOCUMENT -> listOf(
                Proof.CredentialType.ORB,
                Proof.CredentialType.SECURE_DOCUMENT
            )

            VerificationLevel.DOCUMENT -> listOf(
                Proof.CredentialType.ORB,
                Proof.CredentialType.SECURE_DOCUMENT,
                Proof.CredentialType.DOCUMENT
            )

            else -> listOf(Proof.CredentialType.ORB, Proof.CredentialType.DEVICE)
        }
    )
}

@Serializable
data class CreateCredentialCategoryRequestPayload(
    @SerialName("app_id") val appId: String,
    @SerialName("action") val action: String,
    @SerialName("signal") val signal: String,
    @SerialName("action_description") val actionDescription: String?,
    @SerialName("credential_category") val credentialCategory: Set<CredentialCategory>
) : EncryptablePayload {
    constructor(
        appID: AppID,
        action: String,
        signal: String,
        actionDescription: String?,
        credentialCategory: Set<CredentialCategory>
    ) : this(
        appId = appID.rawId,
        action = action,
        signal = signal,
        actionDescription = actionDescription,
        credentialCategory = credentialCategory
    )
}

@Serializable
enum class CredentialCategory {
    /**
     * The set of NFC credentials with no authentication.
     */
    DOCUMENT,

    /**
     * The set of NFC credentials with active or passive authentication.
     */
    SECURE_DOCUMENT
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
