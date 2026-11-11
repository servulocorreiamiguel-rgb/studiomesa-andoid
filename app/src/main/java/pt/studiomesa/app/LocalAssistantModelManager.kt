package pt.studiomesa.app

import android.app.ActivityManager
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

internal object LocalAssistantModelPolicy {
    fun needsIntegrityVerification(activeModelPath: String, verifiedModelPath: String?): Boolean =
        verifiedModelPath != activeModelPath

    fun shouldFallbackToLfmAfterGemmaLoadFailure(
        activeModelFilename: String,
        lfmFileExists: Boolean,
        lfmSizeValid: Boolean,
        lfmHashValid: Boolean
    ): Boolean =
        activeModelFilename == LocalAssistantModelManager.GEMMA_MODEL_FILENAME &&
            lfmFileExists && lfmSizeValid && lfmHashValid
}

internal class LocalAssistantModelManager(context: Context) : AutoCloseable {
    data class Turn(val role: String, val content: String)

    private val appContext = context.applicationContext
    private val worker = Executors.newSingleThreadExecutor { task ->
        Thread(task, "studiomesa-local-ai").apply {
            priority = Thread.NORM_PRIORITY
        }
    }
    private val modelDirectory = File(appContext.filesDir, MODEL_DIRECTORY)
    private val lfmModelFile = File(modelDirectory, LFM_MODEL_FILENAME)
    private val gemmaModelFile = File(modelDirectory, GEMMA_MODEL_FILENAME)
    @Volatile private var forceLfmForProcess = false

    private fun canUseGemmaOnThisDevice(): Boolean = runCatching {
        val manager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memory = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(memory)
        val availableBytes = StatFs(appContext.filesDir.absolutePath).availableBytes
        memory.totalMem >= MIN_GEMMA_TOTAL_MEMORY_BYTES &&
            availableBytes >= GEMMA_MODEL_SIZE_BYTES + MINIMUM_FREE_SPACE_BYTES
    }.getOrDefault(false)

    private fun artifactFor(file: File): ModelArtifact =
        if (file.name == GEMMA_MODEL_FILENAME) {
            ModelArtifact(
                file = file,
                displayName = "Gemma 4 E2B",
                sizeBytes = GEMMA_MODEL_SIZE_BYTES,
                sha256 = GEMMA_MODEL_SHA256,
                downloadUrl = GEMMA_MODEL_DOWNLOAD_URL,
                cacheName = "gemma-4-e2b"
            )
        } else {
            ModelArtifact(
                file = file,
                displayName = "LFM2.5 1.2B",
                sizeBytes = LFM_MODEL_SIZE_BYTES,
                sha256 = LFM_MODEL_SHA256,
                downloadUrl = LFM_MODEL_DOWNLOAD_URL,
                cacheName = "lfm2.5-1.2b"
            )
        }

    private fun installedModelFile(): File? = when {
        !forceLfmForProcess && gemmaModelFile.isFile &&
            gemmaModelFile.length() == GEMMA_MODEL_SIZE_BYTES -> gemmaModelFile
        lfmModelFile.isFile && lfmModelFile.length() == LFM_MODEL_SIZE_BYTES -> lfmModelFile
        else -> null
    }

    private fun preferredModelFile(): File =
        if (canUseGemmaOnThisDevice()) gemmaModelFile else lfmModelFile

    fun activeModelDisplayName(): String =
        artifactFor(installedModelFile() ?: preferredModelFile()).displayName

    fun shouldOfferGemmaUpgrade(): Boolean =
        canUseGemmaOnThisDevice() &&
            installedModelFile() == lfmModelFile &&
            (!gemmaModelFile.isFile || gemmaModelFile.length() != GEMMA_MODEL_SIZE_BYTES)
    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var activeConversationId: String? = null
    private var activeSystemInstruction: String? = null
    private var activeUserTurns = 0
    private var restaurantDataTool: StudioMesaRestaurantDataTool? = null
    private var priceReferenceTool: StudioMesaPriceReferenceTool? = null
    private var supplierInvoiceTool: StudioMesaSupplierInvoiceTool? = null
    private var economicAssistantBridgeTool: StudioMesaEconomicAssistantBridgeTool? = null
    private var economicCalculationsTool: StudioMesaEconomicCalculationsTool? = null
    private var salesForecastTool: StudioMesaSalesForecastTool? = null
    @Volatile private var downloading = false
    @Volatile private var closed = false
    private var verifiedModelPath: String? = null

    fun isDownloading(): Boolean = downloading

    fun isModelInstalled(): Boolean = installedModelFile() != null

    fun modelSizeBytes(preferGemma: Boolean = false): Long {
        val selected = when {
            preferGemma && canUseGemmaOnThisDevice() -> gemmaModelFile
            installedModelFile() != null -> installedModelFile()!!
            else -> preferredModelFile()
        }
        return artifactFor(selected).sizeBytes
    }

    fun downloadModel(
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
        onComplete: (Result<File>) -> Unit,
        preferGemma: Boolean = false
    ) {
        if (closed || downloading) {
            onComplete(Result.failure(IllegalStateException("A transferência já está em curso ou o motor foi fechado.")))
            return
        }
        downloading = true
        worker.execute {
            val result = runCatching {
                val targetFile = when {
                    preferGemma && canUseGemmaOnThisDevice() -> gemmaModelFile
                    installedModelFile() != null -> installedModelFile()!!
                    else -> preferredModelFile()
                }
                val artifact = artifactFor(targetFile)
                targetFile.parentFile?.let { directory ->
                    if (!directory.exists() && !directory.mkdirs()) {
                        throw IllegalStateException("Não foi possível criar a pasta privada do modelo.")
                    }
                } ?: throw IllegalStateException("A pasta privada do modelo não está disponível.")

                if (targetFile.isFile &&
                    targetFile.length() == artifact.sizeBytes &&
                    verifySha256(targetFile, artifact.sha256)
                ) {
                    verifiedModelPath = targetFile.absolutePath
                    return@runCatching targetFile
                }
                targetFile.delete()

                val availableBytes = StatFs(appContext.filesDir.absolutePath).availableBytes
                val requiredBytes = artifact.sizeBytes + MINIMUM_FREE_SPACE_BYTES
                if (availableBytes < requiredBytes) {
                    throw IllegalStateException(
                        "Espaço insuficiente. São necessários cerca de " +
                            formatModelSize(artifact.sizeBytes) + " livres para transferir " +
                            artifact.displayName + " e manter espaço de trabalho."
                    )
                }

                val temporaryFile = File(targetFile.parentFile, "${artifact.file.name}.part")
                temporaryFile.delete()
                val connection = (URL(artifact.downloadUrl).openConnection() as HttpURLConnection).apply {
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
                    if (declaredLength > 0 && declaredLength != artifact.sizeBytes) {
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
                                onProgress(downloaded, artifact.sizeBytes)
                            }
                            output.flush()
                        }
                    }

                    if (downloaded != artifact.sizeBytes) {
                        throw IllegalStateException(
                            "A transferência ficou incompleta ($downloaded de ${artifact.sizeBytes} bytes)."
                        )
                    }
                    val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
                    if (!actualHash.equals(artifact.sha256, ignoreCase = true)) {
                        throw IllegalStateException("A verificação SHA-256 falhou. O ficheiro foi rejeitado.")
                    }
                    if (targetFile.exists() && !targetFile.delete()) {
                        throw IllegalStateException("Não foi possível substituir um ficheiro de modelo anterior.")
                    }
                    if (!temporaryFile.renameTo(targetFile)) {
                        throw IllegalStateException("Não foi possível instalar o modelo transferido.")
                    }
                    verifiedModelPath = targetFile.absolutePath
                    targetFile
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
        economicMode: Boolean = false,
        economicBridgeEnabled: Boolean = false,
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
                require(userMessage.length <= MAX_USER_MESSAGE_CHARS) {
                    "A pergunta é demasiado longa para o modelo local (${MAX_USER_MESSAGE_CHARS} caracteres). Divide-a em duas mensagens para evitar perder contexto."
                }
                require(isModelInstalled()) { "Transfere primeiro o modelo de IA local." }

                var activeModelFile = installedModelFile()
                    ?: throw IllegalStateException("O modelo local deixou de estar disponível.")
                var activeArtifact = artifactFor(activeModelFile)
                if (LocalAssistantModelPolicy.needsIntegrityVerification(
                        activeModelFile.absolutePath,
                        verifiedModelPath
                    )) {
                    if (!verifySha256(activeModelFile, activeArtifact.sha256)) {
                        val lfmExists = lfmModelFile.isFile
                        val lfmSizeValid = lfmModelFile.length() == LFM_MODEL_SIZE_BYTES
                        val lfmHashValid = lfmExists && lfmSizeValid &&
                            verifySha256(lfmModelFile, LFM_MODEL_SHA256)
                        if (!LocalAssistantModelPolicy.shouldFallbackToLfmAfterGemmaLoadFailure(
                                activeModelFile.name,
                                lfmExists,
                                lfmSizeValid,
                                lfmHashValid
                            )
                        ) {
                            throw IllegalStateException(
                                "O modelo guardado não passou a verificação de integridade. Transfere-o novamente."
                            )
                        }
                        forceLfmForProcess = true
                        activeModelFile = lfmModelFile
                        activeArtifact = artifactFor(lfmModelFile)
                    }
                    verifiedModelPath = activeModelFile.absolutePath
                }

                val effectiveSystemInstruction = AssistantThinkingModePolicy.buildSystemInstruction(
                    systemInstruction,
                    deepThinking,
                    MAX_SYSTEM_INSTRUCTION_CHARS
                )

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

                    fun createInitializedEngine(modelFile: File, artifact: ModelArtifact): Engine {
                        val candidate = Engine(
                            EngineConfig(
                                modelPath = modelFile.absolutePath,
                                backend = Backend.CPU(),
                                maxNumTokens = MAX_CONTEXT_TOKENS,
                                cacheDir = File(
                                    appContext.cacheDir,
                                    "studiomesa-litert-cache-${artifact.cacheName}"
                                ).absolutePath
                            )
                        )
                        try {
                            candidate.initialize()
                            return candidate
                        } catch (error: Exception) {
                            runCatching { candidate.close() }
                            throw error
                        }
                    }

                    // Gemma can exceed the real memory/runtime capability of a device even
                    // when its advertised RAM and free-space checks pass. Fall back to LFM.
                    val createdEngine = try {
                        createInitializedEngine(activeModelFile, activeArtifact)
                    } catch (primaryError: Exception) {
                        val lfmExists = lfmModelFile.isFile
                        val lfmSizeValid = lfmModelFile.length() == LFM_MODEL_SIZE_BYTES
                        val lfmHashValid = lfmExists && lfmSizeValid &&
                            verifySha256(lfmModelFile, LFM_MODEL_SHA256)
                        if (!LocalAssistantModelPolicy.shouldFallbackToLfmAfterGemmaLoadFailure(
                                activeModelFile.name,
                                lfmExists,
                                lfmSizeValid,
                                lfmHashValid
                            )
                        ) {
                            throw primaryError
                        }
                        forceLfmForProcess = true
                        activeModelFile = lfmModelFile
                        activeArtifact = artifactFor(lfmModelFile)
                        verifiedModelPath = activeModelFile.absolutePath
                        createInitializedEngine(activeModelFile, activeArtifact)
                    }

                    val dataTool = StudioMesaRestaurantDataTool(snapshot)
                    val priceTool = StudioMesaPriceReferenceTool(snapshot)
                    val invoiceTool = if (economicMode || economicBridgeEnabled) {
                        StudioMesaSupplierInvoiceTool(snapshot)
                    } else null
                    val bridgeTool = if (!economicMode && economicBridgeEnabled) {
                        invoiceTool?.let(::StudioMesaEconomicAssistantBridgeTool)
                    } else null
                    val calculationsTool = if (economicMode) StudioMesaEconomicCalculationsTool() else null
                    val salesForecast = if (economicMode) StudioMesaSalesForecastTool(snapshot) else null
                    val enabledTools = mutableListOf(tool(dataTool), tool(priceTool), tool(StudioMesaExactMathTool()))
                    if (economicMode && invoiceTool != null) {
                        enabledTools.add(tool(invoiceTool))
                    } else if (bridgeTool != null) {
                        enabledTools.add(tool(bridgeTool))
                    }
                    if (calculationsTool != null) enabledTools.add(tool(calculationsTool))
                    if (salesForecast != null) enabledTools.add(tool(salesForecast))
                    enabledTools.add(tool(StudioMesaWikipediaSearchTool()))
                    enabledTools.add(tool(StudioMesaWebSearchTool(appContext)))
                    val initialMessages = AssistantConversationContextPolicy.compactHistory(history)
                        .map { turn ->
                            if (turn.role == "user") Message.user(turn.content) else Message.model(turn.content)
                        }

                    val createdConversation = try {
                        createdEngine.createConversation(
                            ConversationConfig(
                                systemInstruction = Contents.of(effectiveSystemInstruction),
                                initialMessages = initialMessages,
                                tools = enabledTools,
                                automaticToolCalling = true,
                                samplerConfig = SamplerConfig(
                                    topK = 40,
                                    topP = 0.9,
                                    temperature = AssistantThinkingModePolicy.temperature(deepThinking)
                                ),
                                maxOutputToken = AssistantThinkingModePolicy.maxOutputTokens(deepThinking),
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
                    activeSystemInstruction = effectiveSystemInstruction
                    activeUserTurns = 0
                    restaurantDataTool = dataTool
                    priceReferenceTool = priceTool
                    supplierInvoiceTool = invoiceTool
                    economicAssistantBridgeTool = bridgeTool
                    economicCalculationsTool = calculationsTool
                    salesForecastTool = salesForecast
                }

                // Keep the conversation's native history, but refresh the read-only tool
                // snapshots before each user turn so the assistant cannot rely on stale
                // menu, order, stock or reservation values.
                restaurantDataTool?.updateSnapshot(snapshot)
                    ?: throw IllegalStateException("As ferramentas de consulta não estão disponíveis.")
                priceReferenceTool?.updateSnapshot(snapshot)
                    ?: throw IllegalStateException("A ferramenta de preços não está disponível.")
                if (economicMode) {
                    supplierInvoiceTool?.updateSnapshot(snapshot)
                        ?: throw IllegalStateException("A ferramenta de faturas não está disponível no modo económico.")
                    if (economicCalculationsTool == null) {
                        throw IllegalStateException("A calculadora económica não está disponível nesta sessão.")
                    }
                    salesForecastTool?.updateSnapshot(snapshot)
                        ?: throw IllegalStateException("A análise semanal e previsão de vendas não está disponível nesta sessão.")
                } else if (economicBridgeEnabled) {
                    economicAssistantBridgeTool?.updateSnapshot(snapshot)
                        ?: throw IllegalStateException("A ligação ao Assistente económico não está disponível.")
                }

                val activeConversation = conversation
                    ?: throw IllegalStateException("Não foi possível iniciar a conversa local.")

                val answer = StringBuilder()
                runBlocking {
                    activeConversation.sendMessageAsync(
                        Message.user(userMessage)
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
        supplierInvoiceTool = null
        economicAssistantBridgeTool = null
        economicCalculationsTool = null
        salesForecastTool = null

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

    private fun verifySha256(file: File, expectedSha256: String): Boolean = runCatching {
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
        actual.equals(expectedSha256, ignoreCase = true)
    }.getOrDefault(false)

    private fun formatModelSize(bytes: Long): String =
        String.format(java.util.Locale("pt", "PT"), "%.2f GB", bytes.toDouble() / 1_000_000_000.0)

    private data class ModelArtifact(
        val file: File,
        val displayName: String,
        val sizeBytes: Long,
        val sha256: String,
        val downloadUrl: String,
        val cacheName: String
    )

    internal companion object {
        // Compatibility aliases for existing tests and callers; the compact LFM remains the safe fallback.
        const val MODEL_FILENAME = "LFM2.5-1.2B-Instruct_int4.litertlm"
        const val MODEL_SIZE_BYTES = 736_015_744L
        const val MODEL_SHA256 = "e904729a8a8a0160658cb7cd22992e270a0547c05cc45b08c47ece5c0e29425b"
        const val MODEL_DOWNLOAD_URL =
            "https://huggingface.co/litert-community/LFM2.5-1.2B-Instruct/resolve/d23bed4d8871e970d9d4d7de087377a90ecb1b4e/LFM2.5-1.2B-Instruct_int4.litertlm"

        const val LFM_MODEL_FILENAME = MODEL_FILENAME
        const val LFM_MODEL_SIZE_BYTES = MODEL_SIZE_BYTES
        const val LFM_MODEL_SHA256 = MODEL_SHA256
        const val LFM_MODEL_DOWNLOAD_URL = MODEL_DOWNLOAD_URL

        const val GEMMA_MODEL_FILENAME = "gemma-4-E2B-it.litertlm"
        const val GEMMA_MODEL_SIZE_BYTES = 2_588_147_712L
        const val GEMMA_MODEL_SHA256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c"
        const val GEMMA_MODEL_DOWNLOAD_URL =
            "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/6e5c4f1e395deb959c494953478fa5cec4b8008f/gemma-4-E2B-it.litertlm"

        const val MODEL_DIRECTORY = "studiomesa_ai_models"
        const val MIN_GEMMA_TOTAL_MEMORY_BYTES = 6L * 1024L * 1024L * 1024L
        const val MAX_CONTEXT_TOKENS = 4096
        const val MAX_HISTORY_MESSAGES = AssistantGeneralConversationPolicy.MAX_HISTORY_MESSAGES
        const val MAX_SESSION_USER_TURNS = 2
        const val MAX_HISTORY_MESSAGE_CHARS = AssistantGeneralConversationPolicy.MAX_HISTORY_MESSAGE_CHARS
        const val MAX_SYSTEM_INSTRUCTION_CHARS = 3200
        const val MAX_USER_MESSAGE_CHARS = 1200
        const val MINIMUM_FREE_SPACE_BYTES = 512L * 1024L * 1024L
        const val BUFFER_SIZE = 1024 * 1024
    }
}
