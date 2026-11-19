package com.auracurve.app.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.auracurve.app.ui.equalizer.SonicStyle
import com.auracurve.app.ui.theme.PillShape
import com.auracurve.app.ui.theme.SonicPaper
import com.auracurve.app.ui.theme.SonicSteel
import com.auracurve.app.ui.theme.spacing

// A text field in the app's look: a pill on the raised surface, no outline, an optional symbol
// at the start and a control at the end.
@Composable
internal fun PillTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailing: (@Composable () -> Unit)? = null,
) {
    val style = SonicStyle
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        singleLine = true,
        shape = PillShape,
        leadingIcon = leadingIcon?.let { icon -> { Icon(icon, contentDescription = null, tint = style.mutedText) } },
        trailingIcon = trailing?.let { content -> { Box(modifier = Modifier.padding(end = 6.dp)) { content() } } },
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        colors =
            TextFieldDefaults.colors(
                focusedContainerColor = style.surface,
                unfocusedContainerColor = style.surface,
                disabledContainerColor = style.surface,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                focusedTextColor = style.text,
                unfocusedTextColor = style.text,
                cursorColor = style.accent,
                focusedPlaceholderColor = style.mutedText,
                unfocusedPlaceholderColor = style.mutedText,
            ),
    )
}

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
