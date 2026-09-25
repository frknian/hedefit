package com.hedefit.app.ui.screens

import androidx.compose.ui.draw.alpha
import androidx.compose.material.icons.filled.Lock

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import com.hedefit.app.ui.components.AlertDialog
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hedefit.app.data.model.ExerciseCatalogData
import com.hedefit.app.ui.components.HedefitCard
import com.hedefit.app.ui.components.ExerciseMedia
import com.hedefit.app.ui.components.ExerciseMotionPlayer
import com.hedefit.app.ui.components.ScreenContainer
import com.hedefit.app.ui.theme.HedefitColors
import com.hedefit.app.ui.components.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.sp
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseLibraryScreen(items: List<ExerciseCatalogData>, loading: Boolean, language: String, onBack: () -> Unit, onSearch: (String, String, String, String, String, String, String, String, String) -> Unit, onUse: (ExerciseCatalogData) -> Unit, onStart: (ExerciseCatalogData) -> Unit, onCreateProgram: (String, List<ExerciseCatalogData>) -> Unit = { _, _ -> }, isLocked: (ExerciseCatalogData) -> Boolean = { false }, onLocked: (ExerciseCatalogData) -> Unit = {}) {
    val en = language == "en"
    var query by remember { mutableStateOf("") }
    var muscle by remember { mutableStateOf("") }
    var equipment by remember { mutableStateOf("") }
    var level by remember { mutableStateOf("") }
    var environment by remember { mutableStateOf("") }
    var muscleRole by remember { mutableStateOf("primary") }
    var force by remember { mutableStateOf("") }
    var mechanic by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var showFilters by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<ExerciseCatalogData?>(null) }
    var showCustom by remember { mutableStateOf(false) }
    var selectedForProgram by remember { mutableStateOf<List<ExerciseCatalogData>>(emptyList()) }
    var showNameDialog by remember { mutableStateOf(false) }
    val activeFilterCount = listOf(muscle, equipment, level, environment, force, mechanic, category).count(String::isNotBlank)
    val clearFilters = {
        muscle = ""; equipment = ""; level = ""; environment = ""; muscleRole = ""; force = ""; mechanic = ""; category = ""
        onSearch(query, "", "", "", "", "", "", "", "")
    }
    val search = { onSearch(query, muscle, equipment, level, environment, muscleRole, force, mechanic, category) }
    ScreenContainer { Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            HfScreenHeader(
                if (en) "Movement Atlas" else "Hareket Atlası",
                if (loading) (if (en) "Loading…" else "Yükleniyor…") else if (en) "${items.size} movements" else "${items.size} hareket",
                onBack = onBack,
                backLabel = if (en) "Back" else "Geri",
            ) { HfCircleButton(Icons.Default.Add, if (en) "Custom movement" else com.hedefit.app.ui.i18n.tr("Özel hareket", "Custom exercise"), { showCustom = true }) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    query,
                    { query = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(if (en) "Search movement or muscle" else "Hareket veya kas ara") },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = HedefitColors.TextMuted) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { search() }),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = HedefitColors.Surface, unfocusedContainerColor = HedefitColors.Surface,
                        focusedBorderColor = HedefitColors.Lime, unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                    ),
                )
                Box {
                    IconButton(onClick = { showFilters = true }, modifier = Modifier.size(56.dp).background(if (activeFilterCount > 0) HedefitColors.Lime else HedefitColors.Surface, RoundedCornerShape(16.dp))) {
                        Icon(Icons.Default.FilterList, if (en) "Filters, $activeFilterCount active" else "Filtreler, $activeFilterCount aktif", tint = if (activeFilterCount > 0) HedefitColors.OnLime else HedefitColors.TextPrimary)
                    }
                    if (activeFilterCount > 0) Text("$activeFilterCount", color = HedefitColors.Lime, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.align(Alignment.TopEnd).offset(4.dp, (-4).dp).background(HedefitColors.Surface, CircleShape).padding(horizontal = 5.dp, vertical = 1.dp))
                }
            }
            HfChipRow {
                muscleOptions(en).take(9).forEach { (value, label) ->
                    HfChip(label, muscle == value, {
                        muscle = value
                        muscleRole = if (value.isBlank()) "" else "primary"
                        onSearch(query, value, equipment, level, environment, if (value.isBlank()) "" else "primary", force, mechanic, category)
                    })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (en) "${items.size} results" else "${items.size} sonuç", color = HedefitColors.TextSecondary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (activeFilterCount > 0) TextButton(onClick = clearFilters) { Text(if (en) "Clear" else "Temizle", color = HedefitColors.Lime, fontWeight = FontWeight.ExtraBold) }
            }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = HedefitColors.Lime)
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(items, key = { it.id }) { item ->
                val isPicked = selectedForProgram.any { it.id == item.id }
                val locked = isLocked(item)
                HedefitCard(Modifier.fillMaxWidth().alpha(if (locked) .55f else 1f), onClick = { if (locked) onLocked(item) else selected = item }, contentPadding = PaddingValues(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ExerciseMedia(item.imageUrls.firstOrNull(), item.name, Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, maxLines = 2)
                            Text(
                                listOfNotNull(item.primaryMuscles.joinToString().ifBlank { null }, item.secondaryMuscles.take(2).joinToString().ifBlank { null }).joinToString(" • "),
                                color = HedefitColors.TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1,
                            )
                            Text(listOf(item.level, item.equipment.ifBlank { if (en) "No equipment" else "Ekipmansız" }).filter(String::isNotBlank).joinToString(" • "), color = HedefitColors.TextMuted, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                        }
                        IconButton(
                            onClick = { if (locked) onLocked(item) else selectedForProgram = if (isPicked) selectedForProgram.filterNot { it.id == item.id } else selectedForProgram + item },
                            modifier = Modifier.size(40.dp).background(if (isPicked) HedefitColors.Lime else HedefitColors.SurfaceHigh, CircleShape),
                        ) {
                            Icon(
                                if (locked) Icons.Default.Lock else if (isPicked) Icons.Default.Check else Icons.Default.Add,
                                if (isPicked) (if (en) "Remove ${item.name} from selection" else "${item.name} seçimden çıkar") else (if (en) "Select ${item.name} for a new program" else "${item.name} yeni program için seç"),
                                tint = if (isPicked) HedefitColors.OnLime else HedefitColors.TextPrimary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
        if (selectedForProgram.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().background(HedefitColors.Surface).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                IconButton(onClick = { selectedForProgram = emptyList() }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Close, if (en) "Clear selection" else "Seçimi temizle", tint = HedefitColors.TextMuted)
                }
                Text(
                    if (en) "${selectedForProgram.size} selected" else "${selectedForProgram.size} hareket seçildi",
                    modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = { showNameDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = HedefitColors.Lime, contentColor = HedefitColors.OnLime)) {
                    Text(if (en) "Create program" else "Program oluştur")
                }
            }
        }
    } }
    if (showFilters) ModalBottomSheet(onDismissRequest = { showFilters = false }, containerColor = HedefitColors.Surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 18.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (en) "Narrow results" else "Sonuçları daralt", style = MaterialTheme.typography.titleLarge)
                }
                if (activeFilterCount > 0) TextButton(onClick = clearFilters) { Text(if (en) "Reset" else "Sıfırla") }
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 560.dp), contentPadding = PaddingValues(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { FilterSection(if (en) "Place" else "Ortam", environmentOptions(en), environment) { environment = it } }
                item { FilterSection(if (en) "Target muscle" else "Hedef kas", muscleOptions(en), muscle) { muscle = it; muscleRole = if (it.isBlank()) "" else "primary" } }
                if (muscle.isNotBlank()) item { FilterSection(if (en) "Muscle role" else "Kasın rolü", muscleRoleOptions(en), muscleRole) { muscleRole = it } }
                item { FilterSection(if (en) "Training type" else "Antrenman türü", categoryOptions(en), category) { category = it } }
                item { HorizontalDivider(color = HedefitColors.Divider) }
                item { Text(if (en) "More precise" else "Daha seçici", color = HedefitColors.Lime, style = MaterialTheme.typography.labelLarge) }
                item { FilterSection(if (en) "Level" else "Seviye", levelOptions(en), level) { level = it } }
                item { FilterSection(if (en) "Equipment" else "Ekipman", equipmentOptions(en), equipment) { equipment = it } }
                item { FilterSection(if (en) "Movement" else "Hareket yönü", forceOptions(en), force) { force = it } }
                item { FilterSection(if (en) "Structure" else "Yapı", mechanicOptions(en), mechanic) { mechanic = it } }
            }
            Button(
                onClick = { onSearch(query, muscle, equipment, level, environment, muscleRole, force, mechanic, category); showFilters = false },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = HedefitColors.Lime, contentColor = HedefitColors.OnLime),
            ) { Text(if (en) "Show matching movements" else "Uygun hareketleri göster") }
        }
    }
    selected?.let { exercise ->
        AlertDialog(onDismissRequest = { selected = null }, title = { Text(exercise.name) }, text = { LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { ExerciseMotionPlayer(exercise.imageUrls, exercise.name, Modifier.fillMaxWidth().height(230.dp).clip(RoundedCornerShape(18.dp))) }
            item { MuscleConnections(exercise, en) }
            item { Text(if (en) "How to perform" else "Nasıl yapılır?", style = MaterialTheme.typography.titleMedium) }
            items(exercise.instructions.size) { index -> Text("${index + 1}. ${exercise.instructions[index]}") }
        } }, dismissButton = { TextButton(onClick = { onUse(exercise); selected = null }) { Text(if (en) "Add to program" else "Programa ekle") } }, confirmButton = { Button(onClick = { onStart(exercise); selected = null }, colors = ButtonDefaults.buttonColors(containerColor = HedefitColors.Lime, contentColor = HedefitColors.OnLime)) { Text(if (en) "Train now" else "Hemen çalış") } })
    }
    if (showCustom) CustomExerciseDialog(onDismiss = { showCustom = false }) { exercise -> onUse(exercise); showCustom = false }
    if (showNameDialog) {
        var programName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text(if (en) "Name your program" else "Programına isim ver") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (en) "${selectedForProgram.size} movements: ${selectedForProgram.joinToString { it.name }}" else "${selectedForProgram.size} hareket: ${selectedForProgram.joinToString { it.name }}",
                    color = HedefitColors.TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 3,
                )
                OutlinedTextField(programName, { programName = it }, label = { Text(if (en) "Program name" else "Program adı") }, singleLine = true)
            } },
            dismissButton = { TextButton(onClick = { showNameDialog = false }) { Text(if (en) "Cancel" else com.hedefit.app.ui.i18n.tr("Vazgeç", "Cancel")) } },
            confirmButton = {
                Button(
                    enabled = programName.trim().length >= 2,
                    onClick = {
                        onCreateProgram(programName.trim(), selectedForProgram)
                        showNameDialog = false
                        selectedForProgram = emptyList()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = HedefitColors.Lime, contentColor = HedefitColors.OnLime),
                ) { Text(if (en) "Create" else "Oluştur") }
            },
        )
    }
}

private fun environmentOptions(en: Boolean) = listOf(
    "" to if (en) "Any" else "Tümü",
    "gym" to if (en) "Gym" else "Spor salonu",
    "home" to if (en) "Home" else "Ev",
)

private fun muscleRoleOptions(en: Boolean) = listOf(
    "primary" to if (en) "Main target" else "Ana hedef",
    "secondary" to if (en) "Supporting" else "Yardımcı",
    "" to if (en) "Either" else "Her ikisi",
)

private fun categoryOptions(en: Boolean) = listOf(
    "" to if (en) "All" else "Tümü",
    "strength" to if (en) "Strength" else "Kuvvet",
    "stretching" to if (en) "Mobility" else "Mobilite",
    "plyometrics" to if (en) "Explosive" else "Patlayıcı",
    "cardio" to if (en) "Cardio" else "Kardiyo",
)

private fun levelOptions(en: Boolean) = listOf(
    "" to if (en) "All" else "Tümü",
    "beginner" to if (en) "Easy" else "Kolay",
    "intermediate" to if (en) "Medium" else "Orta",
    "expert" to if (en) "Advanced" else "İleri",
)

// Bu anahtarlar sunucudaki EQUIPMENT_GROUPS eşlemesiyle (lib/exercise-service.ts)
// birebir uyuşmalı; RepDB'nin ~55 ham ekipman etiketi (scripts/import-repdb.mjs)
// oradan bu küçük gruplara indirgeniyor.
private fun equipmentOptions(en: Boolean) = listOf(
    "" to if (en) "All" else "Tümü",
    "bodyweight" to if (en) "No equipment" else "Ekipmansız",
) + HOME_EQUIPMENT_OPTIONS.map { (key, labels) -> key to if (en) labels.second else labels.first } + listOf(
    "cable" to if (en) "Cable" else "Kablo",
    "machine" to if (en) "Machine" else "Makine",
)

private fun forceOptions(en: Boolean) = listOf(
    "" to if (en) "All" else "Tümü",
    "push" to if (en) "Push" else "İtiş",
    "pull" to if (en) "Pull" else "Çekiş",
    "static" to if (en) "Static" else "Statik",
)

private fun mechanicOptions(en: Boolean) = listOf(
    "" to if (en) "All" else "Tümü",
    "compound" to if (en) "Compound" else "Bileşik",
    "isolation" to if (en) "Isolation" else "İzolasyon",
)

private fun muscleOptions(en: Boolean) = if (en) listOf(
    "" to "All", "arms" to "Arms · all", "back" to "Back · all", "legs" to "Legs · all", "core" to "Core · all", "hips" to "Hips · all", "chest" to "Chest", "lats" to "Lats", "middle back" to "Mid back", "lower back" to "Lower back", "traps" to "Traps", "neck" to "Neck",
    "shoulders" to "Shoulders", "biceps" to "Front arm · biceps", "triceps" to "Back arm · triceps", "forearms" to "Forearm & wrist", "abdominals" to "Abs",
    "glutes" to "Glutes", "quadriceps" to "Front thigh · quads", "hamstrings" to "Back thigh · hamstrings", "calves" to "Calves", "adductors" to "Inner thigh · adductors", "abductors" to "Outer hip · abductors",
) else listOf(
    "" to "Tümü", "chest" to "Göğüs", "back" to "Sırt", "lats" to "Kanat", "traps" to "Trapez", "neck" to "Boyun", "shoulders" to "Omuz", "biceps" to "Ön kol", "triceps" to "Arka kol", "forearms" to "Bilek", "abdominals" to "Karın", "legs" to "Bacak", "glutes" to "Kalça", "calves" to "Baldır", "abductors" to "Dış kalça",
)

@Composable
private fun MuscleConnections(exercise: ExerciseCatalogData, en: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(if (en) "Muscles involved" else "Çalışan kas grupları", style = MaterialTheme.typography.titleMedium)
        Text(
            (if (en) "Main target · " else "Ana hedef · ") + exercise.primaryMuscles.joinToString(),
            color = HedefitColors.Lime,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            if (exercise.secondaryMuscles.isEmpty()) {
                if (en) "Supporting muscles · No additional group listed" else "Yardımcı kaslar · Ek grup belirtilmemiş"
            } else {
                (if (en) "Supporting muscles · " else "Yardımcı kaslar · ") + exercise.secondaryMuscles.joinToString()
            },
            color = HedefitColors.TextSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(exercise.equipment.ifBlank { if (en) "No equipment" else "Ekipmansız" }, color = HedefitColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun FilterSection(title: String, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, color = HedefitColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
        HfChipRow { options.forEach { (value, label) -> HfChip(label, selected == value, { onSelect(value) }) } }
    }
}

@Composable
private fun CustomExerciseDialog(onDismiss: () -> Unit, onCreate: (ExerciseCatalogData) -> Unit) {
    var name by remember { mutableStateOf("") }
    var muscle by remember { mutableStateOf("") }
    var equipment by remember { mutableStateOf("") }
    var instruction by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(com.hedefit.app.ui.i18n.tr("Özel hareket", "Custom exercise")) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text(com.hedefit.app.ui.i18n.tr("Hareket adı", "Exercise name")) }, singleLine = true)
            OutlinedTextField(muscle, { muscle = it }, label = { Text("Kas grubu") }, singleLine = true)
            OutlinedTextField(equipment, { equipment = it }, label = { Text("Ekipman") }, singleLine = true)
            OutlinedTextField(instruction, { instruction = it }, label = { Text("Uygulama notu") })
        } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(com.hedefit.app.ui.i18n.tr("Vazgeç", "Cancel")) } },
        confirmButton = { Button(enabled = name.trim().length >= 2, onClick = { onCreate(ExerciseCatalogData("custom-${UUID.randomUUID()}", name.trim(), "custom", equipment.trim(), listOf(muscle.trim().ifBlank { "Tüm Vücut" }), listOf(instruction.trim()).filter(String::isNotBlank), "custom", emptyList())) }, colors = ButtonDefaults.buttonColors(containerColor = HedefitColors.Lime, contentColor = HedefitColors.OnLime)) { Text("Programa ekle") } },
    )
}
