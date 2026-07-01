package com.raised.app.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.raised.core.WorkoutType

/**
 * PLACEHOLDER Session screen — issue #5 owns the real session timer UI/ViewModel.
 *
 * The Home Start button navigates to `session/{type}`, which renders this. The
 * `type` arg is all #5 needs to rebuild the [com.raised.core.WorkoutPlan] via
 * [com.raised.core.buildPlan] over the current stored config (same lookup Home
 * uses), so #5 should:
 *
 *  1. Replace this composable's body with the real session screen.
 *  2. Add a `SessionViewModel` (@HiltViewModel) that resolves the current
 *     config of [type] from [com.raised.app.data.PresetRepository], builds the
 *     plan, and drives [com.raised.core.SessionEngine] ticks (foreground-only,
 *     decision D6).
 *  3. Keep the `session/{type}` route definition in [com.raised.app.nav.RaisedApp].
 *
 * Route: `session/{type}` where `type` ∈ {`HIIT`, `RAISED`}.
 */
@Composable
fun SessionPlaceholder(type: WorkoutType) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Session — coming in #5",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Text(
            "WorkoutType = $type",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
