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
            "a28b5c59ac204e2e51c1f98d2d6db6982f0e12da59a268fe498edcb33237e906",
            LocalAssistantModelManager.MODEL_SHA256
        )
        assertEquals(64, LocalAssistantModelManager.MODEL_SHA256.length)
        assertTrue(LocalAssistantModelManager.MODEL_DOWNLOAD_URL.startsWith("https://huggingface.co/"))
    }

    @Test
    fun localInferenceStaysInsideTheModelContextWindow() {
        assertEquals(4096, LocalAssistantModelManager.MAX_CONTEXT_TOKENS)
        assertTrue(LocalAssistantModelManager.MAX_OUTPUT_TOKENS in 1..4096)
        assertTrue(LocalAssistantModelManager.MAX_HISTORY_MESSAGES in 1..16)
        assertTrue(LocalAssistantModelManager.MAX_HISTORY_MESSAGE_CHARS <= 1000)
    }
    @Test
    fun runtimeSessionIsReusedOnlyWithinTheSameUserConversation() {
        assertEquals(
            false,
            AssistantModelConversationPolicy.requiresNewSession(
                activeConversationId = "thread-a",
                requestedConversationId = "thread-a",
                sessionAvailable = true
            )
        )
        assertEquals(
            true,
            AssistantModelConversationPolicy.requiresNewSession(
                activeConversationId = "thread-a",
                requestedConversationId = "thread-b",
                sessionAvailable = true
            )
        )
        assertEquals(
            true,
            AssistantModelConversationPolicy.requiresNewSession(
                activeConversationId = "thread-a",
                requestedConversationId = "thread-a",
                sessionAvailable = false
            )
        )
        assertEquals(
            true,
            AssistantModelConversationPolicy.requiresNewSession(
                activeConversationId = null,
                requestedConversationId = "thread-a",
                sessionAvailable = false
            )
        )
    }

}
