package com.hedefit.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hedefit.app.ui.theme.HedefitColors

@Composable
fun PersonalDetailsOnboardingDialog(
    initialAge: Int?,
    initialGender: String?,
    initialHeightCm: Double?,
    initialWeightKg: Double?,
    isSaving: Boolean,
    onSave: (age: Int, gender: String, heightCm: Double, weightKg: Double) -> Unit,
    language: String = "tr",
) {
    val en = language == "en"
    var ageText by remember { mutableStateOf(initialAge?.toString() ?: "") }
    var gender by remember { mutableStateOf(initialGender?.ifBlank { "Erkek" } ?: "Erkek") }
    var heightText by remember { mutableStateOf(initialHeightCm?.toInt()?.toString() ?: "") }
    var weightText by remember { mutableStateOf(initialWeightKg?.let { "%.1f".format(it).replace(',', '.') } ?: "") }
    var errorText by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = { /* Non-dismissable until filled */ },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = HedefitColors.Surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, HedefitColors.Lime.copy(alpha = 0.3f)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Header
                Text(
                    text = if (en) "Complete Your Profile" else "Profilini Tamamla",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = HedefitColors.TextPrimary,
                )
                Text(
                    text = if (en) {
                        "To accurately calculate your personal calorie, water, and workout plans, we need a few details."
                    } else {
                        "Sana özel kalori, su ve antrenman hedeflerini doğru hesaplayabilmemiz için temel bilgilerini girmen gerekiyor."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = HedefitColors.TextSecondary,
                    lineHeight = 18.sp,
                )

                HorizontalDivider(color = HedefitColors.Divider, thickness = 0.5.dp)

                // Age / Birth year
                OutlinedTextField(
                    value = ageText,
                    onValueChange = {
                        ageText = it.filter(Char::isDigit).take(3)
                        errorText = null
                    },
                    label = { Text(if (en) "Age (e.g. 25)" else "Yaş (Örn: 25)") },
                    leadingIcon = { Icon(Icons.Default.Cake, null, tint = HedefitColors.Lime) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                )

                // Gender selector
                Text(
                    text = if (en) "Gender" else "Cinsiyet",
                    style = MaterialTheme.typography.labelMedium,
                    color = HedefitColors.TextSecondary,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        "Erkek" to if (en) "Male" else "Erkek",
                        "Kadın" to if (en) "Female" else "Kadın",
                        "Diğer" to if (en) "Other" else "Diğer",
                    ).forEach { (code, label) ->
                        val isSelected = gender.equals(code, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) HedefitColors.Lime.copy(alpha = 0.2f) else HedefitColors.SurfaceHigh)
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) HedefitColors.Lime else Color.Transparent,
                                    shape = RoundedCornerShape(10.dp),
                                )
                                .clickable { gender = code }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) HedefitColors.Lime else HedefitColors.TextPrimary,
                            )
                        }
                    }
                }

                // Height (cm)
                OutlinedTextField(
                    value = heightText,
                    onValueChange = {
                        heightText = it.filter(Char::isDigit).take(3)
                        errorText = null
                    },
                    label = { Text(if (en) "Height (cm, e.g. 178)" else "Boy (cm, Örn: 178)") },
                    leadingIcon = { Icon(Icons.Default.Height, null, tint = HedefitColors.Lime) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                )

                // Weight (kg)
                OutlinedTextField(
                    value = weightText,
                    onValueChange = {
                        weightText = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(5)
                        errorText = null
                    },
                    label = { Text(if (en) "Weight (kg, e.g. 74.5)" else "Kilo (kg, Örn: 74.5)") },
                    leadingIcon = { Icon(Icons.Default.MonitorWeight, null, tint = HedefitColors.Lime) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                )

                errorText?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Spacer(Modifier.height(4.dp))

                Button(
                    onClick = {
                        val age = ageText.toIntOrNull()
                        val height = heightText.toDoubleOrNull()
                        val weight = weightText.replace(',', '.').toDoubleOrNull()

                        when {
                            age == null || age !in 13..100 -> {
                                errorText = if (en) "Please enter a valid age (13-100)." else "Lütfen geçerli bir yaş girin (13–100)."
                            }
                            height == null || height !in 100.0..250.0 -> {
                                errorText = if (en) "Please enter a valid height in cm (100-250)." else "Lütfen geçerli bir boy girin (100–250 cm)."
                            }
                            weight == null || weight !in 30.0..300.0 -> {
                                errorText = if (en) "Please enter a valid weight in kg (30-300)." else "Lütfen geçerli bir kilo girin (30–300 kg)."
                            }
                            else -> {
                                errorText = null
                                onSave(age, gender, height, weight)
                            }
                        }
                    },
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HedefitColors.Lime,
                        contentColor = HedefitColors.OnLime,
                    ),
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            color = HedefitColors.OnLime,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(
                            text = if (en) "Save and Continue" else "Kaydet ve Başla",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                        )
                    }
                }
            }
        }
    }
}
