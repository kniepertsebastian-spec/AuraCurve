package com.auracurve.app.ui.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.auracurve.app.ui.theme.SonicPaper
import com.auracurve.app.ui.theme.SonicSteel
import com.auracurve.app.ui.theme.spacing

// An empty area with what is missing and one next action.
@Composable
fun EmptyState(
    title: String,
    text: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = MaterialTheme.spacing.medium),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, color = SonicPaper)
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.extraSmall))
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = SonicSteel)
        TextButton(onClick = onAction) { Text(actionLabel) }
    }
}
