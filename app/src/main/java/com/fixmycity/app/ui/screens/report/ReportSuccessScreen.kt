package com.fixmycity.app.ui.screens.report

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fixmycity.app.ui.components.AppButton
import com.fixmycity.app.ui.components.SecondaryButton
import com.fixmycity.app.ui.theme.Success
import com.fixmycity.app.ui.theme.TextPrimary
import com.fixmycity.app.ui.theme.TextSecondary

@Composable
fun ReportSuccessScreen(
    onGoHome: () -> Unit,
    onReportAnother: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Success",
                    modifier = Modifier.size(100.dp),
                    tint = Success
                )
                Text(
                    "Report Submitted!",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "Thank you for helping improve your city. Your report has been received and will be reviewed by the municipal team shortly.",
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                AppButton(text = "Go to Home", onClick = onGoHome)
                SecondaryButton(text = "Report Another Issue", onClick = onReportAnother)
            }
        }
    }
}
