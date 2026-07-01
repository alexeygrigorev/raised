package com.raised.app.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raised.core.StepType

/**
 * The running-workout screen. Drives the pure timer engine through
 * [SessionViewModel]: a big countdown, the current step + phase colour, a
 * "next up" preview, round/overall progress, play/pause/skip controls, and a
 * finish summary.
 *
 * Foreground-only (D6): [keepScreenOn] turns on `FLAG_KEEP_SCREEN_ON` while the
 * screen is composed, and the per-second ticker is started/cancelled from a
 * [DisposableEffect] tied to the composition — backgrounding the app removes the
 * composition, which cancels the ticker, so no time advances in the background.
 */
@Composable
fun SessionScreen(
    onExit: () -> Unit,
    viewModel: SessionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Keep the screen awake while a session is foregrounded (D6).
    KeepScreenOn()

    // Start/stop the ticker with the composition: leaves-composition ⇒ cancelled,
    // so the timer never runs in the background.
    DisposableEffect(viewModel) {
        viewModel.startTicker()
        onDispose { viewModel.stopTicker() }
    }

    if (state.loading) {
        Loading()
        return
    }

    val phaseColor = phaseBackground(state.step?.phase ?: StepType.WARMUP)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(phaseColor)
            .padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (state.isFinished) {
            FinishSummary(
                state = state,
                onExit = onExit,
                onRestart = { viewModel.restart() },
            )
        } else {
            RunningContent(
                state = state,
                onToggleRunning = viewModel::toggleRunning,
                onSkipForward = viewModel::skipForward,
                onSkipBack = viewModel::skipBack,
                onRestart = viewModel::restart,
            )
        }
    }
}

@Composable
private fun RunningContent(
    state: SessionUiState,
    onToggleRunning: () -> Unit,
    onSkipForward: () -> Unit,
    onSkipBack: () -> Unit,
    onRestart: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = (state.step?.label ?: "").uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            letterSpacing = 2.sp,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = formatCountdown(state.remainingSec),
            fontSize = 96.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        state.step?.nextLabel?.let { next ->
            Text(
                text = "Next up: $next",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.85f),
            )
        }
    }

    val progress = state.progress
    if (progress != null) {
        Text(
            text = "Step ${progress.stepIndex + 1} / ${progress.stepCount}  ·  " +
                "${formatCountdown(progress.totalElapsedSec)} / ${formatCountdown(progress.totalDurationSec)}",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.85f),
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ControlButton(Icons.Default.Refresh, "Restart", onClick = onRestart)
        ControlButton(Icons.Default.SkipPrevious, "Previous", onClick = onSkipBack)
        PlayPauseButton(running = state.running, onClick = onToggleRunning)
        ControlButton(Icons.Default.SkipNext, "Skip", onClick = onSkipForward)
    }
}

@Composable
private fun PlayPauseButton(running: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(72.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor = MaterialTheme.colorScheme.background,
        ),
    ) {
        Icon(
            imageVector = if (running) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (running) "Pause" else "Play",
            modifier = Modifier.size(36.dp),
        )
    }
}

@Composable
private fun ControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(56.dp).clip(CircleShape)
            .background(Color.White.copy(alpha = 0.15f)),
        colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun FinishSummary(
    state: SessionUiState,
    onExit: () -> Unit,
    onRestart: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = "Done!",
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
        Text(
            text = "Total time: ${formatCountdown(state.totalElapsedSecOnFinish)}",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
        )
        Text(
            text = "${state.completedStepsOnFinish} steps completed",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.85f),
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onRestart,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = MaterialTheme.colorScheme.background,
            ),
        ) { Text("Restart") }
        Button(
            onClick = onExit,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = Color.White,
            ),
        ) { Text("Done") }
    }
}

@Composable
private fun Loading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

/**
 * Turns on `FLAG_KEEP_SCREEN_ON` for the host window while composed (D6 — the
 * screen stays on for the duration of a foregrounded session). Reverted
 * automatically on dispose because the flag is tied to the window; clearing it
 * on dispose restores default screen behaviour.
 */
@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? android.app.Activity)?.window
        if (window != null) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

/** Phase → background colour (work = flame, rest = mint, warm-up/cool-down = ember). */
private fun phaseBackground(phase: StepType): Color = when (phase) {
    StepType.EXERCISE, StepType.CHALLENGE -> Flame
    StepType.BREAK, StepType.LONG_BREAK -> Mint
    StepType.WARMUP -> Ember
    StepType.GET_READY -> Ember
    StepType.COOLDOWN -> MintDark
    StepType.DONE -> Slate
}

private val Flame = Color(0xFFFF5A36)
private val Ember = Color(0xFFFF8A3D)
private val Mint = Color(0xFF2EC4B6)
private val MintDark = Color(0xFF1E8A82)
private val Slate = Color(0xFF0E1116)

/** mm:ss (or h:mm:ss past an hour) for a seconds count. */
private fun formatCountdown(totalSec: Int): String {
    if (totalSec <= 0) return "0:00"
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
