package com.isotjs.todosian.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.isotjs.todosian.R
import com.isotjs.todosian.utils.MonthDay
import com.isotjs.todosian.utils.RepeatRule
import com.isotjs.todosian.utils.RepeatUnit
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.WeekFields

private const val ON_COMPLETION_DELETE = "delete"
private const val ON_COMPLETION_KEEP = "keep"

/**
 * The task editor's recurrence field. Shows the rule and opens the Repeat sheet; rules the sheet
 * can't express (or that the user chose to type) are edited as free text instead.
 */
@Composable
fun RepeatField(
    recurrence: String?,
    onCompletion: String?,
    label: String,
    onChange: (recurrence: String?, onCompletion: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSheet by remember { mutableStateOf(false) }
    var typing by remember { mutableStateOf(!recurrence.isNullOrBlank() && RepeatRule.parse(recurrence) == null) }
    val supporting: (@Composable () -> Unit)? = if (onCompletion.equals(ON_COMPLETION_DELETE, ignoreCase = true)) {
        { Text(text = stringResource(R.string.repeat_deleted_when_done)) }
    } else {
        null
    }

    if (typing) {
        OutlinedTextField(
            value = recurrence.orEmpty(),
            onValueChange = { onChange(it.ifBlank { null }, onCompletion) },
            singleLine = true,
            label = { Text(text = label) },
            supportingText = supporting,
            trailingIcon = {
                IconButton(onClick = { showSheet = true }) {
                    Icon(
                        imageVector = Icons.Outlined.Tune,
                        contentDescription = stringResource(R.string.cd_repeat_open_picker),
                    )
                }
            },
            modifier = modifier.fillMaxWidth(),
        )
    } else {
        val choose = stringResource(R.string.cd_repeat_open_picker)
        Box(modifier = modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = recurrence?.replaceFirstChar { it.uppercase() } ?: stringResource(R.string.repeat_never),
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                label = { Text(text = label) },
                supportingText = supporting,
                trailingIcon = { Icon(imageVector = Icons.Filled.ArrowDropDown, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
            )
            // A read-only text field swallows taps; this layer opens the sheet instead.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable(role = Role.Button, onClickLabel = choose) { showSheet = true },
            )
        }
    }

    if (showSheet) {
        RepeatSheet(
            recurrence = recurrence,
            onCompletion = onCompletion,
            onDone = { newRecurrence, newOnCompletion ->
                onChange(newRecurrence, newOnCompletion)
                typing = false
                showSheet = false
            },
            onTypeRule = {
                typing = true
                showSheet = false
            },
            onDismiss = { showSheet = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepeatSheet(
    recurrence: String?,
    onCompletion: String?,
    onDone: (recurrence: String?, onCompletion: String?) -> Unit,
    onTypeRule: () -> Unit,
    onDismiss: () -> Unit,
) {
    val initial = remember { RepeatRule.parse(recurrence) }
    var unit by remember { mutableStateOf(initial?.unit) }
    var intervalText by remember { mutableStateOf((initial?.interval ?: 1).toString()) }
    var weekdays by remember { mutableStateOf(initial?.weekdays.orEmpty()) }
    var monthDay by remember { mutableStateOf(initial?.monthDay ?: MonthDay.Same) }
    var dayOfMonthText by remember {
        mutableStateOf(((initial?.monthDay as? MonthDay.Nth)?.day ?: LocalDate.now().dayOfMonth).toString())
    }
    var whenDone by remember { mutableStateOf(initial?.whenDone ?: false) }
    var deleteWhenDone by remember { mutableStateOf(onCompletion.equals(ON_COMPLETION_DELETE, ignoreCase = true)) }

    val interval = intervalText.toIntOrNull()?.coerceAtLeast(1) ?: 1
    val rule = unit?.let { selected ->
        RepeatRule(
            unit = selected,
            interval = interval,
            weekdays = if (selected == RepeatUnit.WEEK) weekdays else emptySet(),
            monthDay = when {
                selected != RepeatUnit.MONTH || interval != 1 -> MonthDay.Same
                monthDay is MonthDay.Nth -> MonthDay.Nth(dayOfMonthText.toIntOrNull()?.coerceIn(1, 31) ?: 1)
                else -> monthDay
            },
            whenDone = whenDone,
        )
    }
    // An explicit "keep" is the default; leave it in the file unless the user turns on delete.
    val newOnCompletion = when {
        deleteWhenDone -> ON_COMPLETION_DELETE
        onCompletion.equals(ON_COMPLETION_KEEP, ignoreCase = true) -> onCompletion
        else -> null
    }

    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.ime)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp),
        ) {
            Column {
                Text(text = stringResource(R.string.repeat_title), style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = rule?.let { stringResource(R.string.repeat_preview, "🔁 ${it.toText()}") }
                        ?: stringResource(R.string.repeat_preview_never),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val units = listOf(null) + RepeatUnit.entries
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                units.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = unit == option,
                        onClick = { unit = option },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = units.size),
                        icon = {},
                        label = { Text(text = unitLabel(option), maxLines = 1) },
                    )
                }
            }

            val selectedUnit = unit
            if (selectedUnit != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(text = stringResource(R.string.repeat_every))
                    OutlinedTextField(
                        value = intervalText,
                        onValueChange = { intervalText = it.filter(Char::isDigit).take(3) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(80.dp),
                    )
                    Text(text = unitNoun(selectedUnit, interval))
                }

                if (selectedUnit == RepeatUnit.WEEK) {
                    WeekdayPicker(selected = weekdays, onChange = { weekdays = it })
                }

                // The recurrence engine only supports a fixed day for "every month".
                if (selectedUnit == RepeatUnit.MONTH && interval == 1) {
                    val options = listOf(MonthDay.Same, MonthDay.Nth(1), MonthDay.Last)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        options.forEachIndexed { index, option ->
                            SegmentedButton(
                                selected = monthDay::class == option::class,
                                onClick = { monthDay = option },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                                icon = {},
                                label = {
                                    Text(
                                        text = when (option) {
                                            MonthDay.Same -> stringResource(R.string.repeat_month_same)
                                            is MonthDay.Nth -> stringResource(R.string.repeat_month_nth)
                                            MonthDay.Last -> stringResource(R.string.repeat_month_last)
                                        },
                                        maxLines = 1,
                                    )
                                },
                            )
                        }
                    }
                    if (monthDay is MonthDay.Nth) {
                        OutlinedTextField(
                            value = dayOfMonthText,
                            onValueChange = { dayOfMonthText = it.filter(Char::isDigit).take(2) },
                            singleLine = true,
                            label = { Text(text = stringResource(R.string.repeat_day_of_month)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(160.dp),
                        )
                    }
                }

                SwitchRow(
                    title = stringResource(R.string.repeat_when_done),
                    subtitle = stringResource(R.string.repeat_when_done_hint),
                    checked = whenDone,
                    onCheckedChange = { whenDone = it },
                )
            }

            SwitchRow(
                title = stringResource(R.string.repeat_delete),
                subtitle = stringResource(R.string.repeat_delete_hint),
                checked = deleteWhenDone,
                onCheckedChange = { deleteWhenDone = it },
            )

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextButton(onClick = onTypeRule) { Text(text = stringResource(R.string.repeat_type_rule)) }
                TextButton(onClick = { onDone(rule?.toText(), newOnCompletion) }) {
                    Text(text = stringResource(R.string.repeat_done))
                }
            }
        }
    }
}

@Composable
private fun WeekdayPicker(
    selected: Set<DayOfWeek>,
    onChange: (Set<DayOfWeek>) -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val firstDay = remember(locale) { WeekFields.of(locale).firstDayOfWeek }
    val days = remember(firstDay) { (0L until 7L).map { firstDay.plus(it) } }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        days.forEach { day ->
            val isSelected = day in selected
            val fullName = day.getDisplayName(TextStyle.FULL, locale)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = CircleShape,
                    )
                    .toggleable(
                        value = isSelected,
                        role = Role.Checkbox,
                        onValueChange = { on -> onChange(if (on) selected + day else selected - day) },
                    )
                    .semantics { contentDescription = fullName },
            ) {
                Text(
                    text = day.getDisplayName(TextStyle.NARROW, locale),
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun unitLabel(unit: RepeatUnit?): String = when (unit) {
    null -> stringResource(R.string.repeat_never)
    RepeatUnit.DAY -> stringResource(R.string.repeat_unit_day)
    RepeatUnit.WEEK -> stringResource(R.string.repeat_unit_week)
    RepeatUnit.MONTH -> stringResource(R.string.repeat_unit_month)
    RepeatUnit.YEAR -> stringResource(R.string.repeat_unit_year)
}

@Composable
private fun unitNoun(unit: RepeatUnit, count: Int): String = when (unit) {
    RepeatUnit.DAY -> pluralStringResource(R.plurals.repeat_days, count)
    RepeatUnit.WEEK -> pluralStringResource(R.plurals.repeat_weeks, count)
    RepeatUnit.MONTH -> pluralStringResource(R.plurals.repeat_months, count)
    RepeatUnit.YEAR -> pluralStringResource(R.plurals.repeat_years, count)
}
