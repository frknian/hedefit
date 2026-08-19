package com.hedefit.app.localai

import android.content.ComponentCallbacks2
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Hedefit'in cihaz üstü AI köprüsü.
 *
 * JavaScript'e AÇILAN YÜZEY BİLEREK DARDIR. Buradaki metotlar genel amaçlı
 * native yetenek sunmaz:
 *
 *   · dosya yolu kabul edilmez  → yalnız katalogdaki model kimliği
 *   · indirme URL'si kabul edilmez → adres native katalogdan gelir
 *   · keyfi kod/komut çalıştırılmaz
 *
 * Böylece WebView'a (uzak bir sunucudan yüklenen sayfaya) native yetki
 * devredilmiş olmaz.
 *
 * İŞ PARÇACIĞI: model yükleme ve üretim ASLA ana iş parçacığında çalışmaz;
 * hepsi Dispatchers.IO / Default üzerinde koşar.
 */
@CapacitorPlugin(name = "HedefitLocalAI")
class HedefitLocalAiPlugin : Plugin() {

    private val engine = LocalAiEngine()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val downloadCancelled = AtomicBoolean(false)
    @Volatile private var downloadJob: Job? = null
    @Volatile private var generationJob: Job? = null
    // Kullanıcı iptali ile gerçek arıza ayrımı: iptal, uzak sağlayıcıya
    // düşmeyi TETİKLEMEMELİDİR.
    @Volatile private var generationCancelledByUser = false

    // Bir seferde tek üretim. Aynı anda iki üretim başlatmak cihaz belleğini
    // ve ısısını gereksiz yere zorlar.
    private val generating = AtomicBoolean(false)

    override fun handleOnDestroy() {
        scope.cancel()
        engine.release()
        super.handleOnDestroy()
    }

    /**
     * Sistem bellek baskısı bildirdiğinde modeli bırakırız.
     *
     * Model belleği en büyük tek tüketicidir; onu tutmak uğruna sürecin
     * öldürülmesine izin vermek kullanıcının uygulamayı kaybetmesi demektir.
     * Bırakıldıktan sonra bir sonraki istek yeniden yükler veya uzağa düşer.
     */
    override fun handleOnPause() {
        super.handleOnPause()
    }

    fun onTrimMemory(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            engine.release()
        }
    }

    private fun resolveModel(call: PluginCall): LocalAiModelCatalog.Entry? {
        // JS yalnız KİMLİK verebilir; bilinmeyen kimlik reddedilir.
        val id = call.getString("modelId") ?: LocalAiModelCatalog.DEFAULT_MODEL_ID
        return LocalAiModelCatalog.byId(id)
    }

    @PluginMethod
    fun getCapabilities(call: PluginCall) {
        scope.launch {
            runCatching {
                val model = LocalAiModelCatalog.byId(call.getString("modelId") ?: LocalAiModelCatalog.DEFAULT_MODEL_ID)
                val report = LocalAiCapability.evaluate(context, model)
                JSObject().apply {
                    put("runtimeAvailable", true)
                    put("supported", report.supported)
                    put("state", report.state.name)
                    put("reason", report.reason)
                    put("abi", report.abi)
                    put("sdkInt", report.sdkInt)
                    put("totalRamMb", report.totalRamMb)
                    put("availableRamMb", report.availableRamMb)
                    put("freeStorageMb", report.freeStorageMb)
                    put("lowRamDevice", report.lowRamDevice)
                    put("engineLoaded", engine.isLoaded)
                    put("loadedModelId", engine.currentModelId)
                }
            }.onSuccess { call.resolve(it) }
                .onFailure { call.reject("capability_failed", it.javaClass.simpleName) }
        }
    }

    @PluginMethod
    fun listModels(call: PluginCall) {
        scope.launch {
            val models = com.getcapacitor.JSArray()
            for (entry in LocalAiModelCatalog.entries) {
                models.put(JSObject().apply {
                    put("id", entry.id)
                    put("displayName", entry.displayName)
                    put("sizeBytes", entry.sizeBytes)
                    put("installed", LocalAiModelStore.isInstalled(context, entry))
                    put("minTotalRamMb", entry.minTotalRamMb)
                })
            }
            call.resolve(JSObject().apply {
                put("models", models)
                put("defaultModelId", LocalAiModelCatalog.DEFAULT_MODEL_ID)
            })
        }
    }

    @PluginMethod
    fun getModelStatus(call: PluginCall) {
        val model = resolveModel(call) ?: return call.reject("unknown_model")
        scope.launch {
            call.resolve(JSObject().apply {
                put("modelId", model.id)
                put("installed", LocalAiModelStore.isInstalled(context, model))
                put("sizeBytes", model.sizeBytes)
                put("downloadedBytes", LocalAiModelStore.installedBytes(context, model))
                put("downloading", downloadJob?.isActive == true)
                put("loaded", engine.currentModelId == model.id && engine.isLoaded)
            })
        }
    }

    @PluginMethod
    fun downloadModel(call: PluginCall) {
        val model = resolveModel(call) ?: return call.reject("unknown_model")
        if (downloadJob?.isActive == true) return call.reject("download_in_progress")
        downloadCancelled.set(false)
        downloadJob = scope.launch {
            runCatching {
                LocalAiModelStore.download(context, model, downloadCancelled) { downloaded, total ->
                    notifyListeners("localAiDownloadProgress", JSObject().apply {
                        put("modelId", model.id)
                        put("downloadedBytes", downloaded)
                        put("totalBytes", total)
                    })
                }
            }.onSuccess {
                call.resolve(JSObject().apply { put("installed", true); put("modelId", model.id) })
            }.onFailure { error ->
                // Ham hata mesajı/stacktrace ARAYÜZE GİTMEZ; yalnız sınıflandırma.
                val kind = when (error) {
                    is LocalAiModelStore.DownloadCancelledException -> "cancelled"
                    is LocalAiModelStore.IntegrityException -> "integrity_failed"
                    else -> "download_failed"
                }
                call.reject(kind)
            }
        }
    }

    @PluginMethod
    fun cancelDownload(call: PluginCall) {
        downloadCancelled.set(true)
        call.resolve()
    }

    @PluginMethod
    fun deleteModel(call: PluginCall) {
        val model = resolveModel(call) ?: return call.reject("unknown_model")
        scope.launch {
            if (engine.currentModelId == model.id) engine.release()
            val deleted = LocalAiModelStore.delete(context, model)
            call.resolve(JSObject().apply { put("deleted", deleted) })
        }
    }

    @PluginMethod
    fun loadModel(call: PluginCall) {
        val model = resolveModel(call) ?: return call.reject("unknown_model")
        val timeoutMs = call.getInt("timeoutMs") ?: 120_000
        scope.launch {
            val result = runCatching {
                withTimeoutOrNull(timeoutMs.toLong()) { engine.load(context, model) }
            }
            result.onSuccess { loadMs ->
                if (loadMs == null) {
                    engine.release()
                    call.reject("load_timeout")
                } else {
                    call.resolve(JSObject().apply { put("loadMs", loadMs); put("modelId", model.id) })
                }
            }.onFailure { error ->
                engine.release()
                call.reject(if (error.message == "model_not_installed") "model_not_installed" else "load_failed")
            }
        }
    }

    @PluginMethod
    fun unloadModel(call: PluginCall) {
        scope.launch { engine.release(); call.resolve() }
    }

    /**
     * Akışlı üretim.
     *
     * Girdi, uygulamanın kendi boru hattının ürettiği NİHAİ istemdir; native
     * taraf ne Supabase'e ne başka bir servise erişir, kendi başına bağlam
     * üretmez.
     */
    @PluginMethod
    fun generate(call: PluginCall) {
        val model = resolveModel(call) ?: return call.reject("unknown_model")
        val systemPrompt = call.getString("systemPrompt").orEmpty()
        val userPrompt = call.getString("userPrompt").orEmpty()
        if (userPrompt.isBlank()) return call.reject("empty_prompt")
        val maxOutputTokens = call.getInt("maxOutputTokens") ?: 320
        val temperature = call.getDouble("temperature") ?: 0.3
        val timeoutMs = (call.getInt("timeoutMs") ?: 60_000).toLong()
        val stream = call.getBoolean("stream") ?: true
        val requestId = call.getString("requestId").orEmpty()

        if (!generating.compareAndSet(false, true)) return call.reject("generation_in_progress")
        generationCancelledByUser = false

        generationJob = scope.launch {
            val startedAt = System.currentTimeMillis()
            val builder = StringBuilder()
            val outcome = runCatching {
                if (!engine.isLoaded || engine.currentModelId != model.id) engine.load(context, model)
                withTimeoutOrNull(timeoutMs) {
                    engine.generateStream(model, systemPrompt, userPrompt, maxOutputTokens, temperature)
                        .collect { message ->
                            val chunk = message.contents.contents
                                .filterIsInstance<com.google.ai.edge.litertlm.Content.Text>()
                                .joinToString("") { it.text }
                            if (chunk.isNotEmpty()) {
                                builder.append(chunk)
                                if (stream && requestId.isNotEmpty()) {
                                    notifyListeners("localAiToken", JSObject().apply {
                                        put("requestId", requestId)
                                        put("chunk", chunk)
                                    })
                                }
                            }
                        }
                    true
                }
            }

            generating.set(false)
            outcome.onSuccess { completed ->
                if (generationCancelledByUser) {
                    // İPTAL ARIZA DEĞİLDİR: ayrı bir kodla döner ki JS tarafı
                    // uzak sağlayıcıya düşmesin.
                    call.reject("cancelled")
                    return@onSuccess
                }
                if (completed == null) {
                    engine.cancel()
                    call.reject("generation_timeout")
                    return@onSuccess
                }
                val info = engine.benchmarkInfo()
                call.resolve(JSObject().apply {
                    put("text", builder.toString())
                    put("modelId", model.id)
                    put("totalMs", System.currentTimeMillis() - startedAt)
                    put("loadMs", engine.loadDurationMs)
                    put("promptTokens", info?.lastPrefillTokenCount ?: 0)
                    put("outputTokens", info?.lastDecodeTokenCount ?: 0)
                    put("ttftMs", ((info?.timeToFirstTokenInSecond ?: 0.0) * 1000).toLong())
                    put("decodeTokensPerSecond", info?.lastDecodeTokensPerSecond ?: 0.0)
                    put("prefillTokensPerSecond", info?.lastPrefillTokensPerSecond ?: 0.0)
                })
            }.onFailure { error ->
                if (generationCancelledByUser) call.reject("cancelled")
                else call.reject("generation_failed", error.javaClass.simpleName)
            }
        }
    }

    @PluginMethod
    fun cancelGeneration(call: PluginCall) {
        generationCancelledByUser = true
        engine.cancel()
        call.resolve()
    }

    @PluginMethod
    fun getBenchmarkInfo(call: PluginCall) {
        val info = engine.benchmarkInfo()
        call.resolve(JSObject().apply {
            put("available", info != null)
            put("initTimeMs", ((info?.initTimeInSecond ?: 0.0) * 1000).toLong())
            put("ttftMs", ((info?.timeToFirstTokenInSecond ?: 0.0) * 1000).toLong())
            put("prefillTokens", info?.lastPrefillTokenCount ?: 0)
            put("decodeTokens", info?.lastDecodeTokenCount ?: 0)
            put("prefillTokensPerSecond", info?.lastPrefillTokensPerSecond ?: 0.0)
            put("decodeTokensPerSecond", info?.lastDecodeTokensPerSecond ?: 0.0)
            put("tokenCount", engine.tokenCount())
        })
    }
}
