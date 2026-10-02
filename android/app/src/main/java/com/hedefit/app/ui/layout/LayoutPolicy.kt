package com.hedefit.app.ui.layout

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

object LayoutPolicy {
    const val ExpandedNavigationWidthDp = 700
    const val TabletSmallestWidthDp = 600
    const val CompactHorizontalPaddingDp = 12
    const val RegularHorizontalPaddingDp = 18

    fun usesExpandedNavigation(widthDp: Int): Boolean = widthDp >= ExpandedNavigationWidthDp
    fun horizontalPadding(widthDp: Int): Int = if (widthDp < 360) CompactHorizontalPaddingDp else RegularHorizontalPaddingDp

    /**
     * Katlanan telefonlar açıkken tablet genişliğine ulaşabilir ama yanında GPS/hareket
     * donanımıyla dışarıda kullanılır; bu yüzden menteşe sensörü olan cihazlar tablet sayılmaz.
     */
    fun isTablet(smallestWidthDp: Int, hasHingeSensor: Boolean): Boolean =
        smallestWidthDp >= TabletSmallestWidthDp && !hasHingeSensor

    /** Tablet yerleşiminde yatay ekran yan yana, dikey ekran üst üste bölünür. */
    fun standSideBySide(widthDp: Int, heightDp: Int): Boolean = widthDp > heightDp
}

fun detectTablet(context: Context): Boolean = LayoutPolicy.isTablet(
    smallestWidthDp = context.resources.configuration.smallestScreenWidthDp,
    hasHingeSensor = context.packageManager.hasSystemFeature("android.hardware.sensor.hinge_angle"),
)

@Composable
fun rememberIsTablet(): Boolean {
    val context = LocalContext.current
    return remember(context) { detectTablet(context) }
}
