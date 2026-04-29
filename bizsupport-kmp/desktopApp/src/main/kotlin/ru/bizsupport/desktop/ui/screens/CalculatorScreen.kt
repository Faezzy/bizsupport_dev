package ru.bizsupport.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.bizsupport.desktop.ui.components.*
import ru.bizsupport.desktop.ui.theme.*
import ru.bizsupport.shared.model.TaxCalcResult

@Composable
fun CalculatorScreen(
    results: List<TaxCalcResult>,
    isLoading: Boolean,
    error: String?,
    onCalculate: (Double, Double, Int, Double, Boolean) -> Unit
) {
    var revenue by remember { mutableStateOf("3000000") }
    var expenses by remember { mutableStateOf("1500000") }
    var employees by remember { mutableStateOf("0") }
    var avgSalary by remember { mutableStateOf("50000") }
    var isIp by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp)
    ) {
        PageHeader(
            title = "Калькулятор налогов",
            subtitle = "Сравните налоговую нагрузку по всем доступным режимам"
        )

        // Input form
        Surface(shape = RoundedCornerShape(12.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Border), color = CardBg) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Параметры", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Spacer(Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = revenue, onValueChange = { revenue = it },
                        label = { Text("Годовой доход (₽)") },
                        modifier = Modifier.weight(1f), singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )
                    OutlinedTextField(
                        value = expenses, onValueChange = { expenses = it },
                        label = { Text("Годовые расходы (₽)") },
                        modifier = Modifier.weight(1f), singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )
                    OutlinedTextField(
                        value = employees, onValueChange = { employees = it },
                        label = { Text("Сотрудники") },
                        modifier = Modifier.weight(0.6f), singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = avgSalary, onValueChange = { avgSalary = it },
                        label = { Text("Ср. зарплата (₽)") },
                        modifier = Modifier.weight(1f), singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Text("Форма: ", color = TextMuted)
                        FilterChip(selected = isIp, onClick = { isIp = true }, label = { Text("ИП") })
                        Spacer(Modifier.width(8.dp))
                        FilterChip(selected = !isIp, onClick = { isIp = false }, label = { Text("ООО") })
                    }
                    Button(
                        onClick = {
                            onCalculate(
                                revenue.toDoubleOrNull() ?: 0.0,
                                expenses.toDoubleOrNull() ?: 0.0,
                                employees.toIntOrNull() ?: 0,
                                avgSalary.toDoubleOrNull() ?: 0.0,
                                isIp
                            )
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Accent),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text("Рассчитать", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        if (isLoading) { LoadingIndicator(); return }
        if (error != null) { ErrorMessage(error); return }

        if (results.isNotEmpty()) {
            Text("Результаты", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text("по возрастанию нагрузки", fontSize = 13.sp, color = TextMuted)
            Spacer(Modifier.height(16.dp))

            // Results grid 2 columns
            val chunked = results.chunked(2)
            chunked.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { result ->
                        ResultCard(result = result, modifier = Modifier.weight(1f))
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun ResultCard(result: TaxCalcResult, modifier: Modifier = Modifier) {
    val borderColor = if (result.best) Success else Border
    val borderWidth = if (result.best) 2.dp else 1.dp

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(borderWidth, borderColor),
        color = CardBg
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (result.best) {
                CustomBadge("Лучший", color = Color.White, bgColor = Success)
                Spacer(Modifier.height(8.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(result.regimeName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text("Эфф. ${result.effectiveRate}%", fontSize = 12.sp, color = TextMuted)
            }
            Spacer(Modifier.height(4.dp))

            Text(
                result.totalLoadFormatted ?: "${result.totalLoad.toLong()} ₽",
                fontSize = 22.sp, fontWeight = FontWeight.Bold
            )

            // Progress bar
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier.fillMaxWidth().height(6.dp)
                    .clip(RoundedCornerShape(3.dp)).background(Color(0xFFE2E8F0))
            ) {
                val width = (result.effectiveRate / 100.0).coerceIn(0.0, 1.0)
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(width.toFloat())
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (result.best) Success else Accent)
                )
            }

            Spacer(Modifier.height(12.dp))
            result.details.forEach { (key, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(key, fontSize = 12.sp, color = TextMuted)
                    Text(value, fontSize = 12.sp)
                }
            }
        }
    }
}
