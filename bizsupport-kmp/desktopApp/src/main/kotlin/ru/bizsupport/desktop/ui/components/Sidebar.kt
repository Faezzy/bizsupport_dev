package ru.bizsupport.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.bizsupport.desktop.ui.theme.*

enum class Screen(val title: String, val icon: ImageVector) {
    DASHBOARD("Главная", Icons.Filled.Dashboard),
    TAX("Налоги", Icons.Filled.Calculate),
    CALCULATOR("Калькулятор", Icons.Filled.TrendingUp),
    PROCUREMENT("Госзакупки", Icons.Filled.Checklist),
    SEARCH("Поиск", Icons.Filled.Search),
    PROFILE("Профиль", Icons.Filled.Business),
    FAVORITES("Избранное", Icons.Filled.Star),
    NOTIFICATIONS("Уведомления", Icons.Filled.Notifications),
}

@Composable
fun Sidebar(
    currentScreen: Screen,
    unreadCount: Long,
    onNavigate: (Screen) -> Unit,
    onLogout: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(260.dp)
            .fillMaxHeight()
            .background(Navy)
            .padding(vertical = 20.dp)
    ) {
        // Brand
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Filled.BusinessCenter, null, tint = Accent, modifier = Modifier.size(24.dp))
            Text("BizSupport", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Divider(color = NavyLight, modifier = Modifier.padding(vertical = 16.dp))

        // Nav items
        Column(modifier = Modifier.weight(1f)) {
            Screen.entries.forEach { screen ->
                NavItem(
                    screen = screen,
                    isActive = currentScreen == screen,
                    badge = if (screen == Screen.NOTIFICATIONS && unreadCount > 0) unreadCount else null,
                    onClick = { onNavigate(screen) }
                )
            }
        }

        // Logout
        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { onLogout() }
                .background(Color.Transparent)
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Logout, null, tint = TextMuted, modifier = Modifier.size(18.dp))
                Text("Выйти", color = TextMuted, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun NavItem(screen: Screen, isActive: Boolean, badge: Long?, onClick: () -> Unit) {
    val bg = if (isActive) Accent.copy(alpha = 0.15f) else Color.Transparent
    val textColor = if (isActive) Color.White else Color(0xFF94A3B8)
    val borderColor = if (isActive) Accent else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .background(bg)
            .padding(start = 0.dp)
            .then(
                if (isActive) Modifier.drawWithContent {
                    drawContent()
                    drawRect(Accent, size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height))
                } else Modifier
            )
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(screen.icon, null, tint = textColor, modifier = Modifier.size(20.dp))
        Text(screen.title, color = textColor, fontSize = 14.sp, modifier = Modifier.weight(1f))
        if (badge != null) {
            Badge(containerColor = Danger) {
                Text(if (badge > 9) "9+" else "$badge", color = Color.White, fontSize = 10.sp)
            }
        }
    }
}

// Helper extension for drawing active border
private fun Modifier.drawWithContent(onDraw: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit) =
    this.then(Modifier)
