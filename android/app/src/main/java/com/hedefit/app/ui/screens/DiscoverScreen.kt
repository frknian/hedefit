package com.hedefit.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hedefit.app.ui.components.HedefitCard
import com.hedefit.app.ui.components.HfActionTile
import com.hedefit.app.ui.components.HfScreenHeader
import com.hedefit.app.ui.components.HfSectionHeader
import com.hedefit.app.ui.components.MuscleMap
import com.hedefit.app.ui.components.ScreenContainer
import com.hedefit.app.ui.theme.HedefitColors

/** Yeni kullanıcı için tek giriş noktası: ara, vücut haritasına dokun ya da bir araç seç. */
@Composable
fun DiscoverScreen(
    language: String,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onMuscle: (String) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenCardio: () -> Unit,
    onOpenRoute: () -> Unit,
    onOpenScanner: () -> Unit,
    onOpenGame: () -> Unit,
    onModality: (String) -> Unit = {},
) {
    val en = language == "en"
    ScreenContainer {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { HfScreenHeader(if (en) "Discover" else "Keşfet", if (en) "Exercises, muscles and tools" else "Hareketler, kaslar ve araçlar", onBack = onBack, backLabel = if (en) "Back" else "Geri") }
            item {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(HedefitColors.Surface).clickable(onClick = onSearch).padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Default.Search, null, tint = HedefitColors.TextMuted)
                    Text(if (en) "What shall we train?" else "Ne çalışalım?", color = HedefitColors.TextMuted, style = MaterialTheme.typography.bodyLarge)
                }
            }
            item { HfSectionHeader(if (en) "Tap a muscle" else "Çalışmak istediğin kasa dokun") }
            item {
                HedefitCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        MuscleMap(
                            selected = emptySet(), onSelect = onMuscle,
                            description = if (en) "Tap a muscle to list its exercises" else "Hareketlerini görmek için bir kasa dokun",
                        )
                        Text(if (en) "Front and back view • tap to see the exercises" else "Ön ve arka görünüm • dokununca hareketleri listelenir", color = HedefitColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item { HfSectionHeader(if (en) "Training styles" else "Antrenman tarzları") }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.height(IntrinsicSize.Min)) {
                        HfActionTile(Icons.Default.SelfImprovement, HedefitColors.Lime, "Pilates", if (en) "Core, posture, control" else "Core, duruş, kontrol", { onModality("pilates") }, Modifier.weight(1f).fillMaxHeight())
                        HfActionTile(Icons.Default.AccessibilityNew, HedefitColors.Lime, if (en) "Mobility" else "Mobilite", if (en) "Hips, back, shoulders" else "Kalça, sırt, omuz", { onModality("mobility") }, Modifier.weight(1f).fillMaxHeight())
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.height(IntrinsicSize.Min)) {
                        HfActionTile(Icons.Default.FitnessCenter, HedefitColors.Lime, "Barre", if (en) "Lower body and balance" else "Alt vücut ve denge", { onModality("barre") }, Modifier.weight(1f).fillMaxHeight())
                        HfActionTile(Icons.AutoMirrored.Filled.DirectionsRun, HedefitColors.Lime, if (en) "Low impact" else "Düşük etkili", if (en) "No jumping, joint-friendly" else "Zıplamadan, eklem dostu", { onModality("low_impact") }, Modifier.weight(1f).fillMaxHeight())
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.height(IntrinsicSize.Min)) {
                        HfActionTile(Icons.Default.Spa, HedefitColors.Lime, if (en) "Recovery" else "Toparlanma", if (en) "Stretching and breathing" else "Esneme ve nefes", { onModality("recovery") }, Modifier.weight(1f).fillMaxHeight())
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
            item { HfSectionHeader(if (en) "Explore" else "Keşfet") }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.height(IntrinsicSize.Min)) {
                        HfActionTile(Icons.Default.MenuBook, HedefitColors.Lime, if (en) "Movement Atlas" else "Hareket Atlası", if (en) "600+ animated exercises" else "600+ animasyonlu hareket", onOpenLibrary, Modifier.weight(1f).fillMaxHeight())
                        HfActionTile(Icons.AutoMirrored.Filled.DirectionsRun, HedefitColors.Lime, if (en) "Cardio" else "Kardiyo", if (en) "Treadmill, bike, rower" else "Koşu bandı, bisiklet, kürek", onOpenCardio, Modifier.weight(1f).fillMaxHeight())
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.height(IntrinsicSize.Min)) {
                        HfActionTile(Icons.Default.Route, HedefitColors.Lime, if (en) "Hedefit Route" else "Hedefit Rota", if (en) "GPS run or walk" else "GPS ile koşu, yürüyüş", onOpenRoute, Modifier.weight(1f).fillMaxHeight())
                        HfActionTile(Icons.Default.CameraAlt, HedefitColors.Lime, if (en) "Scan equipment" else "Ekipman tara", if (en) "Recognise a machine" else "Makineyi kamerayla tanı", onOpenScanner, Modifier.weight(1f).fillMaxHeight())
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.height(IntrinsicSize.Min)) {
                        HfActionTile(Icons.Default.SportsEsports, HedefitColors.Lime, if (en) "Game" else "Oyun", if (en) "Train your athlete" else "Sporcunu çalıştır", onOpenGame, Modifier.weight(1f).fillMaxHeight())
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
