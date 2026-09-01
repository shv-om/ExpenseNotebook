package com.shivam.expensenotebook.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val BrandIndigo = Color(0xFF4F46E5)
val BrandIndigoDark = Color(0xFF3730A3)
val BrandIndigoLight = Color(0xFFEEF2FF)
val IncomeGreen = Color(0xFF16A34A)
val IncomeGreenLight = Color(0xFFDCFCE7)
val ExpenseRed = Color(0xFFDC2626)
val ExpenseRedLight = Color(0xFFFEE2E2)
val WarningAmber = Color(0xFFD97706)
val WarningAmberLight = Color(0xFFFEF3C7)

data class FinanceColors(
    val income: Color,
    val incomeContainer: Color,
    val expense: Color,
    val expenseContainer: Color,
    val warning: Color,
    val warningContainer: Color
)

private val LightFinanceColors = FinanceColors(
    income = IncomeGreen,
    incomeContainer = IncomeGreenLight,
    expense = ExpenseRed,
    expenseContainer = ExpenseRedLight,
    warning = WarningAmber,
    warningContainer = WarningAmberLight
)

private val DarkFinanceColors = FinanceColors(
    income = Color(0xFF4ADE80),
    incomeContainer = Color(0xFF143D27),
    expense = Color(0xFFFB7185),
    expenseContainer = Color(0xFF4C1D28),
    warning = Color(0xFFFBBF24),
    warningContainer = Color(0xFF493510)
)

val LocalFinanceColors = staticCompositionLocalOf { LightFinanceColors }

private val LightColors = lightColorScheme(
    primary = BrandIndigo,
    onPrimary = Color.White,
    primaryContainer = BrandIndigoLight,
    onPrimaryContainer = BrandIndigoDark,
    secondary = Color(0xFF475569),
    onSecondary = Color.White,
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF64748B),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),
    error = ExpenseRed,
    onError = Color.White,
    errorContainer = ExpenseRedLight,
    onErrorContainer = Color(0xFF7F1D1D)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF818CF8),
    onPrimary = Color(0xFF1E1B4B),
    primaryContainer = Color(0xFF312E81),
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = Color(0xFFCBD5E1),
    onSecondary = Color(0xFF1E293B),
    background = Color(0xFF0F172A),
    onBackground = Color(0xFFF8FAFC),
    surface = Color(0xFF1E293B),
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0xFF334155),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF475569),
    outlineVariant = Color(0xFF334155),
    error = Color(0xFFFB7185),
    onError = Color(0xFF4C0519),
    errorContainer = Color(0xFF4C1D28),
    onErrorContainer = Color(0xFFFFE4E6)
)

private val AppTypography = Typography(
    headlineMedium = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp)
)

@Composable
fun ExpenseNotebookTheme(content: @Composable () -> Unit) {
    val darkTheme = isSystemInDarkTheme()
    CompositionLocalProvider(
        LocalFinanceColors provides if (darkTheme) DarkFinanceColors else LightFinanceColors
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}

fun categoryColor(categoryName: String): Color {
    val name = categoryName.lowercase()
    return when {
        "food" in name || "grocer" in name -> Color(0xFFF97316)
        "transport" in name || "fuel" in name || "travel" in name -> Color(0xFF0EA5E9)
        "shopping" in name || "gift" in name || "personal" in name -> Color(0xFFA855F7)
        "bill" in name || "utilities" in name || "rent" in name || "emi" in name ||
            "subscription" in name || "home" in name -> Color(0xFF6366F1)
        "entertainment" in name -> Color(0xFFEC4899)
        "health" in name || "medicine" in name -> Color(0xFF14B8A6)
        "education" in name -> Color(0xFFEAB308)
        "work" in name -> Color(0xFF16A34A)
        else -> Color(0xFF64748B)
    }
}
