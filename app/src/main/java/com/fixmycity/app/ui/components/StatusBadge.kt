package com.fixmycity.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fixmycity.app.data.model.ReportStatus
import com.fixmycity.app.ui.theme.*

fun ReportStatus.displayName(): String = when (this) {
    ReportStatus.SUBMITTED    -> "Submitted"
    ReportStatus.UNDER_REVIEW -> "Under Review"
    ReportStatus.ASSIGNED     -> "Assigned"
    ReportStatus.IN_PROGRESS  -> "In Progress"
    ReportStatus.RESOLVED     -> "Resolved"
    ReportStatus.REJECTED     -> "Rejected"
}

@Composable
fun StatusBadge(status: ReportStatus) {
    val (bg, fg) = when (status) {
        ReportStatus.SUBMITTED    -> StatusSubmittedBg    to StatusSubmittedText
        ReportStatus.UNDER_REVIEW -> StatusUnderReviewBg  to StatusUnderReviewText
        ReportStatus.ASSIGNED     -> PrimaryLight          to PrimaryDark
        ReportStatus.IN_PROGRESS  -> StatusInProgressBg   to StatusInProgressText
        ReportStatus.RESOLVED     -> StatusResolvedBg     to StatusResolvedText
        ReportStatus.REJECTED     -> StatusRejectedBg     to StatusRejectedText
    }

    Text(
        text = status.displayName(),
        color = fg,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}
