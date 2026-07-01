package com.raised.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raised.core.WorkoutType

/**
 * Home screen. Lists the two workouts (HIIT, Raised) as cards, each showing the
 * current config summary and the live total duration (from the plan builder).
 * Tapping a card opens Config; the Start button navigates to the Session route.
 *
 * No business logic here — every value comes from [HomeViewModel]'s StateFlow.
 */
@Composable
fun HomeScreen(
    onOpenConfig: (WorkoutType) -> Unit,
    onStart: (WorkoutType) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (state.loading) {
        Loading()
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Raised",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "Pick a workout to start, or tap a card to configure.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        state.hiit?.let { WorkoutCard(summary = it, onOpenConfig = { onOpenConfig(WorkoutType.HIIT) }, onStart = { onStart(WorkoutType.HIIT) }) }
        state.raised?.let { WorkoutCard(summary = it, onOpenConfig = { onOpenConfig(WorkoutType.RAISED) }, onStart = { onStart(WorkoutType.RAISED) }) }
    }
}

@Composable
private fun WorkoutCard(
    summary: WorkoutSummary,
    onOpenConfig: () -> Unit,
    onStart: () -> Unit,
) {
    val accent = if (summary.type == WorkoutType.HIIT) {
        MaterialTheme.colorScheme.primary // flame
    } else {
        MaterialTheme.colorScheme.secondary // mint
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenConfig),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()) {
                Text(summary.name, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    formatDuration(summary.totalDurationSec),
                    style = MaterialTheme.typography.titleLarge,
                    color = accent,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(configSummaryLine(summary), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = MaterialTheme.colorScheme.onPrimary),
            ) {
                Text("Start")
            }
        }
    }
}

/** One-line human summary of the config, e.g. "8 exercises · 3 rounds · 45s / 15s". */
private fun configSummaryLine(s: WorkoutSummary): String {
    val count = if (s.type == WorkoutType.HIIT) {
        "${s.config.rounds} rounds"
    } else {
        "${s.config.sets} sets"
    }
    return "${s.config.exercises.size} exercises · $count · ${s.config.exerciseSecs}s work / ${s.config.breakSecs}s break"
}

/** mm:ss or mm:ss if over an hour. */
internal fun formatDuration(totalSec: Int): String {
    if (totalSec <= 0) return "--:--"
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@Composable
private fun Loading() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}
