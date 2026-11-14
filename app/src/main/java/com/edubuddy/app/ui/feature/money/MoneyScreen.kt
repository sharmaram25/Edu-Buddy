package com.edubuddy.app.ui.feature.money

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.edubuddy.app.AppContainer
import com.edubuddy.app.core.Fmt
import com.edubuddy.app.core.Money
import com.edubuddy.app.core.TimeMath
import com.edubuddy.app.data.db.TransactionEntity
import com.edubuddy.app.data.db.TxnType
import com.edubuddy.app.data.model.Categories
import com.edubuddy.app.ui.common.BarChart
import com.edubuddy.app.ui.common.CategoryVisuals
import com.edubuddy.app.ui.common.DonutChart
import com.edubuddy.app.ui.common.EduCard
import com.edubuddy.app.ui.common.EduTextField
import com.edubuddy.app.ui.common.EduViewModel
import com.edubuddy.app.ui.common.EmptyState
import com.edubuddy.app.ui.common.Gutter
import com.edubuddy.app.ui.common.HeroCard
import com.edubuddy.app.ui.common.IconBadge
import com.edubuddy.app.ui.common.LocalMessenger
import com.edubuddy.app.ui.common.PageHeader
import com.edubuddy.app.ui.common.SectionHeader
import com.edubuddy.app.ui.common.Slice
import com.edubuddy.app.ui.common.ThinProgress
import com.edubuddy.app.ui.common.eduViewModel
import com.edubuddy.app.ui.navigation.TxnEditRoute
import com.edubuddy.app.ui.theme.EduTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth

data class CategorySum(val key: String, val paise: Long, val share: Float)

data class MoneyState(
    val month: YearMonth = YearMonth.now(),
    val txns: List<TransactionEntity> = emptyList(),
    val spent: Long = 0,
    val income: Long = 0,
    val budget: Long = 0,
    val byCategory: List<CategorySum> = emptyList(),
    val daily: List<Long> = emptyList(),
    val loading: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
class MoneyViewModel(c: AppContainer) : EduViewModel(c) {
    val month = MutableStateFlow(YearMonth.from(c.clock.today()))

    val state = month.flatMapLatest { ym ->
        combine(c.money.monthTransactions(ym), c.money.effectiveBudget(ym)) { txns, budget ->
            val expenses = txns.filter { it.type == TxnType.EXPENSE }
            val spent = expenses.sumOf { it.amountPaise }
            val cats = expenses.groupBy { it.category }
                .map { (k, v) -> k to v.sumOf { it.amountPaise } }
                .sortedByDescending { it.second }
                .map { (k, p) -> CategorySum(k, p, if (spent > 0) p.toFloat() / spent else 0f) }
            val daily = LongArray(ym.lengthOfMonth())
            expenses.forEach { daily[LocalDate.ofEpochDay(it.epochDay).dayOfMonth - 1] += it.amountPaise }
            MoneyState(
                month = ym, txns = txns, spent = spent,
                income = txns.filter { it.type == TxnType.INCOME }.sumOf { it.amountPaise },
                budget = budget?.amountPaise ?: 0L, byCategory = cats, daily = daily.toList(), loading = false,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MoneyState())

    fun shift(months: Long) { month.value = month.value.plusMonths(months) }
    fun setBudget(paise: Long) = write { c.money.setBudget(month.value, paise) }
    fun delete(t: TransactionEntity) = write { c.money.delete(t) }
    fun restore(t: TransactionEntity) = write { c.money.restore(t) }
}

@Composable
fun MoneyScreen(nav: NavHostController) {
    val vm = eduViewModel { MoneyViewModel(it) }
    val st by vm.state.collectAsStateWithLifecycle()
    var editBudget by remember { mutableStateOf(false) }
    var categoryFilter by rememberSaveable { mutableStateOf<String?>(null) }
    val thisMonth = YearMonth.now()
    val today = LocalDate.now()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
            item { Spacer(Modifier.windowInsetsPadding(WindowInsets.statusBars)) }
            item {
                PageHeader("Money", subtitle = Fmt.month(st.month)) {
                    IconButton(onClick = { vm.shift(-1); categoryFilter = null }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Previous month") }
                    IconButton(onClick = { vm.shift(1); categoryFilter = null }, enabled = st.month < thisMonth) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Next month")
                    }
                }
            }
            item { Summary(st, isCurrent = st.month == thisMonth, today = today, onEditBudget = { editBudget = true }) }

            if (st.byCategory.isNotEmpty()) {
                item { SectionHeader("Where it went") }
                item {
                    EduCard(Modifier.fillMaxWidth().padding(horizontal = Gutter)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DonutChart(
                                slices = st.byCategory.map { Slice(it.paise.toFloat(), CategoryVisuals.of(it.key).color) },
                                size = 128.dp, thickness = 12.dp,
                                description = "Spending by category",
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(Money.formatCompact(st.spent), style = MaterialTheme.typography.titleMedium)
                                    Text("spent", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                st.byCategory.take(6).forEach { cs ->
                                    val v = CategoryVisuals.of(cs.key)
                                    val selected = categoryFilter == cs.key
                                    Row(
                                        Modifier.fillMaxWidth().clickable { categoryFilter = if (selected) null else cs.key }.padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        com.edubuddy.app.ui.common.ColorDot(v.color, 10.dp)
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            Categories.of(cs.key).label, style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text("${(cs.share * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                if (st.byCategory.size > 6) Text("+${st.byCategory.size - 6} more", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                item { SectionHeader("Daily spending") }
                item {
                    EduCard(Modifier.fillMaxWidth().padding(horizontal = Gutter)) {
                        val avg = st.spent / (if (st.month == thisMonth) today.dayOfMonth else st.month.lengthOfMonth()).coerceAtLeast(1)
                        Text("Avg ${Money.formatWhole(avg)} a day", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        BarChart(
                            values = st.daily,
                            highlight = if (st.month == thisMonth) today.dayOfMonth - 1 else -1,
                            color = MaterialTheme.colorScheme.primary,
                            description = "Daily spending for ${Fmt.month(st.month)}",
                        )
                    }
                }
            }

            item {
                SectionHeader("History")
                if (categoryFilter != null) {
                    InputChip(
                        selected = true, onClick = { categoryFilter = null },
                        label = { Text(Categories.of(categoryFilter!!).label) },
                        trailingIcon = { Icon(Icons.Rounded.Close, "Clear filter") },
                        modifier = Modifier.padding(horizontal = Gutter),
                    )
                }
            }
            val shown = st.txns.filter { categoryFilter == null || it.category == categoryFilter }
            if (!st.loading && shown.isEmpty()) {
                item {
                    EmptyState(
                        Icons.AutoMirrored.Rounded.ReceiptLong, "No entries for ${Fmt.month(st.month)}",
                        "Log what you spend and receive. EduBuddy shows where your money goes and how much you can spend per day.",
                        actionLabel = "Add expense", onAction = { nav.navigate(TxnEditRoute()) },
                    )
                }
            }
            val byDay = shown.groupBy { it.epochDay }.toSortedMap(compareByDescending { it })
            byDay.forEach { (day, list) ->
                item(key = "d$day") {
                    val date = LocalDate.ofEpochDay(day)
                    val dayExpense = list.filter { it.type == TxnType.EXPENSE }.sumOf { it.amountPaise }
                    Row(Modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, top = 16.dp, bottom = 6.dp)) {
                        Text(Fmt.relativeDay(date, today).uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        if (dayExpense > 0) Text(Money.format(dayExpense), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                item(key = "l$day") {
                    EduCard(Modifier.fillMaxWidth().padding(horizontal = Gutter), contentPadding = PaddingValues(vertical = 4.dp)) {
                        list.forEachIndexed { i, t ->
                            if (i > 0) com.edubuddy.app.ui.common.Hairline(start = 66.dp)
                            TxnRow(t, onClick = { nav.navigate(TxnEditRoute(id = t.id)) })
                        }
                    }
                }
            }
        }

        com.edubuddy.app.ui.common.EduFab(
            onClick = { nav.navigate(TxnEditRoute()) },
            icon = Icons.Rounded.Add,
            text = "Add entry",
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    if (editBudget) BudgetDialog(st.budget, st.month, onSave = { vm.setBudget(it) }, onDismiss = { editBudget = false })
}

@Composable
private fun Summary(st: MoneyState, isCurrent: Boolean, today: LocalDate, onEditBudget: () -> Unit) {
    val ex = EduTheme.extra
    HeroCard(Modifier.fillMaxWidth().padding(horizontal = Gutter)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("SPENT THIS MONTH", style = MaterialTheme.typography.labelSmall, color = ex.onHeroMuted, modifier = Modifier.weight(1f))
            androidx.compose.material3.OutlinedButton(
                onClick = onEditBudget,
                border = androidx.compose.foundation.BorderStroke(1.dp, ex.heroTrack),
                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = ex.onHero),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                modifier = Modifier.height(32.dp),
            ) {
                Icon(Icons.Rounded.Edit, null, Modifier.padding(end = 6.dp).height(14.dp))
                Text(if (st.budget > 0) "Budget" else "Set budget", style = MaterialTheme.typography.labelMedium)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(Money.format(st.spent), style = MaterialTheme.typography.displaySmall, color = ex.onHero)
            if (st.budget > 0) {
                Spacer(Modifier.width(8.dp))
                Text("/ ${Money.formatCompact(st.budget)}", style = MaterialTheme.typography.titleMedium, color = ex.onHeroMuted, modifier = Modifier.padding(bottom = 5.dp))
            }
        }
        if (st.budget > 0) {
            val ratio = st.spent.toFloat() / st.budget
            Spacer(Modifier.height(16.dp))
            ThinProgress(ratio, if (ratio > 1f) ex.danger else if (ratio > 0.85f) ex.warning else ex.onHero, height = 4.dp, trackColor = ex.heroTrack)
        }
        Spacer(Modifier.height(16.dp))
        Row {
            val remaining = st.budget - st.spent
            when {
                st.budget <= 0 -> HeroStat("Received", Money.formatCompact(st.income), Modifier.weight(1f))
                remaining < 0 -> HeroStat("Over by", Money.formatCompact(-remaining), Modifier.weight(1f), ex.danger)
                else -> HeroStat("Left", Money.formatCompact(remaining), Modifier.weight(1f))
            }
            if (st.budget > 0 && remaining > 0 && isCurrent) {
                val daysLeft = TimeMath.daysLeftInMonth(today)
                HeroStat("Per day · ${daysLeft}d left", Money.formatWhole(remaining / daysLeft), Modifier.weight(1f))
            }
            HeroStat("Net", Money.format(st.income - st.spent, showSign = true), Modifier.weight(1f))
        }
    }
}

@Composable
private fun HeroStat(label: String, value: String, modifier: Modifier = Modifier, color: Color = EduTheme.extra.onHero) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = EduTheme.extra.onHeroMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, style = MaterialTheme.typography.titleMedium, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun TxnRow(t: TransactionEntity, onClick: () -> Unit) {
    val v = CategoryVisuals.of(t.category)
    val ex = EduTheme.extra
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(v.icon, v.color)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(Categories.of(t.category).label, t.note.takeIf { it.isNotBlank() }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            if (t.type == TxnType.INCOME) Money.format(t.amountPaise, showSign = true) else Money.format(t.amountPaise),
            style = MaterialTheme.typography.titleSmall,
            color = if (t.type == TxnType.INCOME) ex.income else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun BudgetDialog(current: Long, month: YearMonth, onSave: (Long) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(if (current > 0) Money.toInput(current) else "") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Monthly budget") },
        text = {
            Column {
                Text(
                    "Applies to ${Fmt.month(month)} and following months until you change it.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                EduTextField(text, { text = it; error = null }, "Amount", prefix = "₹ ", keyboardType = KeyboardType.Decimal, error = error, maxLength = 12)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (text.isBlank()) { onSave(0); onDismiss(); return@TextButton }
                when (val r = Money.parse(text)) {
                    is Money.ParseResult.Ok -> { onSave(r.paise); onDismiss() }
                    is Money.ParseResult.Error -> error = r.message
                }
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (current > 0) TextButton(onClick = { onSave(0); onDismiss() }) { Text("Remove") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
