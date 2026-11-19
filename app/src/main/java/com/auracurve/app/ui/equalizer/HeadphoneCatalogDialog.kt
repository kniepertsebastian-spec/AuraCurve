package com.auracurve.app.ui.equalizer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.auracurve.app.R
import com.auracurve.app.autoeq.AutoEqCatalogEntry
import com.auracurve.app.autoeq.HeadphoneForm
import kotlinx.coroutines.delay

private const val SEARCH_DEBOUNCE_MS = 150L

/**
 * Picks a headphone from the bundled AutoEq catalogue by name: for wired headphones (Android reports
 * no product name for them), for a Bluetooth name the app could not match, or to correct a
 * suggestion. Picking one applies its correction and remembers it for the current output.
 */
@Composable
internal fun HeadphoneCatalogDialog(
    initialQuery: String,
    onSearch: suspend (String) -> List<AutoEqCatalogEntry>,
    onChosen: (AutoEqCatalogEntry) -> Unit,
    onDismiss: () -> Unit,
    style: EqualizerDesignStyle,
) {
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var results by remember { mutableStateOf<List<AutoEqCatalogEntry>>(emptyList()) }
    LaunchedEffect(query) {
        delay(SEARCH_DEBOUNCE_MS)
        results = if (query.isBlank()) emptyList() else onSearch(query)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.headphone_catalog_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.headphone_catalog_hint)) },
                    modifier = Modifier.fillMaxWidth().testTag("headphone_catalog_query"),
                )
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp).padding(top = 8.dp)) {
                    items(results, key = { it.id }) { entry ->
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .clickable(role = Role.Button) { onChosen(entry) }
                                    .padding(vertical = 8.dp),
                        ) {
                            Text(text = entry.displayName, style = MaterialTheme.typography.bodyLarge, color = style.text)
                            Text(
                                text = listOfNotNull(entry.form?.let { formLabel(it) }, entry.source).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = style.mutedText,
                            )
                        }
                    }
                }
                if (query.isNotBlank() && results.isEmpty()) {
                    Text(
                        text = stringResource(R.string.headphone_catalog_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = style.mutedText,
                    )
                }
                // AutoEq is MIT licensed; the licence text ships with the data (autoeq/LICENSE.txt).
                Text(
                    text = stringResource(R.string.headphone_catalog_attribution),
                    style = MaterialTheme.typography.labelSmall,
                    color = style.mutedText,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun formLabel(form: HeadphoneForm): String =
    when (form) {
        HeadphoneForm.OVER_EAR -> stringResource(R.string.headphone_form_over_ear)
        HeadphoneForm.IN_EAR -> stringResource(R.string.headphone_form_in_ear)
        HeadphoneForm.EARBUD -> stringResource(R.string.headphone_form_earbud)
    }
