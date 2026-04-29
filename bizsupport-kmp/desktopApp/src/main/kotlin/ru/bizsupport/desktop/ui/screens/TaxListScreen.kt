package ru.bizsupport.desktop.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.bizsupport.desktop.ui.components.*
import ru.bizsupport.desktop.ui.theme.*
import ru.bizsupport.shared.model.TaxRegimeResponse

@Composable
fun TaxListScreen(
    regimes: List<TaxRegimeResponse>,
    isLoading: Boolean,
    error: String?,
    onRegimeClick: (String) -> Unit,
    onRetry: () -> Unit
) {
    if (isLoading) { LoadingIndicator(); return }
    if (error != null) { ErrorMessage(error, onRetry); return }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp)
    ) {
        PageHeader(
            title = "Налогообложение",
            subtitle = "Все налоговые режимы РФ для малого бизнеса"
        )

        regimes.forEach { regime ->
            Surface(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    .clickable { onRegimeClick(regime.code) },
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Border),
                color = CardBg
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Filled.Description, null, tint = Accent, modifier = Modifier.size(24.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(regime.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        if (regime.description != null) {
                            Text(
                                regime.description!!.take(100) + if (regime.description!!.length > 100) "..." else "",
                                fontSize = 13.sp, color = TextMuted
                            )
                        }
                    }
                    if (regime.nkRef != null) {
                        CustomBadge(regime.nkRef!!, color = TextMuted, bgColor = Color(0xFFF1F5F9))
                    }
                }
            }
        }
    }
}
