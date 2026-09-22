package com.shivam.expensenotebook

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shivam.expensenotebook.data.Category
import com.shivam.expensenotebook.data.Expense
import com.shivam.expensenotebook.data.ExpenseDatabase
import com.shivam.expensenotebook.data.ImportCandidate
import com.shivam.expensenotebook.data.ImportPreview
import com.shivam.expensenotebook.data.MonthlySettings
import com.shivam.expensenotebook.data.TransactionImportParser
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

data class ImportUiState(
    val isReading: Boolean = false,
    val preview: ImportPreview? = null,
    val selectedKeys: Set<String> = emptySet(),
    val message: String? = null,
    val error: String? = null
)

class ExpenseViewModel(application: Application) : AndroidViewModel(application) {
    private val database = ExpenseDatabase(application)
    private val _state = MutableStateFlow(ExpenseUiState())
    val state: StateFlow<ExpenseUiState> = _state.asStateFlow()
    private val _importState = MutableStateFlow(ImportUiState())
    val importState: StateFlow<ImportUiState> = _importState.asStateFlow()

    init {
        refresh()
    }

    fun saveExpense(
        id: Long?,
        amountMinor: Long,
        categoryId: Long,
        dateEpochDay: Long,
        note: String,
        receiver: String
    ) = write {
        database.saveExpense(id, amountMinor, categoryId, dateEpochDay, note, receiver)
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

    fun saveOwnAccountIdentifiers(value: String) = write {
        database.saveSettings(_state.value.settings.copy(ownAccountIdentifiers = value))
    }

    fun readImportFile(uri: Uri, ownAccountIdentifiers: String) {
        _importState.value = ImportUiState(isReading = true)
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val resolver = getApplication<Application>().contentResolver
                val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                } ?: "statement"
                TransactionImportParser.parse(
                    getApplication(),
                    uri,
                    name,
                    ownAccountIdentifiers
                )
            }.onSuccess { preview ->
                _importState.value = ImportUiState(
                    preview = preview,
                    selectedKeys = preview.transactions
                        .filterNot(ImportCandidate::isLikelyOwnTransfer)
                        .mapTo(mutableSetOf(), ImportCandidate::importKey)
                )
            }.onFailure { throwable ->
                _importState.value = ImportUiState(error = throwable.userMessage())
            }
        }
    }

    fun setImportSelected(importKey: String, selected: Boolean) {
        val current = _importState.value
        val keys = current.selectedKeys.toMutableSet()
        if (selected) keys += importKey else keys -= importKey
        _importState.value = current.copy(selectedKeys = keys, message = null)
    }

    fun selectAllImports(selected: Boolean) {
        val current = _importState.value
        val keys = if (selected) current.preview?.transactions?.mapTo(mutableSetOf(), ImportCandidate::importKey).orEmpty()
        else emptySet()
        _importState.value = current.copy(selectedKeys = keys, message = null)
    }

    fun setImportCategory(importKey: String, categoryName: String) {
        val current = _importState.value
        val preview = current.preview ?: return
        _importState.value = current.copy(
            preview = preview.copy(
                transactions = preview.transactions.map { transaction ->
                    if (transaction.importKey == importKey) transaction.copy(categoryName = categoryName) else transaction
                }
            )
        )
    }

    fun setImportGroupSelected(groupKey: String, selected: Boolean) {
        val current = _importState.value
        val groupKeys = current.preview?.transactions
            ?.filter { it.groupKey == groupKey }
            ?.map(ImportCandidate::importKey)
            .orEmpty()
        val selectedKeys = current.selectedKeys.toMutableSet()
        if (selected) selectedKeys.addAll(groupKeys) else selectedKeys.removeAll(groupKeys.toSet())
        _importState.value = current.copy(selectedKeys = selectedKeys, message = null)
    }

    fun setImportGroupCategory(groupKey: String, categoryName: String) {
        val current = _importState.value
        val preview = current.preview ?: return
        _importState.value = current.copy(
            preview = preview.copy(
                transactions = preview.transactions.map { transaction ->
                    if (transaction.groupKey == groupKey) transaction.copy(categoryName = categoryName)
                    else transaction
                }
            )
        )
    }

    fun importSelected() {
        val current = _importState.value
        val selected = current.preview?.transactions?.filter { it.importKey in current.selectedKeys }.orEmpty()
        if (selected.isEmpty()) return
        _importState.value = current.copy(isReading = true, message = null, error = null)
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                database.saveImportedExpenses(selected) to loadState()
            }.onSuccess { (result, refreshedState) ->
                _state.value = refreshedState
                _importState.value = ImportUiState(
                    message = "Imported ${result.imported} transaction${if (result.imported == 1) "" else "s"}. " +
                        "Skipped ${result.duplicates} duplicate${if (result.duplicates == 1) "" else "s"}."
                )
            }
                .onFailure { throwable ->
                    _importState.value = current.copy(isReading = false, error = throwable.userMessage())
                }
        }
    }

    fun clearImport() {
        _importState.value = ImportUiState()
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    private fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                loadState()
            }.onSuccess { _state.value = it }
                .onFailure { throwable ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = throwable.userMessage()
                    )
                }
        }
    }

    private fun loadState() = ExpenseUiState(
        categories = database.getCategories(),
        expenses = database.getExpenses(),
        settings = database.getSettings(),
        isLoading = false
    )

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
