package ru.bizsupport.desktop.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.bizsupport.desktop.ui.components.*
import ru.bizsupport.desktop.ui.theme.*
import ru.bizsupport.shared.model.DashboardResponse

@Composable
fun DashboardScreen(
    dashboard: DashboardResponse?,
    isLoading: Boolean,
    error: String?,
    onNavigate: (Screen) -> Unit,
    onRetry: () -> Unit
) {
    if (isLoading) { LoadingIndicator(); return }
    if (error != null) { ErrorMessage(error, onRetry); return }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp)
    ) {
        PageHeader(
            title = "Добро пожаловать, ${dashboard?.user?.fullName ?: "Пользователь"}!",
            subtitle = "Ваш персональный помощник по налогам и госзакупкам"
        )

        // Stat cards
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            StatCard(
                label = "КОМПАНИЯ",
                value = dashboard?.profile?.companyName ?: "Не указана",
                subtitle = dashboard?.profile?.companyTypeDisplay,
                accentColor = Accent,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                label = "НАЛОГОВЫЙ РЕЖИМ",
                value = dashboard?.currentRegimes?.firstOrNull()?.regimeName ?: "Не выбран",
                accentColor = Success,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                label = "ЧЕК-ЛИСТЫ",
                value = "${dashboard?.checklists?.size ?: 0}",
                subtitle = "активных чек-листов",
                accentColor = Info,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(32.dp))
        PageHeader(title = "Быстрый старт")

        // Action cards grid 2x2
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            ActionCard(
                title = "Калькулятор налогов",
                description = "Сравнить УСН 6%, 15%, ОСНО, ПСН, НПД",
                icon = Icons.Filled.TrendingUp,
                iconBgColor = AccentLight, iconColor = Accent,
                onClick = { onNavigate(Screen.CALCULATOR) },
                modifier = Modifier.weight(1f)
            )
            ActionCard(
                title = "Подобрать налоговый режим",
                description = "Опросник по параметрам компании",
                icon = Icons.Filled.CheckCircle,
                iconBgColor = SuccessLight, iconColor = Success,
                onClick = { onNavigate(Screen.TAX) },
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            ActionCard(
                title = "Чек-лист для госзакупки",
                description = "Пошаговая инструкция 44-ФЗ / 223-ФЗ",
                icon = Icons.Filled.Checklist,
                iconBgColor = WarningLight, iconColor = Warning,
                onClick = { onNavigate(Screen.PROCUREMENT) },
                modifier = Modifier.weight(1f)
            )
            ActionCard(
                title = "Поиск по базе знаний",
                description = "Налоговые режимы и сценарии закупок",
                icon = Icons.Filled.Search,
                iconBgColor = InfoLight, iconColor = Info,
                onClick = { onNavigate(Screen.SEARCH) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}
