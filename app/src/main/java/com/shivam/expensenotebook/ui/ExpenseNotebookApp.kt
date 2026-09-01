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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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
import androidx.compose.ui.unit.sp
import com.shivam.expensenotebook.ExpenseUiState
import com.shivam.expensenotebook.ExpenseViewModel
import com.shivam.expensenotebook.data.CURRENCY_CODE
import com.shivam.expensenotebook.data.Category
import com.shivam.expensenotebook.data.Expense
import com.shivam.expensenotebook.data.MonthlySettings
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

private val LightColors = lightColorScheme(
    primary = Color(0xFF8A4E00),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDCB5),
    onPrimaryContainer = Color(0xFF2C1600),
    secondary = Color(0xFF6F5B40),
    background = Color(0xFFFFF8F2),
    surface = Color(0xFFFFF8F2),
    surfaceVariant = Color(0xFFF2E5D5)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB86B),
    onPrimary = Color(0xFF492900),
    primaryContainer = Color(0xFF693C00),
    onPrimaryContainer = Color(0xFFFFDCB5),
    secondary = Color(0xFFDDC2A0),
    background = Color(0xFF161310),
    surface = Color(0xFF161310),
    surfaceVariant = Color(0xFF514538)
)

@Composable
fun ExpenseNotebookTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}

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
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val mainScreens = setOf(SCREEN_HOME, SCREEN_HISTORY, SCREEN_STATS, SCREEN_BUDGET)
    Scaffold(
        topBar = {
            AppHeader(
                title = when (screen) {
                    SCREEN_HOME -> "My Expenses"
                    SCREEN_HISTORY -> "History"
                    SCREEN_STATS -> "Statistics"
                    SCREEN_BUDGET -> "Monthly Budget"
                    SCREEN_CATEGORIES -> "Categories"
                    SCREEN_ADD -> if (editingExpenseId == null) "Add Expense" else "Edit Expense"
                    else -> "Expense Notebook"
                },
                showBack = screen !in mainScreens,
                onBack = { screen = if (screen == SCREEN_ADD) returnScreen else SCREEN_HOME }
            )
        },
        bottomBar = {
            if (screen in mainScreens) {
                BottomBar(screen) { screen = it }
            }
        }
    ) { innerPadding ->
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                (slideInHorizontally(animationSpec = tween(160)) { it / 12 } +
                    fadeIn(animationSpec = tween(140))) togetherWith
                    fadeOut(animationSpec = tween(100))
            },
            label = "screen transition"
        ) { currentScreen ->
        when (currentScreen) {
            SCREEN_HOME -> HomeScreen(
                state = state,
                contentPadding = innerPadding,
                onAdd = {
                    editingExpenseId = null
                    returnScreen = SCREEN_HOME
                    screen = SCREEN_ADD
                },
                onEdit = {
                    editingExpenseId = it
                    returnScreen = SCREEN_HOME
                    screen = SCREEN_ADD
                },
                onCategories = { screen = SCREEN_CATEGORIES },
                onHistory = { screen = SCREEN_HISTORY },
                onStatistics = { screen = SCREEN_STATS }
            )

            SCREEN_HISTORY -> HistoryScreen(
                state = state,
                contentPadding = innerPadding,
                onEdit = {
                    editingExpenseId = it
                    returnScreen = SCREEN_HISTORY
                    screen = SCREEN_ADD
                },
                onDelete = viewModel::deleteExpense
            )

            SCREEN_STATS -> StatisticsScreen(state, innerPadding)
            SCREEN_BUDGET -> BudgetScreen(
                state = state,
                contentPadding = innerPadding,
                onSave = viewModel::saveSettings
            )

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
    Surface(tonalElevation = 2.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(58.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showBack) {
                TextButton(onClick = onBack) { Text("‹ Back", fontSize = 17.sp) }
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun BottomBar(selected: String, onSelect: (String) -> Unit) {
    val items = listOf(
        SCREEN_HOME to "Home",
        SCREEN_HISTORY to "History",
        SCREEN_STATS to "Statistics",
        SCREEN_BUDGET to "Budget"
    )
    Surface(tonalElevation = 4.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            items.forEach { (screen, label) ->
                TextButton(
                    onClick = { onSelect(screen) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        label,
                        fontWeight = if (selected == screen) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected == screen) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    state: ExpenseUiState,
    contentPadding: PaddingValues,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onCategories: () -> Unit,
    onHistory: () -> Unit,
    onStatistics: () -> Unit
) {
    val today = LocalDate.now()
    val month = YearMonth.from(today)
    val todayTotal = state.expenses.filter { it.date == today }.sumOf { it.amountMinor }
    val monthExpenses = state.expenses.filter { YearMonth.from(it.date) == month }
    val monthTotal = monthExpenses.sumOf { it.amountMinor }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = contentPadding.calculateTopPadding() + 16.dp,
            end = 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Button(
                onClick = onAdd,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("+  Add Expense", style = MaterialTheme.typography.titleMedium)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryCard(
                    "Today",
                    formatMoney(todayTotal),
                    Modifier.weight(1f),
                    accent = Color(0xFF1976D2),
                    onClick = onHistory
                )
                SummaryCard(
                    "This month",
                    formatMoney(monthTotal),
                    Modifier.weight(1f),
                    accent = Color(0xFFF57C00),
                    onClick = onStatistics
                )
            }
        }
        item {
            SummaryCard(
                label = "Expenses this month",
                value = monthExpenses.size.toString(),
                modifier = Modifier.fillMaxWidth(),
                accent = Color(0xFF7B1FA2),
                onClick = onHistory
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Recent expenses",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onHistory) { Text("View all") }
            }
        }
        if (state.expenses.isEmpty()) {
            item { EmptyMessage("No expenses yet. Tap Add Expense to begin.") }
        } else {
            items(state.expenses.take(6), key = { it.id }) { expense ->
                ExpenseRow(expense = expense, onClick = { onEdit(expense.id) })
            }
        }
        item {
            OutlinedButton(onClick = onCategories, modifier = Modifier.fillMaxWidth()) {
                Text("Manage Categories")
            }
        }
    }
}

@Composable
private fun SummaryCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    onClick: (() -> Unit)? = null
) {
    val clickableModifier = if (onClick == null) modifier else modifier.clickable(onClick = onClick)
    ElevatedCard(
        modifier = clickableModifier,
        colors = CardDefaults.elevatedCardColors(containerColor = accent.copy(alpha = 0.13f))
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(label, color = accent, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ExpenseRow(
    expense: Expense,
    onClick: () -> Unit,
    actions: (@Composable () -> Unit)? = null
) {
    val accent = categoryColor(expense.categoryName)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.12f))
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .background(accent, CircleShape)
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    "${formatMoney(expense.amountMinor)} • ${expense.categoryName} • ${formatDate(expense.date)}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
            }
            if (expense.note.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    expense.note,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            actions?.let {
                Spacer(Modifier.height(6.dp))
                it()
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

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = contentPadding.calculateTopPadding() + 16.dp,
            end = 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            OutlinedTextField(
                value = amountText,
                onValueChange = {
                    amountText = it.filter { character -> character.isDigit() || character == '.' }
                    validationError = null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                label = { Text("Amount (₹)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                isError = validationError != null,
                supportingText = { validationError?.let { Text(it) } }
            )
        }
        item {
            Text("Category", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        items(categories.chunked(2)) { rowCategories ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowCategories.forEach { category ->
                    CategoryChoice(
                        category = category,
                        selected = category.id == categoryId,
                        onClick = { categoryId = category.id },
                        modifier = Modifier.weight(1f)
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
                        { _, year, month, day ->
                            dateEpochDay = LocalDate.of(year, month + 1, day).toEpochDay()
                        },
                        selectedDate.year,
                        selectedDate.monthValue - 1,
                        selectedDate.dayOfMonth
                    ).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Date: ${formatDate(selectedDate)}")
            }
        }
        item {
            OutlinedTextField(
                value = note,
                onValueChange = { if (it.length <= 120) note = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Note (optional)") },
                singleLine = true
            )
        }
        item {
            Button(
                onClick = {
                    val amountMinor = parseAmount(amountText)
                    when {
                        amountMinor == null -> validationError = "Enter a valid amount greater than zero."
                        categoryId == null -> validationError = "Select a category."
                        else -> onSave(expense?.id, amountMinor, categoryId!!, dateEpochDay, note)
                    }
                },
                enabled = categories.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                Text(if (expense == null) "Save Expense" else "Save Changes")
            }
        }
    }
}

@Composable
private fun CategoryChoice(
    category: Category,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = categoryColor(category.name)
    Surface(
        modifier = modifier
            .defaultMinSize(minHeight = 52.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) accent else accent.copy(alpha = 0.13f),
        contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(
            1.dp,
            if (selected) accent else accent.copy(alpha = 0.45f)
        )
    ) {
        Box(Modifier.padding(10.dp), contentAlignment = Alignment.Center) {
            Text(category.name, textAlign = TextAlign.Center, maxLines = 2)
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

    val filtered = state.expenses.filter { expense ->
        val normalizedQuery = query.trim().lowercase(Locale.getDefault())
        val matchesQuery = normalizedQuery.isEmpty() ||
            expense.note.lowercase(Locale.getDefault()).contains(normalizedQuery) ||
            expense.categoryName.lowercase(Locale.getDefault()).contains(normalizedQuery)
        val matchesCategory = selectedCategoryId == null || expense.categoryId == selectedCategoryId
        val matchesMonth = selectedMonth == "all" || YearMonth.from(expense.date).toString() == selectedMonth
        matchesQuery && matchesCategory && matchesMonth
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = contentPadding.calculateTopPadding() + 12.dp,
            end = 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search note or category") },
                singleLine = true
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CategoryFilter(
                    categories = state.categories,
                    selectedId = selectedCategoryId,
                    onSelect = { selectedCategoryId = it },
                    modifier = Modifier.weight(1f)
                )
                MonthFilter(
                    months = months,
                    selected = selectedMonth,
                    onSelect = { selectedMonth = it },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            Text(
                "${filtered.size} expense${if (filtered.size == 1) "" else "s"}",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (filtered.isEmpty()) {
            item { EmptyMessage("No expenses match these filters.") }
        } else {
            items(filtered, key = { it.id }) { expense ->
                ExpenseRow(expense = expense, onClick = { onEdit(expense.id) }) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { onEdit(expense.id) }) { Text("Edit") }
                        TextButton(onClick = { deleteTarget = expense }) { Text("Delete") }
                    }
                }
            }
        }
    }

    deleteTarget?.let { expense ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete expense?") },
            text = { Text("${formatMoney(expense.amountMinor)} • ${expense.categoryName}") },
            confirmButton = {
                Button(onClick = {
                    onDelete(expense.id)
                    deleteTarget = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun CategoryFilter(
    categories: List<Category>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val label = categories.firstOrNull { it.id == selectedId }?.name ?: "All categories"
    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("All categories") },
                onClick = { onSelect(null); expanded = false }
            )
            categories.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category.name) },
                    onClick = { onSelect(category.id); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun MonthFilter(
    months: List<YearMonth>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedMonth = months.firstOrNull { it.toString() == selected }
    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(selectedMonth?.format(DateTimeFormatter.ofPattern("MMM yyyy")) ?: "All months")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("All months") },
                onClick = { onSelect("all"); expanded = false }
            )
            months.forEach { month ->
                DropdownMenuItem(
                    text = { Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy"))) },
                    onClick = { onSelect(month.toString()); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun StatisticsScreen(state: ExpenseUiState, contentPadding: PaddingValues) {
    val today = LocalDate.now()
    val currentMonth = YearMonth.from(today)
    val monthExpenses = state.expenses.filter { YearMonth.from(it.date) == currentMonth }
    val yearExpenses = state.expenses.filter { it.date.year == today.year }
    val monthTotal = monthExpenses.sumOf { it.amountMinor }
    val yearTotal = yearExpenses.sumOf { it.amountMinor }
    val categoryTotals = monthExpenses
        .groupBy { it.categoryName }
        .mapValues { (_, expenses) -> expenses.sumOf { it.amountMinor } }
        .toList()
        .sortedByDescending { it.second }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = contentPadding.calculateTopPadding() + 16.dp,
            end = 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryCard("This month", formatMoney(monthTotal), Modifier.weight(1f))
                SummaryCard("This year", formatMoney(yearTotal), Modifier.weight(1f))
            }
        }
        item {
            Text(
                "Spending by category this month",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        if (categoryTotals.isEmpty()) {
            item { EmptyMessage("Add expenses to see your spending breakdown.") }
        } else {
            items(categoryTotals, key = { it.first }) { (category, amount) ->
                val fraction = if (monthTotal == 0L) 0f else amount.toFloat() / monthTotal.toFloat()
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(category, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text(formatMoney(amount))
                        }
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = fraction.coerceIn(0f, 1f),
                            modifier = Modifier.fillMaxWidth(),
                            color = categoryColor(category),
                            trackColor = categoryColor(category).copy(alpha = 0.18f)
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            "${(fraction * 100).toInt()}% of this month’s spending",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
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
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val month = YearMonth.now()
    val spent = state.expenses.filter { YearMonth.from(it.date) == month }.sumOf { it.amountMinor }
    val income = parseOptionalAmount(incomeText)
    val budget = parseOptionalAmount(budgetText)
    val goal = parseOptionalAmount(savingsText)
    val remaining = income - spent
    val savingsProgress = if (goal <= 0) 0f else max(remaining, 0L).toFloat() / goal.toFloat()
    val budgetProgress = if (budget <= 0) 0f else spent.toFloat() / budget.toFloat()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = contentPadding.calculateTopPadding() + 16.dp,
            end = 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 20.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            BudgetOverview(income, spent, remaining, goal, savingsProgress, budget, budgetProgress)
        }
        item {
            Text(
                "Monthly plan",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        item { MoneyField("Monthly income", incomeText) { incomeText = it; error = null } }
        item { MoneyField("Monthly spending budget", budgetText) { budgetText = it; error = null } }
        item { MoneyField("Monthly savings goal", savingsText) { savingsText = it; error = null } }
        error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
        item {
            Button(
                onClick = {
                    val parsedIncome = parseAmountAllowZero(incomeText)
                    val parsedBudget = parseAmountAllowZero(budgetText)
                    val parsedGoal = parseAmountAllowZero(savingsText)
                    if (parsedIncome == null || parsedBudget == null || parsedGoal == null) {
                        error = "Enter valid amounts, or leave a field blank for zero."
                    } else {
                        onSave(MonthlySettings(parsedIncome, parsedBudget, parsedGoal))
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) { Text("Save Monthly Plan") }
        }
    }
}

@Composable
private fun BudgetOverview(
    income: Long,
    spent: Long,
    remaining: Long,
    goal: Long,
    savingsProgress: Float,
    budget: Long,
    budgetProgress: Float
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            OverviewLine("Income", formatMoney(income))
            OverviewLine("Spent", formatMoney(spent))
            OverviewLine("Remaining", formatMoney(remaining))
            OverviewLine("Savings goal", formatMoney(goal))
            if (goal > 0) {
                Spacer(Modifier.height(3.dp))
                Text("Savings goal progress", style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(
                    progress = savingsProgress.coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF2E7D32),
                    trackColor = Color(0xFF2E7D32).copy(alpha = 0.18f)
                )
                Text(
                    "${(savingsProgress * 100).toInt().coerceAtLeast(0)}%",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (budget > 0) {
                Spacer(Modifier.height(3.dp))
                Text("Budget used", style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(
                    progress = budgetProgress.coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFFF57C00),
                    trackColor = Color(0xFFF57C00).copy(alpha = 0.18f)
                )
                Text(
                    "${formatMoney(spent)} of ${formatMoney(budget)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun OverviewLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MoneyField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { input ->
            onValueChange(input.filter { it.isDigit() || it == '.' })
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("$label (₹)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true
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
        contentPadding = PaddingValues(
            start = 16.dp,
            top = contentPadding.calculateTopPadding() + 16.dp,
            end = 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 20.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Button(
                onClick = { showAdd = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) { Text("+  Add Custom Category") }
        }
        items(categories, key = { it.id }) { category ->
            Card(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(category.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                    TextButton(onClick = { editing = category }) { Text("Rename") }
                    TextButton(onClick = { deleteTarget = category }) { Text("Delete") }
                }
            }
        }
    }

    if (showAdd) {
        CategoryNameDialog(
            title = "Add category",
            initialName = "",
            onDismiss = { showAdd = false },
            onConfirm = { name -> onAdd(name); showAdd = false }
        )
    }
    editing?.let { category ->
        CategoryNameDialog(
            title = "Rename category",
            initialName = category.name,
            onDismiss = { editing = null },
            onConfirm = { name -> onRename(category.id, name); editing = null }
        )
    }
    deleteTarget?.let { category ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete ${category.name}?") },
            text = { Text("Existing expenses will be moved to another available category.") },
            confirmButton = {
                Button(onClick = { onDelete(category.id); deleteTarget = null }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
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
                onValueChange = { if (it.length <= 40) { name = it; error = false } },
                label = { Text("Category name") },
                singleLine = true,
                isError = error,
                supportingText = { if (error) Text("Enter a category name.") }
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
private fun EmptyMessage(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(22.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
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

private fun amountForInput(amountMinor: Long): String = when (amountMinor) {
    0L -> ""
    else -> BigDecimal.valueOf(amountMinor, 2).stripTrailingZeros().toPlainString()
}

private fun parseAmount(value: String): Long? = runCatching {
    BigDecimal(value.trim())
        .setScale(2, RoundingMode.HALF_UP)
        .movePointRight(2)
        .longValueExact()
        .takeIf { it > 0L }
}.getOrNull()

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

private fun parseOptionalAmount(value: String): Long = parseAmountAllowZero(value) ?: 0L

private val CategoryColors = listOf(
    Color(0xFF1565C0),
    Color(0xFFEF6C00),
    Color(0xFF2E7D32),
    Color(0xFF7B1FA2),
    Color(0xFFC62828),
    Color(0xFF00838F),
    Color(0xFF5D4037),
    Color(0xFF455A64)
)

private fun categoryColor(categoryName: String): Color {
    val index = (categoryName.hashCode() and Int.MAX_VALUE) % CategoryColors.size
    return CategoryColors[index]
}
