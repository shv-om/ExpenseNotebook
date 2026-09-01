package com.shivam.expensenotebook

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shivam.expensenotebook.data.Category
import com.shivam.expensenotebook.data.Expense
import com.shivam.expensenotebook.data.ExpenseDatabase
import com.shivam.expensenotebook.data.MonthlySettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ExpenseUiState(
    val categories: List<Category> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val settings: MonthlySettings = MonthlySettings(),
    val isLoading: Boolean = true,
    val error: String? = null
)

class ExpenseViewModel(application: Application) : AndroidViewModel(application) {
    private val database = ExpenseDatabase(application)
    private val _state = MutableStateFlow(ExpenseUiState())
    val state: StateFlow<ExpenseUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun saveExpense(
        id: Long?,
        amountMinor: Long,
        categoryId: Long,
        dateEpochDay: Long,
        note: String
    ) = write {
        database.saveExpense(id, amountMinor, categoryId, dateEpochDay, note)
    }

    fun deleteExpense(id: Long) = write { database.deleteExpense(id) }

    fun addCategory(name: String) = write { database.addCategory(name) }

    fun renameCategory(id: Long, name: String) = write { database.renameCategory(id, name) }

    fun deleteCategory(id: Long) = write {
        if (!database.deleteCategory(id)) {
            error("At least one category must remain.")
        }
    }

    fun saveSettings(settings: MonthlySettings) = write { database.saveSettings(settings) }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    private fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                ExpenseUiState(
                    categories = database.getCategories(),
                    expenses = database.getExpenses(),
                    settings = database.getSettings(),
                    isLoading = false
                )
            }.onSuccess { _state.value = it }
                .onFailure { throwable ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = throwable.userMessage()
                    )
                }
        }
    }

    private fun write(action: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching(action)
                .onSuccess {
                    _state.value = _state.value.copy(
                        categories = database.getCategories(),
                        expenses = database.getExpenses(),
                        settings = database.getSettings(),
                        error = null
                    )
                }
                .onFailure { throwable ->
                    _state.value = _state.value.copy(error = throwable.userMessage())
                }
        }
    }

    private fun Throwable.userMessage(): String {
        val messageText = message.orEmpty()
        return when {
            messageText.contains("UNIQUE constraint", ignoreCase = true) ->
                "A category with that name already exists."
            messageText.isNotBlank() -> messageText
            else -> "The change could not be saved."
        }
    }

    override fun onCleared() {
        database.close()
        super.onCleared()
    }
}
