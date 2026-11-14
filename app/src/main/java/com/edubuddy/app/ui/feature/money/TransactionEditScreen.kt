package com.edubuddy.app.ui.feature.money

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.edubuddy.app.AppContainer
import com.edubuddy.app.core.Fmt
import com.edubuddy.app.core.Money
import com.edubuddy.app.data.db.TransactionEntity
import com.edubuddy.app.data.db.TxnType
import com.edubuddy.app.data.model.Categories
import com.edubuddy.app.ui.common.CategoryVisuals
import com.edubuddy.app.ui.common.ConfirmDialog
import com.edubuddy.app.ui.common.EduDatePicker
import com.edubuddy.app.ui.common.EduTextField
import com.edubuddy.app.ui.common.EduViewModel
import com.edubuddy.app.ui.common.EditorScaffold
import com.edubuddy.app.ui.common.FormSection
import com.edubuddy.app.ui.common.LocalMessenger
import com.edubuddy.app.ui.common.Segmented
import com.edubuddy.app.ui.common.eduViewModel
import com.edubuddy.app.ui.navigation.TxnEditRoute
import com.edubuddy.app.ui.theme.EduTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

class TxnEditViewModel(c: AppContainer, private val route: TxnEditRoute) : EduViewModel(c) {
    val isNew = route.id == 0L
    var loaded by mutableStateOf(isNew)
    var type by mutableStateOf(if (route.income) TxnType.INCOME else TxnType.EXPENSE)
    var amount by mutableStateOf("")
    var title by mutableStateOf("")
    var category by mutableStateOf(if (route.income) "pocket_money" else "food")
    var date by mutableStateOf(c.clock.today())
    var note by mutableStateOf("")
    var amountError by mutableStateOf<String?>(null)
    var suggestions by mutableStateOf<List<String>>(emptyList())

    private var original: TransactionEntity? = null
    private var baseline = ""
    private var suggestJob: Job? = null

    init {
        viewModelScope.launch {
            if (!isNew) {
                c.money.get(route.id)?.let { t ->
                    original = t
                    type = t.type; amount = Money.toInput(t.amountPaise); title = t.title
                    category = t.category; date = LocalDate.ofEpochDay(t.epochDay); note = t.note
                }
                loaded = true
            }
            baseline = fp()
        }
    }

    private fun fp() = listOf(type, amount, title, category, date, note).toString()
    val dirty get() = loaded && fp() != baseline

    fun chooseType(t: TxnType) {
        if (t == type) return
        type = t
        category = Categories.forType(t).first().key
    }

    fun onTitle(v: String) {
        title = v
        suggestJob?.cancel()
        suggestJob = viewModelScope.launch {
            delay(150)
            suggestions = c.money.suggestions(type, v).filter { !it.equals(v.trim(), ignoreCase = true) }
        }
    }

    fun save(): Boolean {
        val parsed = Money.parse(amount)
        if (parsed is Money.ParseResult.Error) { amountError = parsed.message; return false }
        val paise = (parsed as Money.ParseResult.Ok).paise
        val name = title.trim().ifBlank { Categories.of(category).label }
        val t = (original ?: TransactionEntity(title = "", amountPaise = 0, category = category, epochDay = 0)).copy(
            title = name, amountPaise = paise, type = type, category = category, epochDay = date.toEpochDay(), note = note.trim(),
        )
        write { c.money.save(t) }
        return true
    }

    fun delete(onDeleted: (TransactionEntity) -> Unit) {
        val o = original ?: return
        write { c.money.delete(o) }
        onDeleted(o)
    }

    fun restore(t: TransactionEntity) = write { c.money.restore(t) }
}

@Composable
fun TransactionEditScreen(route: TxnEditRoute, onBack: () -> Unit) {
    val vm = eduViewModel(key = "txn-${route.id}-${route.income}") { TxnEditViewModel(it, route) }
    val messenger = LocalMessenger.current
    var pickDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val amountFocus = remember { FocusRequester() }
    val today = LocalDate.now()
    val ex = EduTheme.extra

    LaunchedEffect(Unit) { if (vm.isNew) runCatching { amountFocus.requestFocus() } }

    EditorScaffold(
        title = if (vm.isNew) "New entry" else "Edit entry",
        onBack = onBack,
        onSave = { if (vm.save()) { messenger.show(if (vm.type == TxnType.EXPENSE) "Expense saved" else "Income saved"); onBack() } },
        dirty = vm.dirty,
        onDelete = if (vm.isNew) null else ({ confirmDelete = true }),
    ) {
        Segmented(listOf(TxnType.EXPENSE, TxnType.INCOME), vm.type, { if (it == TxnType.EXPENSE) "Expense" else "Income" }, vm::chooseType)

        // Big amount entry.
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("₹", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                BasicTextField(
                    value = vm.amount,
                    onValueChange = { v ->
                        // Digits, one dot, two decimals, sensible length.
                        val filtered = v.filter { it.isDigit() || it == '.' }
                        if (filtered.count { it == '.' } <= 1 && filtered.substringAfter('.', "").length <= 2 && filtered.length <= 10) {
                            vm.amount = filtered; vm.amountError = null
                        }
                    },
                    textStyle = MaterialTheme.typography.displayMedium.copy(
                        color = if (vm.type == TxnType.INCOME) ex.income else MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Start,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.width(androidx.compose.foundation.layout.IntrinsicSize.Min).focusRequester(amountFocus),
                    decorationBox = { inner ->
                        if (vm.amount.isEmpty()) Text("0", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.outlineVariant)
                        inner()
                    },
                )
            }
            if (vm.amountError != null) Text(vm.amountError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        FormSection("Category") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), maxItemsInEachRow = 3) {
                Categories.forType(vm.type).forEach { cat ->
                    val v = CategoryVisuals.of(cat.key)
                    val sel = vm.category == cat.key
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(MaterialTheme.shapes.medium)
                            .background(if (sel) v.color.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerLow)
                            .then(if (sel) Modifier.border(2.dp, v.color, MaterialTheme.shapes.medium) else Modifier)
                            .clickable { vm.category = cat.key }
                            .padding(vertical = 12.dp, horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(v.icon, null, tint = v.color, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.height(4.dp))
                        Text(cat.label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    }
                }
            }
        }

        Column {
            EduTextField(
                vm.title, vm::onTitle, if (vm.type == TxnType.EXPENSE) "What for?" else "From",
                placeholder = Categories.of(vm.category).label, capitalization = KeyboardCapitalization.Sentences, maxLength = 60,
            )
            if (vm.suggestions.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    vm.suggestions.forEach { s -> SuggestionChip(onClick = { vm.title = s; vm.suggestions = emptyList() }, label = { Text(s) }) }
                }
            }
        }

        FormSection("Date") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = vm.date == today, onClick = { vm.date = today }, label = { Text("Today") })
                FilterChip(selected = vm.date == today.minusDays(1), onClick = { vm.date = today.minusDays(1) }, label = { Text("Yesterday") })
                val custom = vm.date != today && vm.date != today.minusDays(1)
                FilterChip(
                    selected = custom, onClick = { pickDate = true },
                    label = { Text(if (custom) Fmt.pickerDate(vm.date, today) else "Pick date") },
                    leadingIcon = { Icon(Icons.Rounded.Event, null, Modifier.size(18.dp)) },
                )
            }
        }

        OutlinedTextField(
            value = vm.note, onValueChange = { if (it.length <= 300) vm.note = it },
            label = { Text("Note (optional)") }, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth(),
        )
    }

    if (pickDate) EduDatePicker(vm.date, { vm.date = it }, { pickDate = false })
    if (confirmDelete) {
        ConfirmDialog(
            "Delete entry?", "${vm.title.ifBlank { "This entry" }} · ₹${vm.amount}", "Delete", destructive = true,
            onConfirm = { vm.delete { t -> messenger.undo("Entry deleted") { vm.restore(t) } }; onBack() },
            onDismiss = { confirmDelete = false },
        )
    }
}
