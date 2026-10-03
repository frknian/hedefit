package com.hedefit.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hedefit.app.data.model.AiMemoryItem
import com.hedefit.app.data.model.ConsentStatus
import com.hedefit.app.data.model.aiMemoryTypeLabel
import com.hedefit.app.ui.components.*
import com.hedefit.app.ui.theme.HedefitColors

/**
 * Ayarlar → Koç hafızası. Gizlilik metninde söylenenin uygulamadaki karşılığı: sunucuda hesabına bağlı
 * saklanan notları görebilir, tek tek ya da toplu silebilirsin.
 */
@Composable
fun AiMemoryScreen(
    memories: List<AiMemoryItem>?,
    busy: Boolean,
    error: String?,
    language: String,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: (String) -> Unit,
    onDeleteAll: () -> Unit,
) {
    val en = language == "en"
    var confirmAll by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { onRefresh() }

    if (confirmAll) {
        AlertDialog(
            onDismissRequest = { confirmAll = false },
            title = { Text(if (en) "Delete all notes?" else "Tüm notlar silinsin mi?") },
            text = { Text(if (en) "Fit Coach will forget your saved preferences, goals and constraints. This can't be undone." else "FitKoç kayıtlı tercihlerini, hedeflerini ve kısıtlarını unutur. Bu işlem geri alınamaz.") },
            dismissButton = { TextButton(onClick = { confirmAll = false }) { Text(if (en) "Cancel" else "Vazgeç") } },
            confirmButton = { TextButton(onClick = { confirmAll = false; onDeleteAll() }) { Text(if (en) "Delete all" else "Hepsini sil", color = HedefitColors.Coral) } },
        )
    }

    ScreenContainer {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp, 12.dp, 18.dp, 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                HfScreenHeader(if (en) "Coach memory" else "Koç hafızası", onBack = onBack, backLabel = if (en) "Back" else "Geri") {
                    if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = HedefitColors.Lime)
                    else HfCircleButton(Icons.Default.Refresh, if (en) "Refresh" else "Yenile", onRefresh)
                }
            }
            item {
                HedefitCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        HfIconBadge(Icons.Default.Psychology, HedefitColors.Lime, 40.dp, 20.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            if (en) "Fit Coach keeps short notes about you (preferences, goals, constraints, habits) to answer more fittingly. They're stored on our server linked to your account, sent to the AI provider as context, and capped at 60 notes. You can remove any of them here."
                            else "FitKoç, sana daha uygun yanıt verebilmek için hakkında kısa notlar (tercih, hedef, kısıt, alışkanlık) tutar. Notlar hesabına bağlı olarak sunucuda saklanır, bağlam olarak yapay zekâ sağlayıcısına gönderilir ve en fazla 60 tanedir. Buradan istediğini kaldırabilirsin.",
                            color = HedefitColors.TextSecondary, fontSize = 13.sp,
                        )
                    }
                }
            }
            if (error != null) item { Text(error, color = HedefitColors.Coral, fontSize = 13.sp) }
            when {
                memories == null && busy -> Unit
                memories.isNullOrEmpty() -> item {
                    HedefitCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(20.dp)) {
                        Text(
                            if (en) "No notes saved yet." else "Henüz kayıtlı not yok.",
                            color = HedefitColors.TextSecondary, modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                else -> {
                    items(memories, key = { it.id }) { memory -> MemoryRow(memory, en, !busy) { onDelete(memory.id) } }
                    item {
                        TextButton(onClick = { confirmAll = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                            Text(if (en) "Delete all notes" else "Tüm notları sil", color = HedefitColors.Coral, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MemoryRow(memory: AiMemoryItem, en: Boolean, enabled: Boolean, onDelete: () -> Unit) {
    HedefitCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(aiMemoryTypeLabel(memory.type, en), color = HedefitColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("${memory.key}: ${memory.value}", color = HedefitColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                Text(
                    (if (memory.userExplicit) (if (en) "You told the coach" else "Koça sen söyledin") else (if (en) "Inferred from your chats" else "Sohbetlerinden çıkarıldı")) +
                        (memory.updatedAt?.take(10)?.let { " · $it" }.orEmpty()),
                    color = HedefitColors.TextMuted, fontSize = 12.sp,
                )
            }
            TextButton(onClick = onDelete, enabled = enabled) { Icon(Icons.Default.Delete, contentDescription = if (en) "Delete note" else "Notu sil", tint = HedefitColors.Coral) }
        }
    }
}

/**
 * Ayarlar → Gizlilik ve rızalar. KVKK açık rızalarının durumunu gösterir, yasal metinleri açar ve
 * rızayı geri çekmeye izin verir. Geri çekme: rıza kaydı silinir, oturum kapanır; hesap bir sonraki
 * girişte yeniden rıza ister. Verilerin silinmesi ayrı ("Hesabı kalıcı sil") bir işlemdir.
 */
@Composable
fun ConsentSettingsScreen(
    status: ConsentStatus?,
    busy: Boolean,
    error: String?,
    language: String,
    onBack: () -> Unit,
    onLoad: () -> Unit,
    onWithdraw: (health: Boolean, crossBorder: Boolean) -> Unit,
) {
    val en = language == "en"
    var document by remember { mutableStateOf<LegalDocument?>(null) }
    var withdrawing by remember { mutableStateOf<Pair<Boolean, Boolean>?>(null) }
    LaunchedEffect(Unit) { onLoad() }

    document?.let { LegalDocumentDialog(it) { document = null } }
    withdrawing?.let { (health, crossBorder) ->
        AlertDialog(
            onDismissRequest = { withdrawing = null },
            title = { Text(if (en) "Withdraw consent?" else "Rıza geri çekilsin mi?") },
            text = {
                Text(
                    if (en) "Hedefit can't work without this consent, so you'll be signed out and asked again next time you sign in. Your data is NOT deleted; to delete it, use \"Delete account permanently\" in settings, or write to $LEGAL_CONTACT."
                    else "Hedefit bu rıza olmadan çalışamaz; bu yüzden oturumun kapanır ve bir sonraki girişte rızan yeniden istenir. Verilerin SİLİNMEZ; silmek için ayarlardan \"Hesabı kalıcı sil\"i kullan ya da $LEGAL_CONTACT adresine yaz.",
                )
            },
            dismissButton = { TextButton(onClick = { withdrawing = null }) { Text(if (en) "Cancel" else "Vazgeç") } },
            confirmButton = { TextButton(onClick = { withdrawing = null; onWithdraw(health, crossBorder) }) { Text(if (en) "Withdraw and sign out" else "Geri çek ve çıkış yap", color = HedefitColors.Coral) } },
        )
    }

    ScreenContainer {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp, 12.dp, 18.dp, 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                HfScreenHeader(if (en) "Privacy and consents" else "Gizlilik ve rızalar", onBack = onBack, backLabel = if (en) "Back" else "Geri") {
                    if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = HedefitColors.Lime)
                }
            }
            if (error != null) item { Text(error, color = HedefitColors.Coral, fontSize = 13.sp) }
            item {
                ConsentCard(
                    title = if (en) "Health data processing" else "Sağlık verilerinin işlenmesi",
                    body = if (en) "Height, weight, measurements, workouts, nutrition, sleep, steps and any pain/injury info you report." else "Boy, kilo, ölçüler, antrenman, beslenme, uyku, adım ve bildirdiğin ağrı/sakatlık bilgileri.",
                    givenAt = status?.healthDataConsentAt,
                    loaded = status != null,
                    en = en,
                    enabled = !busy,
                ) { withdrawing = true to false }
            }
            item {
                ConsentCard(
                    title = if (en) "Transfer abroad" else "Yurt dışına aktarım",
                    body = if (en) "Transfer to servers and AI providers abroad (Supabase, Cloudflare, OpenAI, Google) to provide the service." else "Hizmetin sunulması için yurt dışındaki sunuculara ve yapay zekâ sağlayıcılarına (Supabase, Cloudflare, OpenAI, Google) aktarım.",
                    givenAt = status?.crossBorderConsentAt,
                    loaded = status != null,
                    en = en,
                    enabled = !busy,
                ) { withdrawing = false to true }
            }
            item {
                HedefitCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            (if (en) "Accepted text version: " else "Onaylanan metin sürümü: ") + (status?.textVersion ?: "—"),
                            color = HedefitColors.TextSecondary, fontSize = 13.sp,
                        )
                        TextButton(onClick = { document = LegalDocument.Kvkk }) { Text(if (en) "Read the KVKK Privacy Notice" else "KVKK Aydınlatma Metni'ni oku", color = HedefitColors.Lime) }
                        TextButton(onClick = { document = LegalDocument.Privacy }) { Text(if (en) "Read the Privacy Policy" else "Gizlilik Politikası'nı oku", color = HedefitColors.Lime) }
                        Text(
                            if (en) "For data requests (access, correction, deletion) write to $LEGAL_CONTACT."
                            else "Veri talepleri (erişim, düzeltme, silme) için $LEGAL_CONTACT adresine yaz.",
                            color = HedefitColors.TextMuted, fontSize = 12.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConsentCard(title: String, body: String, givenAt: String?, loaded: Boolean, en: Boolean, enabled: Boolean, onWithdraw: () -> Unit) {
    HedefitCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HfIconBadge(Icons.Default.Shield, if (givenAt != null) HedefitColors.Lime else HedefitColors.TextSecondary, 38.dp, 20.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, color = HedefitColors.TextPrimary, fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            !loaded -> if (en) "Loading…" else "Yükleniyor…"
                            givenAt != null -> (if (en) "Given on " else "Verildi: ") + givenAt.take(10)
                            else -> if (en) "Not given" else "Verilmedi"
                        },
                        color = HedefitColors.TextSecondary, fontSize = 12.sp,
                    )
                }
            }
            Text(body, color = HedefitColors.TextSecondary, fontSize = 13.sp)
            if (givenAt != null) {
                TextButton(onClick = onWithdraw, enabled = enabled) { Text(if (en) "Withdraw consent" else "Rızayı geri çek", color = HedefitColors.Coral, fontWeight = FontWeight.Bold) }
            }
        }
    }
}
