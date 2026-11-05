package pt.studiomesa.app

import android.content.Context
import android.os.StatFs
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.tool
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking

/**
 * Runs the StudioMesa language model locally on the Android device.
 *
 * The model is downloaded once, stored in app-private files and verified before inference.
 * No user prompt, history, restaurant data or memory is sent to a server by this class.
 */
internal object AssistantModelConversationPolicy {
    fun requiresNewSession(
        activeConversationId: String?,
        requestedConversationId: String,
        activeSystemInstruction: String?,
        requestedSystemInstruction: String,
        activeUserTurns: Int,
        maxUserTurnsPerSession: Int,
        sessionAvailable: Boolean
    ): Boolean =
        requestedConversationId.isBlank() ||
            !sessionAvailable ||
            activeConversationId != requestedConversationId ||
            activeSystemInstruction != requestedSystemInstruction ||
            activeUserTurns >= maxUserTurnsPerSession
}

internal class LocalAssistantModelManager(context: Context) : AutoCloseable {
    data class Turn(val role: String, val content: String)

    private val appContext = context.applicationContext
    private val worker = Executors.newSingleThreadExecutor { task ->
        Thread(task, "studiomesa-local-ai").apply {
            priority = Thread.NORM_PRIORITY
        }
    }
    private val modelFile = File(
        File(appContext.filesDir, MODEL_DIRECTORY),
        MODEL_FILENAME
    )
    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var activeConversationId: String? = null
    private var activeSystemInstruction: String? = null
    private var activeUserTurns = 0
    private var restaurantDataTool: StudioMesaRestaurantDataTool? = null
    private var priceReferenceTool: StudioMesaPriceReferenceTool? = null
    @Volatile private var downloading = false
    @Volatile private var closed = false
    private var verifiedThisProcess = false

    fun isDownloading(): Boolean = downloading

    fun isModelInstalled(): Boolean =
        modelFile.isFile && modelFile.length() == MODEL_SIZE_BYTES

    fun modelSizeBytes(): Long = MODEL_SIZE_BYTES

    fun downloadModel(
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
        onComplete: (Result<File>) -> Unit
    ) {
        if (closed || downloading) {
            onComplete(Result.failure(IllegalStateException("A transferência já está em curso ou o motor foi fechado.")))
            return
        }
        downloading = true
        worker.execute {
            val result = runCatching {
                modelFile.parentFile?.let { directory ->
                    if (!directory.exists() && !directory.mkdirs()) {
                        throw IllegalStateException("Não foi possível criar a pasta privada do modelo.")
                    }
                } ?: throw IllegalStateException("A pasta privada do modelo não está disponível.")

                if (modelFile.isFile && modelFile.length() == MODEL_SIZE_BYTES && verifySha256(modelFile)) {
                    verifiedThisProcess = true
                    return@runCatching modelFile
                }
                modelFile.delete()

                val availableBytes = StatFs(appContext.filesDir.absolutePath).availableBytes
                val requiredBytes = MODEL_SIZE_BYTES + MINIMUM_FREE_SPACE_BYTES
                if (availableBytes < requiredBytes) {
                    throw IllegalStateException(
                        "Espaço insuficiente. São necessários cerca de 850 MB livres para transferir e instalar o modelo."
                    )
                }

                val temporaryFile = File(modelFile.parentFile, "$MODEL_FILENAME.part")
                temporaryFile.delete()
                val connection = (URL(MODEL_DOWNLOAD_URL).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 25_000
                    readTimeout = 120_000
                    instanceFollowRedirects = true
                    useCaches = false
                    setRequestProperty("Accept", "application/octet-stream")
                    setRequestProperty("User-Agent", "StudioMesa-Offline-AI/1.0")
                }

                try {
                    val status = connection.responseCode
                    if (status !in 200..299) {
                        throw IllegalStateException("O servidor do modelo respondeu com HTTP $status.")
                    }
                    val declaredLength = connection.contentLengthLong
                    if (declaredLength > 0 && declaredLength != MODEL_SIZE_BYTES) {
                        throw IllegalStateException("O tamanho anunciado pelo servidor não corresponde ao modelo validado.")
                    }

                    val digest = MessageDigest.getInstance("SHA-256")
                    var downloaded = 0L
                    BufferedInputStream(connection.inputStream, BUFFER_SIZE).use { input ->
                        BufferedOutputStream(FileOutputStream(temporaryFile), BUFFER_SIZE).use { output ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            while (true) {
                                if (closed) throw CancellationException("Transferência cancelada ao fechar a aplicação.")
                                val count = input.read(buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                                digest.update(buffer, 0, count)
                                downloaded += count
                                onProgress(downloaded, MODEL_SIZE_BYTES)
                            }
                            output.flush()
                        }
                    }

                    if (downloaded != MODEL_SIZE_BYTES) {
                        throw IllegalStateException(
                            "A transferência ficou incompleta ($downloaded de $MODEL_SIZE_BYTES bytes)."
                        )
                    }
                    val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
                    if (!actualHash.equals(MODEL_SHA256, ignoreCase = true)) {
                        throw IllegalStateException("A verificação SHA-256 falhou. O ficheiro foi rejeitado.")
                    }
                    if (modelFile.exists() && !modelFile.delete()) {
                        throw IllegalStateException("Não foi possível substituir um ficheiro de modelo anterior.")
                    }
                    if (!temporaryFile.renameTo(modelFile)) {
                        throw IllegalStateException("Não foi possível instalar o modelo transferido.")
                    }
                    verifiedThisProcess = true
                    modelFile
                } finally {
                    connection.disconnect()
                    if (temporaryFile.exists()) temporaryFile.delete()
                }
            }
            downloading = false
            onComplete(result)
        }
    }

    fun generate(
        conversationId: String,
        systemInstruction: String,
        snapshot: StudioMesaAssistantSnapshot,
        history: List<Turn>,
        userMessage: String,
        deepThinking: Boolean = false,
        onToken: (String) -> Unit,
        onComplete: (Result<String>) -> Unit
    ) {
        if (closed) {
            onComplete(Result.failure(IllegalStateException("O motor local foi fechado.")))
            return
        }
        worker.execute {
            val result = runCatching {
                require(conversationId.isNotBlank()) { "A conversa não está identificada." }
                require(userMessage.isNotBlank()) { "A mensagem está vazia." }
                require(isModelInstalled()) { "Transfere primeiro o modelo de IA local." }

                if (!verifiedThisProcess) {
                    if (!verifySha256(modelFile)) {
                        throw IllegalStateException("O modelo guardado não passou a verificação de integridade. Transfere-o novamente.")
                    }
                    verifiedThisProcess = true
                }

                val effectiveSystemInstruction = buildString {
                    append(systemInstruction.take(MAX_SYSTEM_INSTRUCTION_CHARS))
                    if (deepThinking) {
                        appendLine()
                        appendLine("MODO PENSAR MAIS: revê a pergunta com especial cuidado; identifica os dados confirmados, as hipóteses e o que falta; verifica novamente cálculos e unidades; compara alternativas e riscos; procura contradições antes de responder. Apresenta apenas conclusões, fórmulas e justificações verificáveis, sem revelar raciocínio interno privado.")
                    }
                }.take(MAX_SYSTEM_INSTRUCTION_CHARS)

                val sessionAvailable = engine != null && conversation != null
                if (AssistantModelConversationPolicy.requiresNewSession(
                        activeConversationId = activeConversationId,
                        requestedConversationId = conversationId,
                        activeSystemInstruction = activeSystemInstruction,
                        requestedSystemInstruction = effectiveSystemInstruction,
                        activeUserTurns = activeUserTurns,
                        maxUserTurnsPerSession = MAX_SESSION_USER_TURNS,
                        sessionAvailable = sessionAvailable
                    )) {
                    closeActiveSession()

                    val createdEngine = Engine(
                        EngineConfig(
                            modelPath = modelFile.absolutePath,
                            backend = Backend.CPU(),
                            maxNumTokens = MAX_CONTEXT_TOKENS,
                            cacheDir = File(appContext.cacheDir, "studiomesa-litert-cache").absolutePath
                        )
                    )
                    // An Engine and its Conversation belong to one user conversation.
                    // LiteRT-LM 0.18/LFM2 can retain short-convolution state when a second
                    // Conversation is created on an already-used Engine.
                    createdEngine.initialize()

                    val dataTool = StudioMesaRestaurantDataTool(snapshot)
                    val priceTool = StudioMesaPriceReferenceTool(snapshot)
                    val initialMessages = history
                        .takeLast(MAX_HISTORY_MESSAGES)
                        .mapNotNull { turn ->
                            val clean = turn.content.trim().take(MAX_HISTORY_MESSAGE_CHARS)
                            when {
                                clean.isBlank() -> null
                                turn.role == "user" -> Message.user(clean)
                                turn.role == "assistant" -> Message.model(clean)
                                else -> null
                            }
                        }

                    val createdConversation = try {
                        createdEngine.createConversation(
                            ConversationConfig(
                                systemInstruction = Contents.of(effectiveSystemInstruction),
                                initialMessages = initialMessages,
                                tools = listOf(tool(dataTool), tool(priceTool)),
                                automaticToolCalling = true,
                                samplerConfig = SamplerConfig(
                                    topK = 40,
                                    topP = 0.9,
                                    temperature = if (deepThinking) 0.2 else 0.3
                                ),
                                maxOutputToken = if (deepThinking) DEEP_MAX_OUTPUT_TOKENS else MAX_OUTPUT_TOKENS,
                                prefillPrefaceOnInit = false
                            )
                        )
                    } catch (error: Exception) {
                        // Only successfully initialized engines may be closed.
                        runCatching { createdEngine.close() }
                        throw error
                    }

                    engine = createdEngine
                    conversation = createdConversation
                    activeConversationId = conversationId
                    activeSystemInstruction = systemInstruction.take(MAX_SYSTEM_INSTRUCTION_CHARS)
                    activeUserTurns = 0
                    restaurantDataTool = dataTool
                    priceReferenceTool = priceTool
                }

                // Keep the conversation's native history, but refresh the read-only tool
                // snapshots before each user turn so the assistant cannot rely on stale
                // menu, order, stock or reservation values.
                restaurantDataTool?.updateSnapshot(snapshot)
                    ?: throw IllegalStateException("As ferramentas de consulta não estão disponíveis.")
                priceReferenceTool?.updateSnapshot(snapshot)
                    ?: throw IllegalStateException("A ferramenta de preços não está disponível.")

                val activeConversation = conversation
                    ?: throw IllegalStateException("Não foi possível iniciar a conversa local.")

                val answer = StringBuilder()
                runBlocking {
                    activeConversation.sendMessageAsync(
                        Message.user(userMessage.take(MAX_USER_MESSAGE_CHARS))
                    ).collect { chunk ->
                        val text = chunk.toString()
                        if (text.isNotEmpty()) {
                            answer.append(text)
                            onToken(text)
                        }
                    }
                }
                val reply = answer.toString().trim().ifBlank {
                    throw IllegalStateException("O modelo local não devolveu uma resposta.")
                }
                activeUserTurns += 1
                reply
            }
            if (result.isFailure) {
                // A persisted conversation can be reconstructed from encrypted history
                // on the next turn; discard possibly incomplete native state now.
                runCatching { closeActiveSession() }
            }
            onComplete(result)
        }
    }

    private fun closeActiveSession() {
        val currentConversation = conversation
        val currentEngine = engine
        conversation = null
        engine = null
        activeConversationId = null
        activeSystemInstruction = null
        activeUserTurns = 0
        restaurantDataTool = null
        priceReferenceTool = null

        try {
            currentConversation?.close()
        } finally {
            currentEngine?.close()
        }
    }

    /** Releases RAM when the app is put in the background; the downloaded model remains installed. */
    fun releaseEngine() {
        if (closed) return
        worker.execute {
            closeActiveSession()
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        worker.execute {
            try {
                closeActiveSession()
            } finally {
                worker.shutdown()
            }
        }
    }

    private fun verifySha256(file: File): Boolean = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        BufferedInputStream(FileInputStream(file), BUFFER_SIZE).use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        actual.equals(MODEL_SHA256, ignoreCase = true)
    }.getOrDefault(false)

    internal companion object {
        const val MODEL_FILENAME = "LFM2.5-1.2B-Instruct_int4.litertlm"
        const val MODEL_SIZE_BYTES = 736_015_744L
        const val MODEL_SHA256 = "a28b5c59ac204e2e51c1f98d2d6db6982f0e12da59a268fe498edcb33237e906"
        const val MODEL_DOWNLOAD_URL =
            "https://huggingface.co/litert-community/LFM2.5-1.2B-Instruct/resolve/main/LFM2.5-1.2B-Instruct_int4.litertlm"
        const val MODEL_DIRECTORY = "studiomesa_ai_models"
        const val MAX_CONTEXT_TOKENS = 4096
        const val MAX_OUTPUT_TOKENS = 1024
        const val DEEP_MAX_OUTPUT_TOKENS = 1400
        const val MAX_HISTORY_MESSAGES = 4
        const val MAX_SESSION_USER_TURNS = 2
        const val MAX_HISTORY_MESSAGE_CHARS = 300
        const val MAX_SYSTEM_INSTRUCTION_CHARS = 3200
        const val MAX_USER_MESSAGE_CHARS = 1600
        const val MINIMUM_FREE_SPACE_BYTES = 128L * 1024L * 1024L
        const val BUFFER_SIZE = 1024 * 1024
    }
}
