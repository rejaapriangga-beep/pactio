package com.keluargakendali.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.keluargakendali.R

/**
 * Satu langkah tur coach-mark - `key` HARUS cocok dengan salah satu Modifier.tutorialTarget di
 * layar yang sama supaya sorotannya menempel di elemen yang benar. `description` sudah teks
 * jadi (hasil stringResource di lokasi pemanggilan) - bukan resId, supaya daftar langkah bisa
 * disusun langsung dari @Composable pemanggil tanpa Context tambahan.
 */
data class TutorialStep(val key: String, val description: String)

/**
 * Status tur coach-mark yang dibagi antara layar yang menandai target lewat
 * Modifier.tutorialTarget dan CoachMarkOverlay yang menggambar sorotan+tooltip-nya. Murni state
 * UI SEMENTARA (posisi target di layar saat ini) - tidak disimpan/disinkronkan ke mana pun.
 * "Sudah pernah lihat tur ini belum" itu hal terpisah, disimpan lewat SettingsStore
 * (lihat pemakaiannya di ParentScreen.kt).
 */
class TutorialCoachMarkState {
    var steps by mutableStateOf<List<TutorialStep>>(emptyList())
        private set
    var currentIndex by mutableStateOf(0)
        private set
    var visible by mutableStateOf(false)
        private set
    private val targetBounds = mutableStateMapOf<String, Rect>()

    fun start(steps: List<TutorialStep>) {
        if (steps.isEmpty()) return
        this.steps = steps
        currentIndex = 0
        visible = true
    }

    fun next() {
        if (currentIndex < steps.lastIndex) currentIndex++ else finish()
    }

    fun finish() {
        visible = false
    }

    fun reportBounds(key: String, rect: Rect) {
        targetBounds[key] = rect
    }

    val currentStep: TutorialStep? get() = steps.getOrNull(currentIndex)
    val currentBounds: Rect? get() = currentStep?.let { targetBounds[it.key] }
}

/** Menandai satu elemen sebagai target langkah tur - taruh di composable yang mau disorot. */
fun Modifier.tutorialTarget(key: String, state: TutorialCoachMarkState): Modifier =
    this.onGloballyPositioned { coordinates -> state.reportBounds(key, coordinates.boundsInRoot()) }

/**
 * Overlay penuh layar (dipasang SEKALI di root komposisi, sejajar dengan Scaffold - lihat
 * MainActivity - BUKAN di dalam AlertDialog/Dialog, supaya berbagi ruang koordinat yang sama
 * dengan elemen yang mau disorot dan "melubangi" scrim persis di posisinya). Kalau target langkah
 * ini belum ketemu posisinya (mis. tab yang jadi target sedang tidak aktif ditampilkan), tooltip
 * tetap muncul di tengah layar tanpa lubang sorotan - degradasi wajar, bukan macet.
 */
@Composable
fun CoachMarkOverlay(state: TutorialCoachMarkState) {
    if (!state.visible) return
    val step = state.currentStep ?: return
    val bounds = state.currentBounds
    val stepNumber = state.currentIndex + 1
    val totalSteps = state.steps.size
    val isLastStep = state.currentIndex == state.steps.lastIndex

    val density = LocalDensity.current

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val holePaddingPx = with(density) { 8.dp.toPx() }
        val maxHeightPx = with(density) { maxHeight.toPx() }
        val maxWidthPx = with(density) { maxWidth.toPx() }

        // Scrim gelap dengan "lubang" transparan di posisi target - graphicsLayer offscreen
        // WAJIB supaya BlendMode.Clear benar-benar melubangi (bukan menggambar hitam solid di
        // atas transparan, yang hasilnya tetap gelap).
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawRect(color = Color.Black.copy(alpha = 0.72f))
                    if (bounds != null) {
                        drawRoundRect(
                            color = Color.Transparent,
                            topLeft = Offset(
                                (bounds.left - holePaddingPx).coerceAtLeast(0f),
                                (bounds.top - holePaddingPx).coerceAtLeast(0f)
                            ),
                            size = Size(bounds.width + holePaddingPx * 2, bounds.height + holePaddingPx * 2),
                            cornerRadius = CornerRadius(24f, 24f),
                            blendMode = BlendMode.Clear
                        )
                    }
                }
        )

        // Tooltip: kalau target ketemu, taruh di bawahnya (atau di atas kalau tidak muat ke
        // bawah); kalau target tidak ketemu (null), tampil di tengah layar saja.
        val tooltipMaxWidthPx = with(density) { 320.dp.toPx() }
        val tooltipWidthDp = with(density) { (maxWidthPx - 64f).coerceAtMost(tooltipMaxWidthPx).toDp() }
        val tooltipModifier = if (bounds == null) {
            Modifier.align(Alignment.Center).padding(horizontal = 32.dp)
        } else {
            val estimatedTooltipHeightPx = with(density) { 180.dp.toPx() }
            val spaceBelow = maxHeightPx - (bounds.bottom + holePaddingPx)
            val placeBelow = spaceBelow > estimatedTooltipHeightPx
            val yPx = if (placeBelow) bounds.bottom + holePaddingPx + with(density) { 12.dp.toPx() }
            else (bounds.top - holePaddingPx - with(density) { 12.dp.toPx() } - estimatedTooltipHeightPx).coerceAtLeast(with(density) { 16.dp.toPx() })
            val targetCenterX = bounds.left + bounds.width / 2f
            val tooltipWidthPx = with(density) { tooltipWidthDp.toPx() }
            val xPx = (targetCenterX - tooltipWidthPx / 2f).coerceIn(
                with(density) { 16.dp.toPx() },
                (maxWidthPx - tooltipWidthPx - with(density) { 16.dp.toPx() }).coerceAtLeast(with(density) { 16.dp.toPx() })
            )
            Modifier.offset(x = with(density) { xPx.toDp() }, y = with(density) { yPx.toDp() })
        }

        Card(
            modifier = tooltipModifier.width(tooltipWidthDp),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.tutorial_step_counter, stepNumber, totalSteps),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(step.description, style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { state.finish() }) { Text(stringResource(R.string.action_skip_tutorial)) }
                    Button(
                        onClick = { if (isLastStep) state.finish() else state.next() },
                        colors = ButtonDefaults.buttonColors()
                    ) {
                        Text(stringResource(if (isLastStep) R.string.action_finish_tutorial else R.string.action_next_tutorial))
                    }
                }
            }
        }
    }
}
