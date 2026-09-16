package com.hedefit.app.ui.components

import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.hedefit.app.BuildConfig
import com.hedefit.app.ui.theme.HedefitColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.URL
import java.io.ByteArrayInputStream

private object ExerciseImageCache {
    private val cache = object : LruCache<String, Bitmap>(16 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }
    fun get(key: String) = cache.get(key)
    fun put(key: String, value: Bitmap) = cache.put(key, value)
}

private val legacyWorkoutImageIds = mapOf(
    "barbell-bench-press" to "Barbell_Bench_Press_-_Medium_Grip",
    "incline-dumbbell-press" to "Incline_Dumbbell_Press",
    "seated-dumbbell-press" to "Seated_Dumbbell_Press",
    "dumbbell-lateral-raise" to "Side_Lateral_Raise",
    "dumbbell-lateral-raise-b" to "Side_Lateral_Raise",
    "cable-rope-triceps-pushdown" to "Triceps_Pushdown_-_Rope_Attachment",
    "barbell-shoulder-press" to "Barbell_Shoulder_Press",
    "dumbbell-bench-press" to "Dumbbell_Bench_Press",
    "cable-crossover" to "Cable_Crossover",
    "overhead-cable-triceps-extension" to "Cable_Rope_Overhead_Triceps_Extension",
    "pullups" to "Pullups",
    "wide-grip-lat-pulldown" to "Wide-Grip_Lat_Pulldown",
    "seated-cable-row" to "Seated_Cable_Rows",
    "face-pull" to "Face_Pull",
    "incline-dumbbell-curl" to "Incline_Dumbbell_Curl",
    "barbell-row" to "Bent_Over_Barbell_Row",
    "close-grip-lat-pulldown" to "Close-Grip_Front_Lat_Pulldown",
    "chest-supported-dumbbell-row" to "Incline_Bench_Pull",
    "reverse-pec-deck" to "Reverse_Machine_Flyes",
    "hammer-curl" to "Hammer_Curls",
)

/**
 * RepDB (bkz. scripts/import-repdb.mjs) `id`'yi doğrudan görsel klasör adı
 * olarak kullanır ve dosyaları `start.webp`/`peak.webp` (çift pozlu hareketler)
 * ya da `main.webp` (esnetme gibi tek pozlu hareketler) olarak adlandırır.
 * Hangisinin var olduğu istemciden bilinmediği için üçü de aday olarak
 * denenir; `ExerciseMedia`/`ExerciseMotionPlayer` ilk başarısız olan adayı
 * atlayıp bir sonrakini dener.
 *
 * RepDB adayları HER ZAMAN önce denenir: `legacyWorkoutImageIds` eski
 * (free-exercise-db) planlar için elle eşlenmiş bir yedek — ama RepDB kendi
 * kebab-case id'lerini kullandığından ("barbell-row", "close-grip-lat-pulldown"
 * gibi) bazı RepDB id'leri bu tabloda tesadüfen anahtar olarak da geçiyor.
 * Legacy'yi önce denemek bu id'lerde YANLIŞ (eski) görseli gösterirdi; RepDB
 * önce denendiğinde doğru görsel bulunduğu an legacy hiç denenmez.
 */
private fun repdbImageCandidates(id: String) = listOf(
    "/exercise-images/$id/start.webp",
    "/exercise-images/$id/main.webp",
    "/exercise-images/$id/peak.webp",
)

private fun legacyImageCandidates(id: String): List<String> {
    val legacyId = legacyWorkoutImageIds[id.lowercase()] ?: return emptyList()
    return listOf("/exercise-images/$legacyId/0.jpg", "/exercise-images/$legacyId/1.jpg")
}

internal fun workoutExerciseImagePaths(id: String, name: String): List<String> =
    repdbImageCandidates(id) + legacyImageCandidates(id)

private fun sampledBitmap(address: String, maxDimension: Int): Bitmap? {
    val cacheKey = "$address@$maxDimension"
    ExerciseImageCache.get(cacheKey)?.let { return it }
    val connection = URL(address).openConnection().apply { connectTimeout = 6_000; readTimeout = 8_000 }
    val bytes = connection.getInputStream().use { it.readBytes() }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (bounds.outWidth / sample > maxDimension || bounds.outHeight / sample > maxDimension) sample *= 2
    return BitmapFactory.decodeStream(ByteArrayInputStream(bytes), null, BitmapFactory.Options().apply { inSampleSize = sample })?.also { ExerciseImageCache.put(cacheKey, it) }
}

@Composable
fun ExerciseMedia(path: String?, description: String, modifier: Modifier = Modifier, maxDimension: Int = 256) {
    ExerciseMedia(listOfNotNull(path), description, modifier, maxDimension)
}

/**
 * `paths` sırayla denenir; ilk başarıyla yüklenen gösterilir. RepDB
 * hareketlerinin start/main/peak adaylarından hangisinin gerçekten var
 * olduğu istemciden bilinmediği için (bkz. `workoutExerciseImagePaths`)
 * gereklidir — tek `path` alan sürüm sadece onu sarmalar.
 */
@Composable
fun ExerciseMedia(paths: List<String>, description: String, modifier: Modifier = Modifier, maxDimension: Int = 256) {
    val fullUrls = remember(paths) {
        paths.filter(String::isNotBlank).map { if (it.startsWith("http")) it else "${BuildConfig.API_BASE_URL.trimEnd('/')}/${it.trimStart('/')}" }
    }
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, fullUrls) {
        value = withContext(Dispatchers.IO) {
            fullUrls.firstNotNullOfOrNull { address -> runCatching { sampledBitmap(address, maxDimension)?.asImageBitmap() }.getOrNull() }
        }
    }
    Box(modifier.background(HedefitColors.SurfaceHigh), contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it, description, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            ?: Icon(Icons.Default.FitnessCenter, description, tint = HedefitColors.Lime)
    }
}

@Composable
fun ExerciseMotionPlayer(id: String, name: String, description: String, modifier: Modifier = Modifier) {
    ExerciseMotionPlayer(repdbImageCandidates(id), legacyImageCandidates(id), description, modifier)
}

@Composable
fun ExerciseMotionPlayer(paths: List<String>, description: String, modifier: Modifier = Modifier) {
    ExerciseMotionPlayer(paths, emptyList(), description, modifier)
}

/**
 * `primaryPaths` ve `fallbackPaths` ayrı gruplar olarak yüklenir: `fallbackPaths`
 * yalnızca `primaryPaths`'ten HİÇBİR kare yüklenemezse denenir. Bu, RepDB
 * (doğru) ve legacy (eski/yanlış) görsellerin aynı animasyonda karışmasını
 * önler — id çakışması olan hareketlerde (bkz. `legacyWorkoutImageIds`)
 * kritik.
 */
@Composable
private fun ExerciseMotionPlayer(primaryPaths: List<String>, fallbackPaths: List<String>, description: String, modifier: Modifier = Modifier) {
    fun toUrls(paths: List<String>) = paths.filter(String::isNotBlank).map { if (it.startsWith("http")) it else "${BuildConfig.API_BASE_URL.trimEnd('/')}/${it.trimStart('/')}" }
    val primaryUrls = remember(primaryPaths) { toUrls(primaryPaths) }
    val fallbackUrls = remember(fallbackPaths) { toUrls(fallbackPaths) }
    val frames by produceState<List<ImageBitmap>>(emptyList(), primaryUrls, fallbackUrls) {
        value = withContext(Dispatchers.IO) {
            val primaryFrames = primaryUrls.mapNotNull { address -> runCatching { sampledBitmap(address, 768)?.asImageBitmap() }.getOrNull() }
            primaryFrames.ifEmpty { fallbackUrls.mapNotNull { address -> runCatching { sampledBitmap(address, 768)?.asImageBitmap() }.getOrNull() } }
        }
    }
    var playing by remember { mutableStateOf(true) }
    var frameIndex by remember { mutableStateOf(0) }
    LaunchedEffect(frames.size, playing) {
        while (playing && frames.size > 1) {
            delay(850)
            frameIndex = (frameIndex + 1) % frames.size
        }
    }
    // RepDB'de tek pozlu (start/peak ayrımı olmayan — esneme, plank gibi
    // izometrik) hareketlerde ikinci bir kare fiziksel olarak yok, bu yüzden
    // gerçek bir "hareket" animasyonu üretilemiyor. Bunun yerine görsele
    // sabit bir "nefes alma" pulse'u uygulanır — statik değil, canlı hissettirir.
    val pulseTransition = rememberInfiniteTransition(label = "exercise-pulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.035f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "exercise-pulse-scale",
    )
    Box(modifier.background(HedefitColors.SurfaceHigh), contentAlignment = Alignment.Center) {
        if (frames.isEmpty()) Icon(Icons.Default.FitnessCenter, description, tint = HedefitColors.Lime)
        else Crossfade(targetState = frameIndex.coerceAtMost(frames.lastIndex), label = "exercise-motion") { index ->
            Image(
                frames[index],
                "$description hareket gösterimi",
                Modifier.fillMaxSize().graphicsLayer {
                    if (frames.size == 1) { scaleX = pulseScale; scaleY = pulseScale }
                },
                contentScale = ContentScale.Fit,
            )
        }
        if (frames.size > 1) {
            IconButton(
                onClick = { playing = !playing },
                modifier = Modifier.align(Alignment.BottomEnd).size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = .68f)),
            ) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (playing) "Animasyonu duraklat" else "Animasyonu oynat", tint = HedefitColors.Lime) }
        }
    }
}
