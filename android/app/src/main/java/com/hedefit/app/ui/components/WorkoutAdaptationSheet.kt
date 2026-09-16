package com.hedefit.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.NightlightRound
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hedefit.app.data.model.WorkoutAdaptationResultData
import com.hedefit.app.ui.theme.HedefitColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutAdaptationSheet(
    onDismiss: () -> Unit,
    onSelectTrigger: (trigger: String, targetMinutes: Int?) -> Unit,
    adaptationResult: WorkoutAdaptationResultData? = null,
    isBusy: Boolean = false,
    onApplyAdaptation: (WorkoutAdaptationResultData) -> Unit,
    locale: String = "tr",
) {
    val en = locale == "en"

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = HedefitColors.Surface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = HedefitColors.TextSecondary.copy(alpha = 0.4f)) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = if (en) "Adapt Workout Plan" else "Antrenman Planını Uyarla",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = HedefitColors.TextPrimary,
                    )
                    Text(
                        text = if (en) "Smart adjustment without canceling your workout" else "Antrenmanı iptal etmeden günün koşullarına göre akıllı uyarlama",
                        style = MaterialTheme.typography.bodySmall,
                        color = HedefitColors.TextSecondary,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Kapat", tint = HedefitColors.TextSecondary)
                }
            }

            HorizontalDivider(color = HedefitColors.Divider, thickness = 0.5.dp)

            if (isBusy) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(color = HedefitColors.Lime)
                        Text(
                            text = if (en) "Adapting your workout..." else "Antrenman uyarlanıyor...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = HedefitColors.TextSecondary,
                        )
                    }
                }
            } else if (adaptationResult != null) {
                // Result presentation
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = HedefitColors.SurfaceHigh),
                    border = BorderStroke(1.dp, HedefitColors.Lime.copy(alpha = 0.4f)),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(HedefitColors.Lime.copy(alpha = 0.2f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = HedefitColors.Lime, modifier = Modifier.size(20.dp))
                            }
                            Column {
                                Text(
                                    text = if (en) "Adaptation Ready" else "Uyarlama Hazır",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = HedefitColors.TextPrimary,
                                )
                                Text(
                                    text = "${adaptationResult.originalDurationMinutes} dk → ${adaptationResult.adaptedDurationMinutes} dk",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = HedefitColors.Lime,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }

                        Text(
                            text = adaptationResult.explanationTr,
                            style = MaterialTheme.typography.bodyMedium,
                            color = HedefitColors.TextPrimary,
                            lineHeight = 20.sp,
                        )

                        if (adaptationResult.changes.isNotEmpty()) {
                            Text(
                                text = if (en) "Changes:" else "Yapılan Değişiklikler:",
                                style = MaterialTheme.typography.labelMedium,
                                color = HedefitColors.TextSecondary,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                adaptationResult.changes.forEach { change ->
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.Top,
                                    ) {
                                        Text("•", color = HedefitColors.Lime, fontWeight = FontWeight.Bold)
                                        Text(change, style = MaterialTheme.typography.bodySmall, color = HedefitColors.TextPrimary)
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = HedefitColors.TextSecondary),
                        border = BorderStroke(1.dp, HedefitColors.Divider),
                    ) {
                        Text(if (en) "Cancel" else "Vazgeç")
                    }

                    Button(
                        onClick = { onApplyAdaptation(adaptationResult); onDismiss() },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = HedefitColors.Lime, contentColor = HedefitColors.OnLime),
                    ) {
                        Text(if (en) "Apply" else "Uygula", fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                // Trigger Options List
                Text(
                    text = if (en) "Choose your current situation:" else "Mevcut durumuna uygun seçeneği belirle:",
                    style = MaterialTheme.typography.bodySmall,
                    color = HedefitColors.TextSecondary,
                )

                AdaptationOptionTile(
                    icon = Icons.Default.Timer,
                    title = if (en) "15-Minute Express" else "15 Dakikalık Hızlı Seans",
                    subtitle = if (en) "Short on time, keep compound movements and supersets" else "Vaktin çok azsa sadece temel bileşik hareketleri tutar",
                    onClick = { onSelectTrigger("time_shortage", 15) },
                )

                AdaptationOptionTile(
                    icon = Icons.Default.Timer,
                    title = if (en) "20-Minute Compact" else "20 Dakikalık Kompakt Seans",
                    subtitle = if (en) "High efficiency compact volume for main muscles" else "Kısa sürede ana kas gruplarını çalıştıracak ideal süre",
                    onClick = { onSelectTrigger("time_shortage", 20) },
                )

                AdaptationOptionTile(
                    icon = Icons.Default.Timer,
                    title = if (en) "30-Minute Balanced" else "30 Dakikalık Dengeli Seans",
                    subtitle = if (en) "Prunes isolation movements while preserving core stimulus" else "İzolasyon hareketleri elenir, verim korunur",
                    onClick = { onSelectTrigger("time_shortage", 30) },
                )

                AdaptationOptionTile(
                    icon = Icons.Default.Flight,
                    title = if (en) "Travel / Bodyweight Only" else "Seyahat / Sadece Vücut Ağırlığı",
                    subtitle = if (en) "No gym or equipment, converts to bodyweight variations" else "Ekipman yoksa tüm hareketleri vücut ağırlığı varyasyonlarına çevirir",
                    onClick = { onSelectTrigger("travel", null) },
                )

                AdaptationOptionTile(
                    icon = Icons.Default.NightlightRound,
                    title = if (en) "Fatigue Deload" else "Yorgunluk Deload'u",
                    subtitle = if (en) "Low energy: reduces volume by 40% and extends rest" else "Düşük enerji veya yorgunlukta setleri azaltıp dinlenmeyi uzatır",
                    onClick = { onSelectTrigger("acute_fatigue", null) },
                )
            }
        }
    }
}

@Composable
private fun AdaptationOptionTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = HedefitColors.SurfaceHigh),
        border = BorderStroke(1.dp, HedefitColors.Divider),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(HedefitColors.Lime.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = HedefitColors.Lime, modifier = Modifier.size(22.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = HedefitColors.TextPrimary,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = HedefitColors.TextSecondary,
                    lineHeight = 16.sp,
                )
            }
        }
    }
}
