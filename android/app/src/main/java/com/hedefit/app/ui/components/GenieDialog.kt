package com.hedefit.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

/**
 * `androidx.compose.material3.AlertDialog` ile aynı imza — dosyalarda yalnız
 * import satırı değiştirilerek (material3.AlertDialog yerine bu) takılır.
 *
 * Material3'ün kendi `AlertDialog`'u pencereyi Compose'un dahili
 * `FloatingDialogWindowTheme` stiliyle açar; bu stil Activity temasındaki
 * `android:windowAnimationStyle`'ı (bkz. styles.xml'deki "cin efekti" XML
 * animasyonları) miras ALMAZ — o yüzden native yaklaşım hiç çalışmıyordu.
 * Burada aynı efekt tamamen Compose içinde, `scaleIn/scaleOut` +
 * `MutableTransitionState` ile üretiliyor: kapanışta gerçek
 * `onDismissRequest` çağrısı çıkış animasyonu bitene kadar ertelenir, aksi
 * halde pencere animasyon oynamadan aniden kaybolurdu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = AlertDialogDefaults.containerColor,
    iconContentColor: Color = AlertDialogDefaults.iconContentColor,
    titleContentColor: Color = AlertDialogDefaults.titleContentColor,
    textContentColor: Color = AlertDialogDefaults.textContentColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties(),
) {
    val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }

    BasicAlertDialog(
        onDismissRequest = { visibleState.targetState = false },
        modifier = modifier,
        properties = properties,
    ) {
        AnimatedVisibility(
            visibleState = visibleState,
            enter = fadeIn(tween(180)) + scaleIn(
                initialScale = 0.72f,
                transformOrigin = TransformOrigin(0.5f, 0.82f),
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
            ),
            exit = fadeOut(tween(140)) + scaleOut(
                targetScale = 0.72f,
                transformOrigin = TransformOrigin(0.5f, 0.82f),
                animationSpec = tween(140),
            ),
        ) {
            Surface(shape = shape, color = containerColor, tonalElevation = tonalElevation) {
                Column(Modifier.padding(24.dp)) {
                    icon?.let {
                        Box(Modifier.fillMaxWidth().padding(bottom = 16.dp), contentAlignment = Alignment.Center) {
                            CompositionLocalProvider(LocalContentColor provides iconContentColor, content = it)
                        }
                    }
                    title?.let {
                        CompositionLocalProvider(
                            LocalContentColor provides titleContentColor,
                            LocalTextStyle provides MaterialTheme.typography.headlineSmall,
                            content = it,
                        )
                        Spacer(Modifier.height(16.dp))
                    }
                    text?.let {
                        CompositionLocalProvider(
                            LocalContentColor provides textContentColor,
                            LocalTextStyle provides MaterialTheme.typography.bodyMedium,
                            content = it,
                        )
                        Spacer(Modifier.height(24.dp))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        dismissButton?.let { it(); Spacer(Modifier.width(8.dp)) }
                        confirmButton()
                    }
                }
            }
        }
    }

    LaunchedEffect(visibleState.currentState, visibleState.isIdle) {
        if (visibleState.isIdle && !visibleState.currentState) onDismissRequest()
    }
}
