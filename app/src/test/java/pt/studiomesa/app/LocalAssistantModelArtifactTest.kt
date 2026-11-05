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
