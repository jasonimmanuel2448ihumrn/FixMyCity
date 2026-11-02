package com.fixmycity.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.fixmycity.app.data.model.AppNotification
import com.fixmycity.app.data.model.NotificationType
import com.fixmycity.app.ui.theme.*

private data class NotificationStyle(val color: Color, val icon: ImageVector)

private fun notificationStyle(type: NotificationType): NotificationStyle = when (type) {
    NotificationType.STATUS_UPDATE -> NotificationStyle(Info, Icons.Default.Info)
    NotificationType.RESOLVED      -> NotificationStyle(Success, Icons.Default.CheckCircle)
    NotificationType.NEW_ISSUE     -> NotificationStyle(Warning, Icons.Default.Warning)
    NotificationType.GENERAL       -> NotificationStyle(Primary, Icons.Default.NotificationsActive)
}

@Composable
fun NotificationCard(
    notification: AppNotification,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val style = notificationStyle(notification.type)
    val bg = if (!notification.isRead) PrimaryLight.copy(alpha = 0.3f)
             else MaterialTheme.colorScheme.surface

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon circle
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(style.color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = style.icon,
                contentDescription = null,
                tint = style.color,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Text column
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = notification.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = notification.message,
                fontSize = 13.sp,
                color = TextSecondary,
                maxLines = 2
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = notification.time,
                fontSize = 11.sp,
                color = TextSecondary
            )
        }

        // Unread dot
        if (!notification.isRead) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Info)
            )
        }
    }
}
