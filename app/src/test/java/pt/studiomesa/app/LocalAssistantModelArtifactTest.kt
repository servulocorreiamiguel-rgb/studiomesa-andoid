package pt.studiomesa.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAssistantModelArtifactTest {
    @Test
    fun modelArtifactIsPinnedAndIntegrityChecked() {
        assertEquals(
            "LFM2.5-1.2B-Instruct_int4.litertlm",
            LocalAssistantModelManager.MODEL_FILENAME
        )
        assertEquals(736_015_744L, LocalAssistantModelManager.MODEL_SIZE_BYTES)
        assertEquals(
            "e904729a8a8a0160658cb7cd22992e270a0547c05cc45b08c47ece5c0e29425b",
            LocalAssistantModelManager.MODEL_SHA256
        )
        assertEquals(64, LocalAssistantModelManager.MODEL_SHA256.length)
        assertTrue(LocalAssistantModelManager.MODEL_DOWNLOAD_URL.startsWith("https://huggingface.co/"))
        assertTrue(
            "Model URL must be pinned to an immutable Hugging Face revision.",
            LocalAssistantModelManager.MODEL_DOWNLOAD_URL.contains(
                "/resolve/d23bed4d8871e970d9d4d7de087377a90ecb1b4e/"
            )
        )
    }

    @Test
    fun gemmaArtifactIsPinnedAndIntegrityChecked() {
        assertEquals(
            "gemma-4-E2B-it.litertlm",
            LocalAssistantModelManager.GEMMA_MODEL_FILENAME
        )
        assertEquals(2_588_147_712L, LocalAssistantModelManager.GEMMA_MODEL_SIZE_BYTES)
        assertEquals(
            "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c",
            LocalAssistantModelManager.GEMMA_MODEL_SHA256
        )
        assertEquals(64, LocalAssistantModelManager.GEMMA_MODEL_SHA256.length)
        assertTrue(LocalAssistantModelManager.GEMMA_MODEL_DOWNLOAD_URL.startsWith("https://huggingface.co/"))
        assertTrue(
            "Gemma model URL must be pinned to an immutable Hugging Face revision.",
            LocalAssistantModelManager.GEMMA_MODEL_DOWNLOAD_URL.contains(
                "/resolve/6e5c4f1e395deb959c494953478fa5cec4b8008f/"
            )
        )
        assertTrue(LocalAssistantModelManager.MIN_GEMMA_TOTAL_MEMORY_BYTES >= 6L * 1024L * 1024L * 1024L)
    }

    @Test
    fun switchingLocalModelAlwaysRequiresFreshIntegrityVerification() {
        assertEquals(
            true,
            LocalAssistantModelPolicy.needsIntegrityVerification(
                activeModelPath = "/private/gemma-4-E2B-it.litertlm",
                verifiedModelPath = "/private/LFM2.5-1.2B-Instruct_int4.litertlm"
            )
        )
        assertEquals(
            false,
            LocalAssistantModelPolicy.needsIntegrityVerification(
                activeModelPath = "/private/LFM2.5-1.2B-Instruct_int4.litertlm",
                verifiedModelPath = "/private/LFM2.5-1.2B-Instruct_int4.litertlm"
            )
        )
    }

    @Test
    fun explicitLfmBackupDownloadOverridesAnInstalledGemmaModel() {
        assertEquals(
            LocalAssistantModelManager.LFM_MODEL_FILENAME,
            LocalAssistantModelPolicy.selectDownloadModelFilename(
                forceLfm = true,
                preferGemma = false,
                canUseGemma = true,
                installedModelFilename = LocalAssistantModelManager.GEMMA_MODEL_FILENAME,
                preferredModelFilename = LocalAssistantModelManager.GEMMA_MODEL_FILENAME
            )
        )
        assertEquals(
            LocalAssistantModelManager.GEMMA_MODEL_FILENAME,
            LocalAssistantModelPolicy.selectDownloadModelFilename(
                forceLfm = false,
                preferGemma = false,
                canUseGemma = true,
                installedModelFilename = LocalAssistantModelManager.GEMMA_MODEL_FILENAME,
                preferredModelFilename = LocalAssistantModelManager.GEMMA_MODEL_FILENAME
            )
        )
    }

    @Test
    fun lfmBackupOfferAppearsOnlyWhenGemmaIsActiveAndLfmIsMissingOrInvalid() {
        assertEquals(
            true,
            LocalAssistantModelPolicy.shouldOfferLfmBackup(
                LocalAssistantModelManager.GEMMA_MODEL_FILENAME,
                lfmFileExists = false,
                lfmSizeValid = false
            )
        )
        assertEquals(
            true,
            LocalAssistantModelPolicy.shouldOfferLfmBackup(
                LocalAssistantModelManager.GEMMA_MODEL_FILENAME,
                lfmFileExists = true,
                lfmSizeValid = false
            )
        )
        assertEquals(
            false,
            LocalAssistantModelPolicy.shouldOfferLfmBackup(
                LocalAssistantModelManager.GEMMA_MODEL_FILENAME,
                lfmFileExists = true,
                lfmSizeValid = true
            )
        )
        assertEquals(
            false,
            LocalAssistantModelPolicy.shouldOfferLfmBackup(
                LocalAssistantModelManager.LFM_MODEL_FILENAME,
                lfmFileExists = false,
                lfmSizeValid = false
            )
        )
    }

    @Test
    fun gemmaLoadFailureFallsBackOnlyToAnInstalledValidLfmArtifact() {
        assertEquals(
            true,
            LocalAssistantModelPolicy.shouldFallbackToLfm(
                LocalAssistantModelManager.GEMMA_MODEL_FILENAME,
                lfmFileExists = true,
                lfmSizeValid = true,
                lfmHashValid = true
            )
        )
        assertEquals(
            false,
            LocalAssistantModelPolicy.shouldFallbackToLfm(
                LocalAssistantModelManager.GEMMA_MODEL_FILENAME,
                lfmFileExists = true,
                lfmSizeValid = true,
                lfmHashValid = false
            )
        )
        assertEquals(
            false,
            LocalAssistantModelPolicy.shouldFallbackToLfm(
                LocalAssistantModelManager.MODEL_FILENAME,
                lfmFileExists = true,
                lfmSizeValid = true,
                lfmHashValid = true
            )
        )
    }

    @Test
    fun localInferenceStaysInsideTheModelContextWindow() {
        assertEquals(4096, LocalAssistantModelManager.MAX_CONTEXT_TOKENS)
        assertTrue(AssistantThinkingModePolicy.DEFAULT_MAX_OUTPUT_TOKENS in 1..4096)
        assertTrue(AssistantThinkingModePolicy.DEEP_MAX_OUTPUT_TOKENS in 1..4096)
        assertTrue(LocalAssistantModelManager.MAX_HISTORY_MESSAGES in 1..16)
        assertTrue(LocalAssistantModelManager.MAX_HISTORY_MESSAGE_CHARS <= 1000)
        assertEquals(1024, AssistantThinkingModePolicy.DEFAULT_MAX_OUTPUT_TOKENS)
        assertEquals(1400, AssistantThinkingModePolicy.DEEP_MAX_OUTPUT_TOKENS)
        assertEquals(2, LocalAssistantModelManager.MAX_SESSION_USER_TURNS)
    }
    @Test
    fun runtimeSessionIsReusedOnlyWithinTheSameUserConversation() {
        assertEquals(
            false,
            AssistantModelConversationPolicy.requiresNewSession(
                activeConversationId = "thread-a",
                requestedConversationId = "thread-a",
                activeSystemInstruction = "memory-a",
                requestedSystemInstruction = "memory-a",
                activeUserTurns = 1,
                maxUserTurnsPerSession = 2,
                sessionAvailable = true
            )
        )
        assertEquals(
            true,
            AssistantModelConversationPolicy.requiresNewSession(
                activeConversationId = "thread-a",
                requestedConversationId = "thread-b",
                activeSystemInstruction = "memory-a",
                requestedSystemInstruction = "memory-a",
                activeUserTurns = 1,
                maxUserTurnsPerSession = 2,
                sessionAvailable = true
            )
        )
        assertEquals(
            true,
            AssistantModelConversationPolicy.requiresNewSession(
                activeConversationId = "thread-a",
                requestedConversationId = "thread-a",
                activeSystemInstruction = "memory-a",
                requestedSystemInstruction = "memory-a",
                activeUserTurns = 0,
                maxUserTurnsPerSession = 2,
                sessionAvailable = false
            )
        )
        assertEquals(
            true,
            AssistantModelConversationPolicy.requiresNewSession(
                activeConversationId = null,
                requestedConversationId = "thread-a",
                activeSystemInstruction = "memory-a",
                requestedSystemInstruction = "memory-a",
                activeUserTurns = 0,
                maxUserTurnsPerSession = 2,
                sessionAvailable = false
            )
        )
        assertEquals(
            true,
            AssistantModelConversationPolicy.requiresNewSession(
                activeConversationId = "thread-a",
                requestedConversationId = "thread-a",
                activeSystemInstruction = "memory-a",
                requestedSystemInstruction = "memory-b",
                activeUserTurns = 0,
                maxUserTurnsPerSession = 2,
                sessionAvailable = true
            )
        )
        assertEquals(
            true,
            AssistantModelConversationPolicy.requiresNewSession(
                activeConversationId = "thread-a",
                requestedConversationId = "thread-a",
                activeSystemInstruction = "memory-a",
                requestedSystemInstruction = "memory-a",
                activeUserTurns = 2,
                maxUserTurnsPerSession = 2,
                sessionAvailable = true
            )
        )
    }

}
