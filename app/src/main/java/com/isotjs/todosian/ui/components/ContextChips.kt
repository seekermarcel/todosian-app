package com.isotjs.todosian.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.isotjs.todosian.R
import com.isotjs.todosian.data.FileRepository
import com.isotjs.todosian.utils.ContextTags

/** Contexts tagged anywhere in the folder, loaded once per [prefix]; empty while loading. */
@Composable
fun rememberVaultContexts(fileRepository: FileRepository, prefix: String): List<String> {
    val contexts by produceState(initialValue = emptyList<String>(), fileRepository, prefix) {
        value = fileRepository.getContexts(prefix)
    }
    return contexts
}

/** "All" plus one chip per context; selecting the active chip again clears the filter. */
@Composable
fun ContextFilterRow(
    contexts: List<String>,
    active: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
    ) {
        FilterChip(
            selected = active == null,
            onClick = { onSelect(null) },
            label = { Text(text = stringResource(R.string.context_all)) },
        )
        contexts.forEach { context ->
            val selected = context.equals(active, ignoreCase = true)
            FilterChip(
                selected = selected,
                onClick = { onSelect(if (selected) null else context) },
                label = { Text(text = ContextTags.displayName(context)) },
                leadingIcon = if (selected) {
                    { Icon(imageVector = Icons.Filled.Check, contentDescription = null) }
                } else {
                    null
                },
            )
        }
    }
}

/** Toggle chips for the known contexts plus a field to add a new one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ContextPicker(
    selected: List<String>,
    known: List<String>,
    onChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var newContext by remember { mutableStateOf("") }
    val options = (known + selected).distinctBy { it.lowercase() }
    fun isSelected(context: String) = selected.any { it.equals(context, ignoreCase = true) }
    fun add() {
        // Tags can't hold spaces; "Am Telefon" becomes "am-telefon".
        val cleaned = newContext.trim().lowercase().replace(Regex("""\s+"""), "-").trim('#', '/')
        if (cleaned.isNotEmpty() && !isSelected(cleaned)) onChange(selected + cleaned)
        newContext = ""
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(R.string.context_picker_title),
            style = MaterialTheme.typography.bodyMedium,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { context ->
                val on = isSelected(context)
                FilterChip(
                    selected = on,
                    onClick = {
                        onChange(if (on) selected.filterNot { it.equals(context, ignoreCase = true) } else selected + context)
                    },
                    label = { Text(text = ContextTags.displayName(context)) },
                    leadingIcon = if (on) {
                        { Icon(imageVector = Icons.Filled.Check, contentDescription = null) }
                    } else {
                        null
                    },
                )
            }
        }
        OutlinedTextField(
            value = newContext,
            onValueChange = { newContext = it },
            singleLine = true,
            label = { Text(text = stringResource(R.string.context_new)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { add() }),
            trailingIcon = {
                IconButton(onClick = { add() }, enabled = newContext.isNotBlank()) {
                    Icon(imageVector = Icons.Filled.Add, contentDescription = stringResource(R.string.cd_context_add))
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
