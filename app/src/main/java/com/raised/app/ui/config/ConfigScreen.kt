package com.raised.app.ui.config

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raised.core.StepType
import com.raised.core.WorkoutType
import com.raised.app.ui.home.formatDuration

/**
 * Config screen. Edits the working config for the selected [WorkoutType]: every
 * duration, the rounds/sets count, and the exercise list (add/remove/reorder/
 * rename). The live total-duration estimate recomputes on every edit (rebuilt
 * from the plan builder in the ViewModel). Save/load round-trips through the
 * Room layer via [ConfigViewModel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    onBack: () -> Unit,
    viewModel: ConfigViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showSaveDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configure ${screenTitle(state.type)}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TotalCard(totalSec = state.totalDurationSec, stepCount = state.stepCount, preview = state.preview)

            DurationsSection(state = state, viewModel = viewModel)
            ExercisesSection(state = state, viewModel = viewModel)
            PresetsSection(
                type = state.type,
                presets = state.presets,
                onSaveClick = { showSaveDialog = true },
                onLoad = { viewModel.loadPreset(it) },
            )
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showSaveDialog) {
        SavePresetDialog(
            defaultName = screenTitle(state.type),
            onDismiss = { showSaveDialog = false },
            onConfirm = { name ->
                viewModel.saveAsPreset(name)
                showSaveDialog = false
            },
        )
    }
}

private fun screenTitle(type: WorkoutType) = when (type) {
    WorkoutType.HIIT -> "HIIT"
    WorkoutType.RAISED -> "Raised"
}

@Composable
private fun TotalCard(
    totalSec: Int,
    stepCount: Int,
    preview: List<com.raised.core.Step>,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Total duration", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                formatDuration(totalSec),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text("$stepCount steps", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DurationsSection(state: ConfigUiState, viewModel: ConfigViewModel) {
    Section(title = "Timing") {
        DurationField(label = "Work (sec)", value = state.exerciseSecs, onChange = viewModel::setExerciseSecs)
        DurationField(label = "Break (sec)", value = state.breakSecs, onChange = viewModel::setBreakSecs)
        DurationField(label = "Long break (sec)", value = state.longBreakSecs, onChange = viewModel::setLongBreakSecs)
        DurationField(label = "Warm-up (sec)", value = state.warmupSecs, onChange = viewModel::setWarmupSecs)
        DurationField(label = "Get ready (sec)", value = state.getReadySecs, onChange = viewModel::setGetReadySecs)
        DurationField(label = "Challenge (sec)", value = state.challengeSecs, onChange = viewModel::setChallengeSecs)
        DurationField(label = "Cool-down (sec)", value = state.cooldownSecs, onChange = viewModel::setCooldownSecs)
        if (state.type == WorkoutType.HIIT) {
            DurationField(label = "Rounds", value = state.rounds, onChange = viewModel::setRounds)
        } else {
            DurationField(label = "Sets", value = state.sets, onChange = viewModel::setSets)
        }
    }
}

@Composable
private fun DurationField(label: String, value: Int, onChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            text = input.filter { it.isDigit() }
            onChange(text.toIntOrNull() ?: 0)
        },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ExercisesSection(state: ConfigUiState, viewModel: ConfigViewModel) {
    Section(title = "Exercises (${state.exercises.size})") {
        state.exercises.forEachIndexed { index, name ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { viewModel.setExerciseName(index, it) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { viewModel.moveExercise(index, index - 1) }, enabled = index > 0) {
                    Icon(Icons.Filled.ArrowUpward, contentDescription = "Move up")
                }
                IconButton(
                    onClick = { viewModel.moveExercise(index, index + 1) },
                    enabled = index < state.exercises.lastIndex,
                ) {
                    Icon(Icons.Filled.ArrowDownward, contentDescription = "Move down")
                }
                IconButton(onClick = { viewModel.removeExercise(index) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remove")
                }
            }
        }
        OutlinedButton(onClick = { viewModel.addExercise() }, modifier = Modifier.fillMaxWidth()) {
            Text("+ Add exercise")
        }
    }
}

@Composable
private fun PresetsSection(
    type: WorkoutType,
    presets: List<com.raised.app.data.Preset>,
    onSaveClick: () -> Unit,
    onLoad: (Long) -> Unit,
) {
    val oftype = presets.filter { it.config.type == type }
    Section(title = "Presets") {
        Button(onClick = onSaveClick, modifier = Modifier.fillMaxWidth()) {
            Text("Save as preset")
        }
        if (oftype.isEmpty()) {
            Text("No saved presets yet.", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            oftype.forEach { preset ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(preset.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            formatDuration(runCatching {
                                com.raised.core.buildPlan(preset.config).totalDurationSec
                            }.getOrDefault(0)) + " · ${preset.config.exercises.size} exercises",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { onLoad(preset.id) }) { Text("Load") }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
        content()
    }
}

@Composable
private fun SavePresetDialog(
    defaultName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(defaultName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save preset") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Preset name") },
                singleLine = true,
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(name.ifBlank { defaultName }) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
