package com.hedefit.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import com.hedefit.app.health.WearableSnapshot
import com.hedefit.app.ui.components.*
import com.hedefit.app.ui.theme.HedefitColors
import java.time.Duration
import java.time.Instant

private enum class WatchSupport { HEALTH_CONNECT, NOT_SUPPORTED, IOS_ONLY }

private data class WatchBrand(
    val key: String,
    val name: String,
    val appName: String,
    val packages: List<String>,
    val support: WatchSupport,
    val stepsTr: List<String>,
    val stepsEn: List<String>,
)

private val WATCH_BRANDS = listOf(
    WatchBrand(
        "samsung", "Samsung Galaxy Watch", "Samsung Health", listOf("com.sec.android.app.shealth"), WatchSupport.HEALTH_CONNECT,
        listOf("Saatini Galaxy Wearable uygulamasıyla telefonuna eşleştir.", "Samsung Health'i aç: Ayarlar → Health Connect.", "Adım, uyku, nabız ve kaloriye izin ver.", "Hedefit'te \"İzinleri ver\"e dokun."),
        listOf("Pair the watch with the Galaxy Wearable app.", "Open Samsung Health: Settings → Health Connect.", "Allow steps, sleep, heart rate and calories.", "Tap \"Grant permissions\" in Hedefit."),
    ),
    WatchBrand(
        "garmin", "Garmin", "Garmin Connect", listOf("com.garmin.android.apps.connectmobile"), WatchSupport.HEALTH_CONNECT,
        listOf("Saatini Garmin Connect uygulamasına ekle.", "Garmin Connect: Diğer → Ayarlar → Bağlı Uygulamalar → Health Connect.", "Paylaşılacak verileri aç.", "Hedefit'te \"İzinleri ver\"e dokun."),
        listOf("Add your watch in Garmin Connect.", "Garmin Connect: More → Settings → Connected Apps → Health Connect.", "Turn on the data to share.", "Tap \"Grant permissions\" in Hedefit."),
    ),
    WatchBrand(
        "xiaomi", "Xiaomi / Redmi", "Mi Fitness", listOf("com.xiaomi.wearable", "com.mi.health", "com.xiaomi.hm.health", "com.mi.health.global"), WatchSupport.HEALTH_CONNECT,
        listOf("Saatini Mi Fitness uygulamasına ekle.", "Mi Fitness: Profil → Üçüncü taraf veri erişimi → Health Connect.", "Senkronizasyonu aç.", "Hedefit'te \"İzinleri ver\"e dokun."),
        listOf("Add your watch in Mi Fitness.", "Mi Fitness: Profile → Third-party data access → Health Connect.", "Turn on sync.", "Tap \"Grant permissions\" in Hedefit."),
    ),
    WatchBrand(
        "amazfit", "Amazfit / Zepp", "Zepp", listOf("com.huami.watch.hmwatchmanager", "com.xiaomi.hm.health"), WatchSupport.HEALTH_CONNECT,
        listOf("Saatini Zepp uygulamasına ekle.", "Zepp: Profil → Veri bağlantıları → Health Connect.", "Senkronizasyonu aç.", "Hedefit'te \"İzinleri ver\"e dokun."),
        listOf("Add your watch in Zepp.", "Zepp: Profile → Add accounts → Health Connect.", "Turn on sync.", "Tap \"Grant permissions\" in Hedefit."),
    ),
    WatchBrand(
        "google", "Pixel Watch / Fitbit", "Fitbit", listOf("com.fitbit.FitbitMobile", "com.google.android.apps.wearables.maestro.companion"), WatchSupport.HEALTH_CONNECT,
        listOf("Saatini Pixel Watch / Fitbit uygulamasıyla eşleştir.", "Fitbit: Profil → Ayarlar → Health Connect.", "Verileri paylaşmayı aç.", "Hedefit'te \"İzinleri ver\"e dokun."),
        listOf("Pair the watch with the Pixel Watch / Fitbit app.", "Fitbit: Profile → Settings → Health Connect.", "Turn on data sharing.", "Tap \"Grant permissions\" in Hedefit."),
    ),
    WatchBrand(
        "huawei", "Huawei", "Huawei Health", listOf("com.huawei.health"), WatchSupport.NOT_SUPPORTED,
        listOf("Huawei Health şu an Health Connect'e veri göndermiyor; bu yüzden Huawei saatler doğrudan bağlanamıyor."),
        listOf("Huawei Health doesn't share data with Health Connect yet, so Huawei watches can't connect directly."),
    ),
    WatchBrand(
        "apple", "Apple Watch", "Apple Health", emptyList(), WatchSupport.IOS_ONLY,
        listOf("Apple Watch verileri yalnızca iPhone'daki Apple Health'te tutulur. Hedefit'in iPhone sürümüyle bağlanacak."),
        listOf("Apple Watch data lives only in Apple Health on iPhone. It will connect with Hedefit's iPhone app."),
    ),
)

@Composable
fun WearablesScreen(
    snapshot: WearableSnapshot?,
    busy: Boolean,
    error: String?,
    steps: Int,
    sleepMinutes: Int,
    language: String,
    healthSdkStatus: Int,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onGrantPermissions: () -> Unit,
) {
    val en = language == "en"
    val context = LocalContext.current
    var expanded by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { onRefresh() }
    val connectedKeys = WATCH_BRANDS.filter { brand -> brand.packages.any { it in snapshot?.sourceLastSeen.orEmpty() } }.map { it.key }.toSet()
    ScreenContainer {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp, 12.dp, 18.dp, 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                HfScreenHeader(if (en) "Smart watches" else "Akıllı saatler", onBack = onBack, backLabel = if (en) "Back" else "Geri") {
                    if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = HedefitColors.Lime)
                    else HfCircleButton(Icons.Default.Refresh, if (en) "Refresh" else "Yenile", onRefresh)
                }
            }
            item {
                HedefitCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            HfIconBadge(Icons.Default.Favorite, HedefitColors.Lime, 40.dp, 20.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Health Connect", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                                Text(
                                    when {
                                        healthSdkStatus != HealthConnectClient.SDK_AVAILABLE -> if (en) "Not available on this phone" else "Bu telefonda kullanılamıyor"
                                        connectedKeys.isNotEmpty() -> if (en) "${connectedKeys.size} watch app sending data" else "${connectedKeys.size} saat uygulaması veri gönderiyor"
                                        else -> if (en) "Waiting for watch data" else "Saat verisi bekleniyor"
                                    },
                                    color = if (connectedKeys.isNotEmpty()) HedefitColors.Lime else HedefitColors.Warning,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                        if (healthSdkStatus != HealthConnectClient.SDK_AVAILABLE) {
                            HfPrimaryButton(if (en) "Install Health Connect" else "Health Connect'i yükle", { openStore(context, "com.google.android.apps.healthdata") }, Modifier.fillMaxWidth())
                        } else if (snapshot?.heartPermissionGranted != true) {
                            HfPrimaryButton(if (en) "Grant permissions" else "İzinleri ver", onGrantPermissions, Modifier.fillMaxWidth(), Icons.Default.CheckCircle)
                        }
                        error?.let { Text(it, color = HedefitColors.Coral) }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HfStatTile(if (en) "Heart rate" else "Nabız", snapshot?.latestHeartRate?.let { "$it bpm" } ?: "—", Modifier.weight(1f), valueColor = HedefitColors.Coral)
                    HfStatTile(if (en) "Resting" else "Dinlenik", snapshot?.restingHeartRate?.let { "$it bpm" } ?: "—", Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HfStatTile(if (en) "Steps" else "Adım", "%,d".format(steps).replace(',', '.'), Modifier.weight(1f), valueColor = HedefitColors.Water)
                    HfStatTile(if (en) "Sleep" else "Uyku", if (sleepMinutes > 0) "${sleepMinutes / 60}s ${sleepMinutes % 60}dk" else "—", Modifier.weight(1f), valueColor = HedefitColors.Sleep)
                }
            }
            if (snapshot?.devices?.isNotEmpty() == true) {
                item { HfSectionHeader(if (en) "Connected devices" else "Bağlı cihazlar") }
                items(snapshot.devices, key = { it.label }) { device ->
                    HfNavRow(Icons.Default.Watch, HedefitColors.Lime, device.label, null, null, trailing = {
                        Text(relativeTime(device.lastSeen, en), color = HedefitColors.Lime, fontWeight = FontWeight.Bold)
                    })
                }
            }
            item { HfSectionHeader(if (en) "Brands" else "Markalar") }
            items(WATCH_BRANDS, key = { it.key }) { brand ->
                // Paket adına ek olarak cihaz üreticisine göre de eşle: veri aracı bir uygulamadan
                // gelse bile (ör. Zepp Life, Mi Fitness küresel sürümü) "Xiaomi" cihazı görünür.
                val brandWord = brand.name.substringBefore(" ").substringBefore("/").trim().lowercase()
                val lastSeen = (brand.packages.mapNotNull { snapshot?.sourceLastSeen?.get(it) } +
                    snapshot?.devices.orEmpty().filter { it.label.lowercase().contains(brandWord) || it.sourcePackage in brand.packages }.map { it.lastSeen })
                    .maxOrNull()
                val installedPackage = brand.packages.firstOrNull { context.packageManager.getLaunchIntentForPackage(it) != null }
                val open = expanded == brand.key
                HedefitCard(Modifier.fillMaxWidth(), onClick = { expanded = if (open) null else brand.key }, contentPadding = PaddingValues(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            HfIconBadge(Icons.Default.Watch, if (lastSeen != null) HedefitColors.Lime else HedefitColors.TextSecondary, 38.dp, 20.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(brand.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                                Text(
                                    when {
                                        lastSeen != null -> if (en) "Connected • ${relativeTime(lastSeen, en)}" else "Bağlı • ${relativeTime(lastSeen, en)}"
                                        brand.support == WatchSupport.IOS_ONLY -> if (en) "Coming with the iPhone app" else "iPhone sürümüyle gelecek"
                                        brand.support == WatchSupport.NOT_SUPPORTED -> if (en) "Not supported yet" else "Şu an desteklenmiyor"
                                        installedPackage != null -> if (en) "${brand.appName} installed • not syncing" else "${brand.appName} yüklü • senkron kapalı"
                                        else -> if (en) "Not connected" else "Bağlı değil"
                                    },
                                    color = when {
                                        lastSeen != null -> HedefitColors.Lime
                                        brand.support != WatchSupport.HEALTH_CONNECT -> HedefitColors.TextSecondary
                                        else -> HedefitColors.Warning
                                    },
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                            Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = HedefitColors.TextSecondary)
                        }
                        if (open) {
                            (if (en) brand.stepsEn else brand.stepsTr).forEachIndexed { index, step ->
                                Row(verticalAlignment = Alignment.Top) {
                                    if (brand.support == WatchSupport.HEALTH_CONNECT) {
                                        Box(Modifier.size(24.dp).background(HedefitColors.Lime.copy(alpha = .16f), CircleShape), contentAlignment = Alignment.Center) {
                                            Text("${index + 1}", color = HedefitColors.Lime, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.labelMedium)
                                        }
                                        Spacer(Modifier.width(10.dp))
                                    }
                                    Text(step, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                }
                            }
                            if (brand.support == WatchSupport.HEALTH_CONNECT) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                HfPrimaryButton(
                                    if (installedPackage != null) (if (en) "Open ${brand.appName}" else "${brand.appName}'i aç") else if (en) "Install ${brand.appName}" else "${brand.appName}'i yükle",
                                    {
                                        if (installedPackage != null) context.packageManager.getLaunchIntentForPackage(installedPackage)?.let(context::startActivity)
                                        else openStore(context, brand.packages.first())
                                    },
                                    Modifier.weight(1f),
                                    secondary = true,
                                )
                                HfPrimaryButton(if (en) "Permissions" else "İzinler", onGrantPermissions, Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun openStore(context: Context, packageName: String) {
    val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(market) }.onFailure {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private fun relativeTime(instant: Instant, en: Boolean): String {
    val minutes = Duration.between(instant, Instant.now()).toMinutes().coerceAtLeast(0)
    return when {
        minutes < 1 -> if (en) "just now" else "az önce"
        minutes < 60 -> if (en) "${minutes}m ago" else "$minutes dk önce"
        minutes < 1_440 -> if (en) "${minutes / 60}h ago" else "${minutes / 60} sa önce"
        else -> if (en) "${minutes / 1_440}d ago" else "${minutes / 1_440} gün önce"
    }
}
