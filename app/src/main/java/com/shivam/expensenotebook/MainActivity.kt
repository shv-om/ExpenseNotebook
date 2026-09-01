package com.shivam.expensenotebook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shivam.expensenotebook.ui.ExpenseNotebookApp
import com.shivam.expensenotebook.ui.theme.ExpenseNotebookTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ExpenseNotebookTheme {
                ExpenseNotebookApp(viewModel())
            }
        }
    }
}
