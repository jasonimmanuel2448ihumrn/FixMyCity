package com.fixmycity.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.fixmycity.app.data.model.Report
import com.fixmycity.app.ui.theme.Primary
import com.fixmycity.app.ui.theme.TextSecondary
import com.fixmycity.app.utils.ServerConfig

private fun categoryIcon(categoryName: String): ImageVector = when (categoryName) {
    "Pothole"         -> Icons.Default.Warning
    "Garbage"         -> Icons.Default.Delete
    "Streetlight"     -> Icons.Default.LightMode
    "Water Leakage"   -> Icons.Default.WaterDrop
    "Drainage"        -> Icons.Default.InvertColors
    "Road Damage"     -> Icons.Default.Construction
    "Public Property" -> Icons.Default.AccountBalance
    else              -> Icons.Default.ReportProblem
}

@Composable
fun ReportCard(
    report: Report,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = categoryIcon(report.category),
                        contentDescription = report.category,
                        tint = Primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = report.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = report.category,
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
                StatusBadge(status = report.status)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = report.description,
                fontSize = 13.sp,
                color = TextSecondary,
                maxLines = 2
            )

            val context = LocalContext.current
            val imageModel = ServerConfig.resolveImageUrl(context, report.imageUrl, report.category)
            val fallbackRes = ServerConfig.getStockDrawableForCategory(report.category)

            Spacer(modifier = Modifier.height(12.dp))
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(imageModel)
                    .crossfade(true)
                    .placeholder(fallbackRes)
                    .error(fallbackRes)
                    .build(),
                contentDescription = report.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(12.dp))
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = report.location,
                    fontSize = 12.sp,
                    color = TextSecondary,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.CalendarToday,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = report.date,
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }
    }
}
