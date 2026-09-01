package com.shivam.expensenotebook.ui

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shivam.expensenotebook.ExpenseUiState
import com.shivam.expensenotebook.ExpenseViewModel
import com.shivam.expensenotebook.data.CURRENCY_CODE
import com.shivam.expensenotebook.data.Category
import com.shivam.expensenotebook.data.Expense
import com.shivam.expensenotebook.data.MonthlySettings
import com.shivam.expensenotebook.ui.theme.LocalFinanceColors
import com.shivam.expensenotebook.ui.theme.categoryColor
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency
import java.util.Locale
import kotlin.math.max

private const val SCREEN_HOME = "home"
private const val SCREEN_HISTORY = "history"
private const val SCREEN_STATS = "statistics"
private const val SCREEN_BUDGET = "budget"
private const val SCREEN_CATEGORIES = "categories"
private const val SCREEN_ADD = "add"

@Composable
fun ExpenseNotebookApp(viewModel: ExpenseViewModel) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var screen by rememberSaveable { mutableStateOf(SCREEN_HOME) }
    var returnScreen by rememberSaveable { mutableStateOf(SCREEN_HOME) }
    var editingExpenseId by rememberSaveable { mutableStateOf<Long?>(null) }

    LaunchedEffect(state.error) {
        state.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    BackHandler(enabled = screen != SCREEN_HOME) {
        screen = if (screen == SCREEN_ADD) returnScreen else SCREEN_HOME
    }

    if (state.isLoading) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        return
    }

    fun openAddExpense(from: String) {
        editingExpenseId = null
        returnScreen = from
        screen = SCREEN_ADD
    }

    val mainScreens = setOf(SCREEN_HOME, SCREEN_HISTORY, SCREEN_STATS, SCREEN_BUDGET)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppHeader(
                title = when (screen) {
                    SCREEN_HOME -> "Overview"
                    SCREEN_HISTORY -> "Transactions"
                    SCREEN_STATS -> "Statistics"
                    SCREEN_BUDGET -> "Monthly plan"
                    SCREEN_CATEGORIES -> "Categories"
                    SCREEN_ADD -> if (editingExpenseId == null) "Add expense" else "Edit expense"
                    else -> "Expense Notebook"
                },
                showBack = screen !in mainScreens,
                onBack = { screen = if (screen == SCREEN_ADD) returnScreen else SCREEN_HOME }
            )
        },
        bottomBar = {
            if (screen in mainScreens) {
                BottomBar(
                    selected = screen,
                    onSelect = { screen = it },
                    onAdd = { openAddExpense(screen) }
                )
            }
        }
    ) { innerPadding ->
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                (slideInHorizontally(animationSpec = tween(140)) { it / 14 } +
                    fadeIn(animationSpec = tween(120))) togetherWith
                    fadeOut(animationSpec = tween(90))
            },
            label = "screen"
        ) { currentScreen ->
            when (currentScreen) {
                SCREEN_HOME -> HomeScreen(
                    state = state,
                    contentPadding = innerPadding,
                    onEdit = { id ->
                        editingExpenseId = id
                        returnScreen = SCREEN_HOME
                        screen = SCREEN_ADD
                    },
                    onHistory = { screen = SCREEN_HISTORY },
                    onStatistics = { screen = SCREEN_STATS },
                    onBudget = { screen = SCREEN_BUDGET },
                    onCategories = { screen = SCREEN_CATEGORIES }
                )

                SCREEN_HISTORY -> HistoryScreen(
                    state = state,
                    contentPadding = innerPadding,
                    onEdit = { id ->
                        editingExpenseId = id
                        returnScreen = SCREEN_HISTORY
                        screen = SCREEN_ADD
                    },
                    onDelete = viewModel::deleteExpense
                )

                SCREEN_STATS -> StatisticsScreen(state, innerPadding)
                SCREEN_BUDGET -> BudgetScreen(state, innerPadding, viewModel::saveSettings)
                SCREEN_CATEGORIES -> CategoriesScreen(
                    categories = state.categories,
                    contentPadding = innerPadding,
                    onAdd = viewModel::addCategory,
                    onRename = viewModel::renameCategory,
                    onDelete = viewModel::deleteCategory
                )

                SCREEN_ADD -> AddExpenseScreen(
                    expense = editingExpenseId?.let { id -> state.expenses.firstOrNull { it.id == id } },
                    categories = state.categories,
                    contentPadding = innerPadding,
                    onSave = { id, amount, categoryId, date, note ->
                        viewModel.saveExpense(id, amount, categoryId, date, note)
                        screen = returnScreen
                    }
                )
            }
        }
    }
}

@Composable
private fun AppHeader(title: String, showBack: Boolean, onBack: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().height(52.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showBack) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            } else {
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun BottomBar(selected: String, onSelect: (String) -> Unit, onAdd: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 5.dp, tonalElevation = 1.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().height(64.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomItem("Home", Icons.Default.Home, selected == SCREEN_HOME) { onSelect(SCREEN_HOME) }
            BottomItem("Transactions", Icons.Default.List, selected == SCREEN_HISTORY) { onSelect(SCREEN_HISTORY) }
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                FloatingActionButton(
                    onClick = onAdd,
                    modifier = Modifier.size(50.dp),
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) { Icon(Icons.Default.Add, contentDescription = "Add expense") }
            }
            BottomItem("Stats", Icons.Default.Info, selected == SCREEN_STATS) { onSelect(SCREEN_STATS) }
            BottomItem("Budget", Icons.Default.Settings, selected == SCREEN_BUDGET) { onSelect(SCREEN_BUDGET) }
        }
    }
}

@Composable
private fun RowScope.BottomItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier.weight(1f).height(56.dp).clickable(onClick = onClick).padding(top = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(21.dp), tint = color)
        Text(label, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1)
    }
}

@Composable
private fun HomeScreen(
    state: ExpenseUiState,
    contentPadding: PaddingValues,
    onEdit: (Long) -> Unit,
    onHistory: () -> Unit,
    onStatistics: () -> Unit,
    onBudget: () -> Unit,
    onCategories: () -> Unit
) {
    val month = YearMonth.now()
    val monthExpenses = state.expenses.filter { YearMonth.from(it.date) == month }
    val spent = monthExpenses.sumOf { it.amountMinor }
    val income = state.settings.incomeMinor
    val balance = income - spent
    val budget = state.settings.budgetMinor

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = screenPadding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { BalanceCard(balance, spent, budget, onBudget) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FinanceSummaryCard(
                    "Planned income",
                    income,
                    "Monthly plan",
                    true,
                    Modifier.weight(1f),
                    onBudget
                )
                FinanceSummaryCard(
                    "Expenses",
                    spent,
                    "${monthExpenses.size} this month",
                    false,
                    Modifier.weight(1f),
                    onStatistics
                )
            }
        }
        item { SectionHeader("Recent transactions", "See all", onHistory) }
        if (state.expenses.isEmpty()) {
            item { EmptyState("No expenses yet", "Use the + button to record your first expense.") }
        } else {
            items(state.expenses.take(5), key = { it.id }) { expense ->
                TransactionRow(expense, { onEdit(expense.id) })
            }
        }
        if (budget > 0L) {
            item { SectionHeader("Budget overview", "Open", onBudget) }
            item { BudgetPreview(spent, budget, onBudget) }
        }
        item {
            TextButton(onClick = onCategories, contentPadding = PaddingValues(horizontal = 4.dp)) {
                Text("Manage expense categories")
            }
        }
    }
}

@Composable
private fun BalanceCard(balance: Long, spent: Long, budget: Long, onClick: () -> Unit) {
    val finance = LocalFinanceColors.current
    val balanceColor = if (balance < 0L) finance.expense else MaterialTheme.colorScheme.onSurface
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 1.dp
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("Available this month", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                formatMoney(balance),
                style = MaterialTheme.typography.headlineMedium,
                color = balanceColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "Planned income minus this month’s expenses",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (budget > 0L) {
                val ratio = spent.toFloat() / budget.toFloat()
                val tone = budgetTone(ratio)
                Spacer(Modifier.height(3.dp))
                LinearProgressIndicator(
                    progress = ratio.coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxWidth(),
                    color = tone,
                    trackColor = tone.copy(alpha = 0.15f)
                )
                Text(
                    "${(ratio * 100).toInt()}% of spending budget used",
                    style = MaterialTheme.typography.bodySmall,
                    color = tone
                )
            }
        }
    }
}

@Composable
private fun FinanceSummaryCard(
    label: String,
    value: Long,
    supporting: String,
    positive: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val finance = LocalFinanceColors.current
    val accent = if (positive) finance.income else finance.expense
    val container = if (positive) finance.incomeContainer else finance.expenseContainer
    Surface(
        modifier = modifier.heightIn(min = 102.dp).clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(accent, CircleShape))
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                formatMoney(value),
                style = MaterialTheme.typography.titleLarge,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Surface(shape = CircleShape, color = container) {
                Text(
                    supporting,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 6.dp)) { Text(action) }
        }
    }
}

@Composable
private fun TransactionRow(expense: Expense, onClick: () -> Unit, onDelete: (() -> Unit)? = null) {
    val finance = LocalFinanceColors.current
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 9.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CategoryAvatar(expense.categoryName)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    expense.note.ifBlank { expense.categoryName },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${expense.categoryName} · ${formatRelativeDate(expense.date)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "-${formatMoney(expense.amountMinor)}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = finance.expense,
                maxLines = 1
            )
            if (onDelete != null) {
                IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete expense",
                        modifier = Modifier.size(19.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Spacer(Modifier.width(6.dp))
            }
        }
    }
}

@Composable
private fun CategoryAvatar(categoryName: String) {
    val color = categoryColor(categoryName)
    Surface(shape = CircleShape, color = color.copy(alpha = 0.13f), modifier = Modifier.size(40.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                categoryMonogram(categoryName),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
private fun BudgetPreview(spent: Long, budget: Long, onClick: () -> Unit) {
    val remaining = budget - spent
    val ratio = if (budget <= 0L) 0f else spent.toFloat() / budget.toFloat()
    val tone = budgetTone(ratio)
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row {
                Text("Monthly spending", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text("${formatMoney(spent)} / ${formatMoney(budget)}", fontWeight = FontWeight.SemiBold)
            }
            LinearProgressIndicator(
                progress = ratio.coerceIn(0f, 1f),
                modifier = Modifier.fillMaxWidth(),
                color = tone,
                trackColor = tone.copy(alpha = 0.15f)
            )
            Row {
                Text("${(ratio * 100).toInt()}% used", style = MaterialTheme.typography.bodySmall, color = tone)
                Spacer(Modifier.weight(1f))
                Text(
                    if (remaining >= 0L) "${formatMoney(remaining)} remaining" else "${formatMoney(-remaining)} over budget",
                    style = MaterialTheme.typography.bodySmall,
                    color = tone
                )
            }
        }
    }
}

@Composable
private fun AddExpenseScreen(
    expense: Expense?,
    categories: List<Category>,
    contentPadding: PaddingValues,
    onSave: (Long?, Long, Long, Long, String) -> Unit
) {
    val context = LocalContext.current
    val finance = LocalFinanceColors.current
    val focusRequester = remember { FocusRequester() }
    var amountText by rememberSaveable(expense?.id) {
        mutableStateOf(expense?.amountMinor?.let(::amountForInput).orEmpty())
    }
    var categoryId by rememberSaveable(expense?.id) {
        mutableStateOf(expense?.categoryId ?: categories.firstOrNull()?.id)
    }
    var dateEpochDay by rememberSaveable(expense?.id) {
        mutableStateOf(expense?.dateEpochDay ?: LocalDate.now().toEpochDay())
    }
    var note by rememberSaveable(expense?.id) { mutableStateOf(expense?.note.orEmpty()) }
    var validationError by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedDate = LocalDate.ofEpochDay(dateEpochDay)

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = screenPadding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Surface(shape = CircleShape, color = finance.expenseContainer) {
                Text(
                    "Expense",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = finance.expense
                )
            }
        }
        item {
            OutlinedTextField(
                value = amountText,
                onValueChange = {
                    amountText = sanitizeMoneyInput(it)
                    validationError = null
                },
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                label = { Text("Amount") },
                prefix = { Text("₹ ") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                isError = validationError != null,
                supportingText = { validationError?.let { Text(it) } },
                shape = MaterialTheme.shapes.medium
            )
        }
        item { Text("Category", style = MaterialTheme.typography.titleSmall) }
        items(categories.chunked(2)) { rowCategories ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowCategories.forEach { category ->
                    CategoryChoice(
                        category,
                        category.id == categoryId,
                        { categoryId = category.id },
                        Modifier.weight(1f)
                    )
                }
                if (rowCategories.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        item {
            OutlinedButton(
                onClick = {
                    DatePickerDialog(
                        context,
                        { _, year, month, day -> dateEpochDay = LocalDate.of(year, month + 1, day).toEpochDay() },
                        selectedDate.year,
                        selectedDate.monthValue - 1,
                        selectedDate.dayOfMonth
                    ).show()
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = MaterialTheme.shapes.medium
            ) { Text("Date · ${formatDate(selectedDate)}") }
        }
        item {
            OutlinedTextField(
                value = note,
                onValueChange = { if (it.length <= 120) note = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Note (optional)") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )
        }
        item {
            Button(
                onClick = {
                    val amount = parseAmount(amountText)
                    when {
                        amount == null -> validationError = "Enter an amount greater than zero."
                        categoryId == null -> validationError = "Select a category."
                        else -> onSave(expense?.id, amount, categoryId!!, dateEpochDay, note)
                    }
                },
                enabled = categories.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = MaterialTheme.shapes.medium
            ) { Text(if (expense == null) "Save expense" else "Save changes") }
        }
    }
}

@Composable
private fun CategoryChoice(category: Category, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val accent = categoryColor(category.name)
    Surface(
        modifier = modifier.defaultMinSize(minHeight = 48.dp).clickable(onClick = onClick),
        shape = MaterialTheme.shapes.small,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(8.dp).background(accent, CircleShape))
            Spacer(Modifier.width(7.dp))
            Text(category.name, style = MaterialTheme.typography.bodySmall, maxLines = 2)
        }
    }
}

@Composable
private fun HistoryScreen(
    state: ExpenseUiState,
    contentPadding: PaddingValues,
    onEdit: (Long) -> Unit,
    onDelete: (Long) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedCategoryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedMonth by rememberSaveable { mutableStateOf("all") }
    var deleteTarget by remember { mutableStateOf<Expense?>(null) }
    val months = state.expenses.map { YearMonth.from(it.date) }.distinct().sortedDescending()

    LaunchedEffect(state.categories) {
        if (selectedCategoryId != null && state.categories.none { it.id == selectedCategoryId }) {
            selectedCategoryId = null
        }
    }

    val normalizedQuery = query.trim().lowercase(Locale.getDefault())
    val filtered = state.expenses.filter { expense ->
        val matchesQuery = normalizedQuery.isEmpty() ||
            expense.note.lowercase(Locale.getDefault()).contains(normalizedQuery) ||
            expense.categoryName.lowercase(Locale.getDefault()).contains(normalizedQuery)
        val matchesCategory = selectedCategoryId == null || expense.categoryId == selectedCategoryId
        val matchesMonth = selectedMonth == "all" || YearMonth.from(expense.date).toString() == selectedMonth
        matchesQuery && matchesCategory && matchesMonth
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = screenPadding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search transactions") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CategoryFilter(state.categories, selectedCategoryId, { selectedCategoryId = it }, Modifier.weight(1f))
                MonthFilter(months, selectedMonth, { selectedMonth = it }, Modifier.weight(1f))
            }
        }
        item {
            Text(
                "${filtered.size} transaction${if (filtered.size == 1) "" else "s"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (filtered.isEmpty()) {
            item { EmptyState("No transactions found", "Try changing the search or filters.") }
        } else {
            items(filtered, key = { it.id }) { expense ->
                TransactionRow(expense, { onEdit(expense.id) }, { deleteTarget = expense })
            }
        }
    }

    deleteTarget?.let { expense ->
        DeleteExpenseDialog(expense, { deleteTarget = null }) {
            onDelete(expense.id)
            deleteTarget = null
        }
    }
}

@Composable
private fun CategoryFilter(
    categories: List<Category>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    modifier: Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val label = categories.firstOrNull { it.id == selectedId }?.name ?: "All categories"
    Box(modifier) {
        FilterButton(label) { expanded = true }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("All categories") }, onClick = {
                onSelect(null)
                expanded = false
            })
            categories.forEach { category ->
                DropdownMenuItem(text = { Text(category.name) }, onClick = {
                    onSelect(category.id)
                    expanded = false
                })
            }
        }
    }
}

@Composable
private fun MonthFilter(
    months: List<YearMonth>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val month = months.firstOrNull { it.toString() == selected }
    Box(modifier) {
        FilterButton(month?.format(DateTimeFormatter.ofPattern("MMM yyyy")) ?: "All months") { expanded = true }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("All months") }, onClick = {
                onSelect("all")
                expanded = false
            })
            months.forEach { item ->
                DropdownMenuItem(text = { Text(item.format(DateTimeFormatter.ofPattern("MMMM yyyy"))) }, onClick = {
                    onSelect(item.toString())
                    expanded = false
                })
            }
        }
    }
}

@Composable
private fun FilterButton(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(46.dp),
        contentPadding = PaddingValues(horizontal = 10.dp),
        shape = MaterialTheme.shapes.small
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DeleteExpenseDialog(expense: Expense, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete expense?") },
        text = { Text("${expense.note.ifBlank { expense.categoryName }} · ${formatMoney(expense.amountMinor)}") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text("Delete") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun StatisticsScreen(state: ExpenseUiState, contentPadding: PaddingValues) {
    val finance = LocalFinanceColors.current
    val today = LocalDate.now()
    val currentMonth = YearMonth.from(today)
    val monthExpenses = state.expenses.filter { YearMonth.from(it.date) == currentMonth }
    val yearExpenses = state.expenses.filter { it.date.year == today.year }
    val monthTotal = monthExpenses.sumOf { it.amountMinor }
    val yearTotal = yearExpenses.sumOf { it.amountMinor }
    val categoryTotals = monthExpenses.groupBy { it.categoryName }
        .mapValues { (_, expenses) -> expenses.sumOf { it.amountMinor } }
        .toList()
        .sortedByDescending { it.second }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = screenPadding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("This month", monthTotal, finance.expense, Modifier.weight(1f))
                StatCard("This year", yearTotal, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            }
        }
        if (categoryTotals.isNotEmpty()) {
            item { HighestCategoryCard(categoryTotals.first()) }
        }
        item { SectionHeader("Spending by category") }
        if (categoryTotals.isEmpty()) {
            item { EmptyState("No statistics yet", "Add expenses to see your monthly breakdown.") }
        } else {
            items(categoryTotals, key = { it.first }) { (category, amount) ->
                CategoryStatRow(category, amount, monthTotal)
            }
        }
    }
}

@Composable
private fun StatCard(label: String, amount: Long, accent: Color, modifier: Modifier) {
    Surface(
        modifier = modifier.heightIn(min = 88.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                formatMoney(amount),
                style = MaterialTheme.typography.titleLarge,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun HighestCategoryCard(highest: Pair<String, Long>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CategoryAvatar(highest.first)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Highest spending category", style = MaterialTheme.typography.bodySmall)
                Text(highest.first, style = MaterialTheme.typography.titleSmall)
            }
            Text(formatMoney(highest.second), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun CategoryStatRow(category: String, amount: Long, total: Long) {
    val accent = categoryColor(category)
    val fraction = if (total <= 0L) 0f else amount.toFloat() / total.toFloat()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).background(accent, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(category, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(formatMoney(amount), fontWeight = FontWeight.SemiBold)
            }
            LinearProgressIndicator(
                progress = fraction.coerceIn(0f, 1f),
                modifier = Modifier.fillMaxWidth(),
                color = accent,
                trackColor = accent.copy(alpha = 0.14f)
            )
            Text(
                "${(fraction * 100).toInt()}% of this month’s expenses",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BudgetScreen(
    state: ExpenseUiState,
    contentPadding: PaddingValues,
    onSave: (MonthlySettings) -> Unit
) {
    var incomeText by rememberSaveable(state.settings.incomeMinor) {
        mutableStateOf(amountForInput(state.settings.incomeMinor))
    }
    var budgetText by rememberSaveable(state.settings.budgetMinor) {
        mutableStateOf(amountForInput(state.settings.budgetMinor))
    }
    var savingsText by rememberSaveable(state.settings.savingsGoalMinor) {
        mutableStateOf(amountForInput(state.settings.savingsGoalMinor))
    }
    val spent = state.expenses.filter { YearMonth.from(it.date) == YearMonth.now() }.sumOf { it.amountMinor }
    val parsedIncome = parseAmountAllowZero(incomeText)
    val parsedBudget = parseAmountAllowZero(budgetText)
    val parsedGoal = parseAmountAllowZero(savingsText)
    val valid = parsedIncome != null && parsedBudget != null && parsedGoal != null
    val draft = if (valid) MonthlySettings(parsedIncome!!, parsedBudget!!, parsedGoal!!) else null
    val hasChanges = draft != null && draft != state.settings

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = screenPadding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { BudgetOverview(parsedIncome ?: 0L, spent, parsedBudget ?: 0L, parsedGoal ?: 0L) }
        item { SectionHeader("Monthly plan details") }
        item { MoneyField("Monthly income", incomeText) { incomeText = sanitizeMoneyInput(it) } }
        item { MoneyField("Spending budget", budgetText) { budgetText = sanitizeMoneyInput(it) } }
        item { MoneyField("Savings goal", savingsText) { savingsText = sanitizeMoneyInput(it) } }
        if (!valid) {
            item {
                Text(
                    "Enter valid amounts, or leave a field blank for zero.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        item {
            Button(
                onClick = { draft?.let(onSave) },
                enabled = valid && hasChanges,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = MaterialTheme.shapes.medium
            ) { Text("Save monthly plan") }
        }
        if (valid && !hasChanges) {
            item {
                Text(
                    "Monthly plan saved locally",
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalFinanceColors.current.income,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun BudgetOverview(income: Long, spent: Long, budget: Long, savingsGoal: Long) {
    val finance = LocalFinanceColors.current
    val remaining = income - spent
    val budgetRatio = if (budget <= 0L) 0f else spent.toFloat() / budget.toFloat()
    val savingsProgress = if (savingsGoal <= 0L) 0f else max(remaining, 0L).toFloat() / savingsGoal.toFloat()
    val tone = budgetTone(budgetRatio)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 1.dp
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OverviewLine("Income", formatMoney(income), finance.income)
            OverviewLine("Spent", formatMoney(spent), finance.expense)
            OverviewLine(
                "Remaining",
                formatMoney(remaining),
                if (remaining >= 0L) MaterialTheme.colorScheme.onSurface else finance.expense
            )
            OverviewLine("Savings goal", formatMoney(savingsGoal), MaterialTheme.colorScheme.primary)
            if (budget > 0L) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row {
                    Text("Budget used", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text("${(budgetRatio * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = tone)
                }
                LinearProgressIndicator(
                    progress = budgetRatio.coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxWidth(),
                    color = tone,
                    trackColor = tone.copy(alpha = 0.15f)
                )
            }
            if (savingsGoal > 0L) {
                Row {
                    Text("Savings progress", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text(
                        "${(savingsProgress * 100).toInt().coerceAtLeast(0)}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = finance.income
                    )
                }
                LinearProgressIndicator(
                    progress = savingsProgress.coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxWidth(),
                    color = finance.income,
                    trackColor = finance.income.copy(alpha = 0.15f)
                )
            }
        }
    }
}

@Composable
private fun OverviewLine(label: String, value: String, valueColor: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.SemiBold, color = valueColor, maxLines = 1)
    }
}

@Composable
private fun MoneyField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        prefix = { Text("₹ ") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        shape = MaterialTheme.shapes.medium
    )
}

@Composable
private fun CategoriesScreen(
    categories: List<Category>,
    contentPadding: PaddingValues,
    onAdd: (String) -> Unit,
    onRename: (Long, String) -> Unit,
    onDelete: (Long) -> Unit
) {
    var editing by remember { mutableStateOf<Category?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Category?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = screenPadding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            OutlinedButton(
                onClick = { showAdd = true },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(7.dp))
                Text("Add custom category")
            }
        }
        item {
            Text(
                "Tap a category to rename it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        items(categories, key = { it.id }) { category ->
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { editing = category },
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 7.dp, bottom = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CategoryAvatar(category.name)
                    Spacer(Modifier.width(10.dp))
                    Text(category.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                    IconButton(onClick = { deleteTarget = category }, modifier = Modifier.size(40.dp)) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete ${category.name}",
                            modifier = Modifier.size(19.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (showAdd) {
        CategoryNameDialog("Add category", "", { showAdd = false }) { name ->
            onAdd(name)
            showAdd = false
        }
    }
    editing?.let { category ->
        CategoryNameDialog("Rename category", category.name, { editing = null }) { name ->
            onRename(category.id, name)
            editing = null
        }
    }
    deleteTarget?.let { category ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete ${category.name}?") },
            text = { Text("Existing expenses will be moved to another available category.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete(category.id)
                        deleteTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun CategoryNameDialog(
    title: String,
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by rememberSaveable(initialName) { mutableStateOf(initialName) }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    if (it.length <= 40) {
                        name = it
                        error = false
                    }
                },
                label = { Text("Category name") },
                singleLine = true,
                isError = error,
                supportingText = { if (error) Text("Enter a category name.") },
                shape = MaterialTheme.shapes.medium
            )
        },
        confirmButton = {
            Button(onClick = {
                if (name.isBlank()) error = true else onConfirm(name.trim())
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun EmptyState(title: String, message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun budgetTone(ratio: Float): Color {
    val finance = LocalFinanceColors.current
    return when {
        ratio > 1f -> finance.expense
        ratio >= 0.75f -> finance.warning
        else -> finance.income
    }
}

private fun screenPadding(contentPadding: PaddingValues) = PaddingValues(
    start = 16.dp,
    top = contentPadding.calculateTopPadding() + 8.dp,
    end = 16.dp,
    bottom = contentPadding.calculateBottomPadding() + 16.dp
)

private fun categoryMonogram(categoryName: String): String {
    val words = categoryName.trim().split(Regex("\\s+|&")).filter { it.isNotBlank() }
    return words.take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("").ifBlank { "•" }
}

private fun formatMoney(amountMinor: Long): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
        currency = Currency.getInstance(CURRENCY_CODE)
        minimumFractionDigits = if (amountMinor % 100L == 0L) 0 else 2
        maximumFractionDigits = 2
    }
    return formatter.format(BigDecimal.valueOf(amountMinor, 2))
}

private fun formatDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale("en", "IN")))

private fun formatRelativeDate(date: LocalDate): String = when (date) {
    LocalDate.now() -> "Today"
    LocalDate.now().minusDays(1) -> "Yesterday"
    else -> formatDate(date)
}

private fun amountForInput(amountMinor: Long): String = when (amountMinor) {
    0L -> ""
    else -> BigDecimal.valueOf(amountMinor, 2).stripTrailingZeros().toPlainString()
}

private fun sanitizeMoneyInput(value: String): String {
    val filtered = value.filter { it.isDigit() || it == '.' }
    val firstDot = filtered.indexOf('.')
    return if (firstDot < 0) filtered else {
        filtered.substring(0, firstDot + 1) + filtered.substring(firstDot + 1).replace(".", "")
    }
}

private fun parseAmount(value: String): Long? = parseAmountAllowZero(value)?.takeIf { it > 0L }

private fun parseAmountAllowZero(value: String): Long? {
    if (value.isBlank()) return 0L
    return runCatching {
        BigDecimal(value.trim())
            .setScale(2, RoundingMode.HALF_UP)
            .movePointRight(2)
            .longValueExact()
            .takeIf { it >= 0L }
    }.getOrNull()
}
