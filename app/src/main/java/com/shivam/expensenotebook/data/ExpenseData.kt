package com.shivam.expensenotebook.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.LocalDate

const val CURRENCY_SYMBOL = "₹"
const val CURRENCY_CODE = "INR"

data class Category(
    val id: Long,
    val name: String
)

data class Expense(
    val id: Long,
    val amountMinor: Long,
    val categoryId: Long,
    val categoryName: String,
    val dateEpochDay: Long,
    val note: String,
    val receiver: String
) {
    val date: LocalDate
        get() = LocalDate.ofEpochDay(dateEpochDay)
}

data class MonthlySettings(
    val incomeMinor: Long = 0,
    val budgetMinor: Long = 0,
    val savingsGoalMinor: Long = 0,
    val ownAccountIdentifiers: String = ""
)

data class ImportCandidate(
    val importKey: String,
    val groupKey: String,
    val groupLabel: String,
    val isLikelyOwnTransfer: Boolean,
    val amountMinor: Long,
    val dateEpochDay: Long,
    val note: String,
    val sender: String,
    val receiver: String,
    val categoryName: String
)

data class ImportPreview(
    val sourceName: String,
    val transactions: List<ImportCandidate>,
    val likelyOwnTransfers: Int,
    val skippedCredits: Int,
    val skippedFailed: Int,
    val unparsedRows: Int
)

data class ImportSaveResult(val imported: Int, val duplicates: Int)

private val DEFAULT_CATEGORIES = listOf(
    "Random Expense",
    "Food & Dining",
    "Groceries",
    "Home",
    "Rent",
    "Utilities & Bills",
    "EMI",
    "Transportation",
    "Fuel",
    "Shopping",
    "Health & Medicine",
    "Education",
    "Entertainment",
    "Subscriptions",
    "Travel",
    "Personal Care",
    "Family",
    "Gifts",
    "Work",
    "Other"
)

class ExpenseDatabase(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE categories (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL COLLATE NOCASE UNIQUE
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE expenses (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                amount_minor INTEGER NOT NULL CHECK(amount_minor > 0),
                category_id INTEGER NOT NULL,
                date_epoch_day INTEGER NOT NULL,
                note TEXT NOT NULL DEFAULT '',
                receiver TEXT NOT NULL DEFAULT '',
                import_key TEXT,
                FOREIGN KEY(category_id) REFERENCES categories(id)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_expenses_date ON expenses(date_epoch_day DESC)")
        db.execSQL("CREATE INDEX idx_expenses_category ON expenses(category_id)")
        db.execSQL("CREATE UNIQUE INDEX idx_expenses_import_key ON expenses(import_key)")
        db.execSQL(
            """
            CREATE TABLE monthly_settings (
                id INTEGER PRIMARY KEY CHECK(id = 1),
                income_minor INTEGER NOT NULL DEFAULT 0,
                budget_minor INTEGER NOT NULL DEFAULT 0,
                savings_goal_minor INTEGER NOT NULL DEFAULT 0,
                own_account_identifiers TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )

        DEFAULT_CATEGORIES.forEach { name ->
            db.insertOrThrow("categories", null, ContentValues().apply { put("name", name) })
        }
        db.insertOrThrow("monthly_settings", null, ContentValues().apply { put("id", 1) })
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE expenses ADD COLUMN receiver TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE expenses ADD COLUMN import_key TEXT")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_expenses_import_key ON expenses(import_key)")
            db.execSQL("ALTER TABLE monthly_settings ADD COLUMN own_account_identifiers TEXT NOT NULL DEFAULT ''")
        }
    }

    fun getCategories(): List<Category> {
        val result = mutableListOf<Category>()
        readableDatabase.query(
            "categories",
            arrayOf("id", "name"),
            null,
            null,
            null,
            null,
            "name COLLATE NOCASE ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += Category(cursor.getLong(0), cursor.getString(1))
            }
        }
        return result
    }

    fun getExpenses(): List<Expense> {
        val result = mutableListOf<Expense>()
        readableDatabase.rawQuery(
            """
            SELECT e.id, e.amount_minor, e.category_id, c.name, e.date_epoch_day, e.note, e.receiver
            FROM expenses e
            JOIN categories c ON c.id = e.category_id
            ORDER BY e.date_epoch_day DESC, e.id DESC
            """.trimIndent(),
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += Expense(
                    id = cursor.getLong(0),
                    amountMinor = cursor.getLong(1),
                    categoryId = cursor.getLong(2),
                    categoryName = cursor.getString(3),
                    dateEpochDay = cursor.getLong(4),
                    note = cursor.getString(5),
                    receiver = cursor.getString(6)
                )
            }
        }
        return result
    }

    fun getSettings(): MonthlySettings {
        readableDatabase.query(
            "monthly_settings",
            arrayOf("income_minor", "budget_minor", "savings_goal_minor", "own_account_identifiers"),
            "id = 1",
            null,
            null,
            null,
            null
        ).use { cursor ->
            return if (cursor.moveToFirst()) {
                MonthlySettings(cursor.getLong(0), cursor.getLong(1), cursor.getLong(2), cursor.getString(3))
            } else {
                MonthlySettings()
            }
        }
    }

    fun saveExpense(
        id: Long?,
        amountMinor: Long,
        categoryId: Long,
        dateEpochDay: Long,
        note: String,
        receiver: String
    ) {
        val values = ContentValues().apply {
            put("amount_minor", amountMinor)
            put("category_id", categoryId)
            put("date_epoch_day", dateEpochDay)
            put("note", note.trim())
            put("receiver", receiver.trim())
        }
        if (id == null) {
            writableDatabase.insertOrThrow("expenses", null, values)
        } else {
            writableDatabase.update("expenses", values, "id = ?", arrayOf(id.toString()))
        }
    }

    fun saveImportedExpenses(transactions: List<ImportCandidate>): ImportSaveResult {
        if (transactions.isEmpty()) return ImportSaveResult(0, 0)
        val db = writableDatabase
        db.beginTransaction()
        return try {
            val categories = mutableMapOf<String, Long>()
            db.query("categories", arrayOf("id", "name"), null, null, null, null, null).use { cursor ->
                while (cursor.moveToNext()) categories[cursor.getString(1).lowercase()] = cursor.getLong(0)
            }
            val fallbackId = categories["other"] ?: categories.values.first()
            var imported = 0
            var duplicates = 0
            transactions.forEach { transaction ->
                val categoryId = categories[transaction.categoryName.lowercase()] ?: fallbackId
                val rowId = db.insertWithOnConflict(
                    "expenses",
                    null,
                    ContentValues().apply {
                        put("amount_minor", transaction.amountMinor)
                        put("category_id", categoryId)
                        put("date_epoch_day", transaction.dateEpochDay)
                        put("note", transaction.note.trim())
                        put("receiver", transaction.receiver.trim())
                        put("import_key", transaction.importKey)
                    },
                    SQLiteDatabase.CONFLICT_IGNORE
                )
                if (rowId == -1L) duplicates++ else imported++
            }
            db.setTransactionSuccessful()
            ImportSaveResult(imported, duplicates)
        } finally {
            db.endTransaction()
        }
    }

    fun deleteExpense(id: Long) {
        writableDatabase.delete("expenses", "id = ?", arrayOf(id.toString()))
    }

    fun addCategory(name: String) {
        writableDatabase.insertOrThrow(
            "categories",
            null,
            ContentValues().apply { put("name", name.trim()) }
        )
    }

    fun renameCategory(id: Long, name: String) {
        writableDatabase.update(
            "categories",
            ContentValues().apply { put("name", name.trim()) },
            "id = ?",
            arrayOf(id.toString())
        )
    }

    fun deleteCategory(id: Long): Boolean {
        val db = writableDatabase
        db.beginTransaction()
        return try {
            val count = db.rawQuery("SELECT COUNT(*) FROM categories", null).use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0)
            }
            if (count <= 1) return false

            val fallbackId = db.rawQuery(
                """
                SELECT id FROM categories
                WHERE id != ?
                ORDER BY CASE WHEN name = 'Other' COLLATE NOCASE THEN 0 ELSE 1 END, id
                LIMIT 1
                """.trimIndent(),
                arrayOf(id.toString())
            ).use { cursor ->
                check(cursor.moveToFirst())
                cursor.getLong(0)
            }

            db.execSQL(
                "UPDATE expenses SET category_id = ? WHERE category_id = ?",
                arrayOf(fallbackId, id)
            )
            db.delete("categories", "id = ?", arrayOf(id.toString()))
            db.setTransactionSuccessful()
            true
        } finally {
            db.endTransaction()
        }
    }

    fun saveSettings(settings: MonthlySettings) {
        writableDatabase.insertWithOnConflict(
            "monthly_settings",
            null,
            ContentValues().apply {
                put("id", 1)
                put("income_minor", settings.incomeMinor)
                put("budget_minor", settings.budgetMinor)
                put("savings_goal_minor", settings.savingsGoalMinor)
                put("own_account_identifiers", settings.ownAccountIdentifiers.trim())
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    companion object {
        private const val DATABASE_NAME = "expense_notebook.db"
        private const val DATABASE_VERSION = 2
    }
}
