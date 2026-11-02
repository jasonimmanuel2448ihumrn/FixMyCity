package com.fixmycity.app.data.mock

import com.fixmycity.app.data.model.AppNotification
import com.fixmycity.app.data.model.Category
import com.fixmycity.app.data.model.NotificationType
import com.fixmycity.app.data.model.Report
import com.fixmycity.app.data.model.ReportStatus
import com.fixmycity.app.data.model.TimelineStep
import com.fixmycity.app.data.model.User
import com.fixmycity.app.domain.model.ImpactStats

/**
 * Offline/fallback mock data used when Firebase is unreachable.
 * After login these values are replaced by real Firestore data.
 */
object MockData {

    val mockUser = User(
        id = "user_1",
        name = "Rahul Sharma",
        email = "rahul.sharma@gmail.com",
        phone = "+91 98765 43210",
        role = "Citizen",
        reportsSubmitted = 12,
        reportsResolved = 8
    )

    val municipalOfficer = User(
        id = "municipal_officer_001",
        name = "Officer Rajesh Patil",
        email = "municipal@fixmycity.org",
        phone = "+91 22 2757 8400",
        role = "Municipal",
        reportsSubmitted = 0,
        reportsResolved = 42
    )

    val mockCitizenUsers: List<User> = listOf(
        mockUser,
        User(
            id = "user_2",
            name = "Priya Mehta",
            email = "priya.mehta@gmail.com",
            phone = "+91 98123 45678",
            role = "Citizen",
            reportsSubmitted = 6,
            reportsResolved = 5
        ),
        User(
            id = "user_3",
            name = "Amit Patil",
            email = "amit.patil@outlook.com",
            phone = "+91 98234 56789",
            role = "Citizen",
            reportsSubmitted = 4,
            reportsResolved = 3
        ),
        User(
            id = "user_4",
            name = "Ananya Desai",
            email = "ananya.d@gmail.com",
            phone = "+91 98345 67890",
            role = "Citizen",
            reportsSubmitted = 3,
            reportsResolved = 2
        )
    )

    val guestUser = User(
        id = "guest",
        name = "Guest",
        email = "guest@fixmycity.org",
        phone = "",
        role = "Guest",
        reportsSubmitted = 1,
        reportsResolved = 0
    )

    val mockReports: List<Report> = listOf(
        Report(
            id = "r1",
            title = "Deep Pothole on Sector 17 Main Road",
            description = "Severe pothole right in the middle lane causing dangerous slowdowns and vehicle rim damage. Needs urgent patch repair.",
            category = "Pothole",
            location = "Vashi Sector 17, Navi Mumbai",
            date = "Aug 24, 2026",
            status = ReportStatus.IN_PROGRESS,
            imageUrl = "/uploads/stock_pothole.jpg",
            userId = "user_1",
            userName = "Rahul Sharma",
            timelineSteps = listOf(
                TimelineStep(ReportStatus.SUBMITTED,    "Submitted",   "Aug 24, 2026", true),
                TimelineStep(ReportStatus.UNDER_REVIEW, "Under Review","Aug 25, 2026", true),
                TimelineStep(ReportStatus.ASSIGNED,     "Assigned",    "Aug 25, 2026", true),
                TimelineStep(ReportStatus.IN_PROGRESS,  "In Progress", "Aug 26, 2026", true),
                TimelineStep(ReportStatus.RESOLVED,     "Resolved",    null, false)
            )
        ),
        Report(
            id = "r2",
            title = "Overflowing Garbage Dump Near Market Yard",
            description = "Commercial waste and plastic bags piling up on pedestrian sidewalk for over 3 days. Strong odor and health hazard.",
            category = "Garbage",
            location = "Vashi Market Yard, Navi Mumbai",
            date = "Aug 22, 2026",
            status = ReportStatus.RESOLVED,
            imageUrl = "/uploads/stock_garbage.jpg",
            userId = "user_2",
            userName = "Priya Mehta",
            timelineSteps = listOf(
                TimelineStep(ReportStatus.SUBMITTED,    "Submitted",   "Aug 22, 2026", true),
                TimelineStep(ReportStatus.UNDER_REVIEW, "Under Review","Aug 23, 2026", true),
                TimelineStep(ReportStatus.ASSIGNED,     "Assigned",    "Aug 23, 2026", true),
                TimelineStep(ReportStatus.IN_PROGRESS,  "In Progress", "Aug 24, 2026", true),
                TimelineStep(ReportStatus.RESOLVED,     "Resolved",    "Aug 25, 2026", true)
            )
        ),
        Report(
            id = "r3",
            title = "Non-Functional Streetlight on Night Corridor",
            description = "Four consecutive light poles out of order along the residential stretch. Road is completely dark at night.",
            category = "Streetlight",
            location = "Sector 10 Ring Road, Navi Mumbai",
            date = "Aug 25, 2026",
            status = ReportStatus.UNDER_REVIEW,
            imageUrl = "/uploads/stock_streetlight.jpg",
            userId = "user_3",
            userName = "Amit Patil",
            timelineSteps = listOf(
                TimelineStep(ReportStatus.SUBMITTED,    "Submitted",   "Aug 25, 2026", true),
                TimelineStep(ReportStatus.UNDER_REVIEW, "Under Review","Aug 26, 2026", true),
                TimelineStep(ReportStatus.ASSIGNED,     "Assigned",    null, false),
                TimelineStep(ReportStatus.IN_PROGRESS,  "In Progress", null, false),
                TimelineStep(ReportStatus.RESOLVED,     "Resolved",    null, false)
            )
        ),
        Report(
            id = "r4",
            title = "High-Pressure Water Main Pipeline Burst",
            description = "Major drinking water pipeline damaged during digging work. Clean water flowing across the road continuously.",
            category = "Water Leakage",
            location = "Sector 5 Near Joggers Garden, Navi Mumbai",
            date = "Aug 26, 2026",
            status = ReportStatus.SUBMITTED,
            imageUrl = "/uploads/stock_water.jpg",
            userId = "user_4",
            userName = "Sneha Joshi",
            timelineSteps = listOf(
                TimelineStep(ReportStatus.SUBMITTED,    "Submitted",   "Aug 26, 2026", true),
                TimelineStep(ReportStatus.UNDER_REVIEW, "Under Review", null, false),
                TimelineStep(ReportStatus.ASSIGNED,     "Assigned",     null, false),
                TimelineStep(ReportStatus.IN_PROGRESS,  "In Progress",  null, false),
                TimelineStep(ReportStatus.RESOLVED,     "Resolved",     null, false)
            )
        ),
        Report(
            id = "r5",
            title = "Blocked Storm Drain & Heavy Waterlogging",
            description = "Culvert blocked with silt and debris causing ankle-deep water accumulation on service road after moderate rain.",
            category = "Drainage",
            location = "Palm Beach Road Service Lane, Navi Mumbai",
            date = "Aug 25, 2026",
            status = ReportStatus.IN_PROGRESS,
            imageUrl = "/uploads/stock_drainage.jpg",
            userId = "user_5",
            userName = "Vikram Deshmukh",
            timelineSteps = listOf(
                TimelineStep(ReportStatus.SUBMITTED,    "Submitted",   "Aug 25, 2026", true),
                TimelineStep(ReportStatus.UNDER_REVIEW, "Under Review","Aug 26, 2026", true),
                TimelineStep(ReportStatus.ASSIGNED,     "Assigned",    "Aug 26, 2026", true),
                TimelineStep(ReportStatus.IN_PROGRESS,  "In Progress", "Aug 27, 2026", true),
                TimelineStep(ReportStatus.RESOLVED,     "Resolved",    null, false)
            )
        ),
        Report(
            id = "r6",
            title = "Cracked Asphalt & Road Surface Subsidence",
            description = "Substantial depression on the roadway near intersection. Heavy vehicles experiencing loss of traction.",
            category = "Road Damage",
            location = "Station Road, Belapur, Navi Mumbai",
            date = "Aug 23, 2026",
            status = ReportStatus.UNDER_REVIEW,
            imageUrl = "/uploads/stock_road.jpg",
            userId = "user_6",
            userName = "Kavita Nair",
            timelineSteps = listOf(
                TimelineStep(ReportStatus.SUBMITTED,    "Submitted",   "Aug 23, 2026", true),
                TimelineStep(ReportStatus.UNDER_REVIEW, "Under Review","Aug 24, 2026", true),
                TimelineStep(ReportStatus.ASSIGNED,     "Assigned",    null, false),
                TimelineStep(ReportStatus.IN_PROGRESS,  "In Progress", null, false),
                TimelineStep(ReportStatus.RESOLVED,     "Resolved",    null, false)
            )
        ),
        Report(
            id = "r7",
            title = "Damaged Park Seating & Broken Children's Fence",
            description = "Public garden bench broken in half and metallic boundary fence dismantled. Poses hazard for small children.",
            category = "Public Property",
            location = "Central Park, Sector 20, Kharghar",
            date = "Aug 21, 2026",
            status = ReportStatus.RESOLVED,
            imageUrl = "/uploads/stock_public_property.jpg",
            userId = "user_7",
            userName = "Ananya Roy",
            timelineSteps = listOf(
                TimelineStep(ReportStatus.SUBMITTED,    "Submitted",   "Aug 21, 2026", true),
                TimelineStep(ReportStatus.UNDER_REVIEW, "Under Review","Aug 22, 2026", true),
                TimelineStep(ReportStatus.ASSIGNED,     "Assigned",    "Aug 22, 2026", true),
                TimelineStep(ReportStatus.IN_PROGRESS,  "In Progress", "Aug 23, 2026", true),
                TimelineStep(ReportStatus.RESOLVED,     "Resolved",    "Aug 24, 2026", true)
            )
        ),
        Report(
            id = "r8",
            title = "Fallen Traffic Sign Post Obstructing Walkway",
            description = "Metal directional board post bent down at 45 degree angle after storm, blocking the pedestrian sidewalk.",
            category = "Other",
            location = "Koparkhairane Sector 3 Junction, Navi Mumbai",
            date = "Aug 27, 2026",
            status = ReportStatus.SUBMITTED,
            imageUrl = "/uploads/stock_traffic.jpg",
            userId = "user_8",
            userName = "Devendra Singh",
            timelineSteps = listOf(
                TimelineStep(ReportStatus.SUBMITTED,    "Submitted",   "Aug 27, 2026", true),
                TimelineStep(ReportStatus.UNDER_REVIEW, "Under Review", null, false),
                TimelineStep(ReportStatus.ASSIGNED,     "Assigned",     null, false),
                TimelineStep(ReportStatus.IN_PROGRESS,  "In Progress",  null, false),
                TimelineStep(ReportStatus.RESOLVED,     "Resolved",     null, false)
            )
        )
    )

    val mockNotifications: List<AppNotification> = listOf(
        AppNotification(
            id = "n1",
            title = "Report Under Review",
            message = "Your pothole report on Main Road is now under review by the municipal team.",
            time = "2 hours ago",
            isRead = false,
            type = NotificationType.STATUS_UPDATE,
            userId = "user_1",
            createdAt = System.currentTimeMillis() - 7200000L
        ),
        AppNotification(
            id = "n2",
            title = "Issue Resolved!",
            message = "Great news! Your garbage report near Market Road has been resolved.",
            time = "1 day ago",
            isRead = false,
            type = NotificationType.RESOLVED,
            userId = "user_1",
            createdAt = System.currentTimeMillis() - 86400000L
        ),
        AppNotification(
            id = "n3",
            title = "New Issue Nearby",
            message = "A new water leakage issue has been reported near your area in Sector 5.",
            time = "2 days ago",
            isRead = true,
            type = NotificationType.NEW_ISSUE,
            userId = "user_1",
            createdAt = System.currentTimeMillis() - 172800000L
        )
    )

    val municipalAlerts: List<AppNotification> = listOf(
        AppNotification(
            id = "admin_notif_new_r1",
            title = "🚨 New Report: Deep Pothole on Main Road",
            message = "Pothole reported at Main Road, Sector 17, Vashi by Rahul Sharma.",
            time = "Aug 28, 2026",
            isRead = false,
            type = NotificationType.NEW_ISSUE,
            reportId = "r1",
            userId = "municipal_officer_001",
            createdAt = System.currentTimeMillis() - 7200000L
        ),
        AppNotification(
            id = "admin_notif_stat_r1",
            title = "🔄 In Progress: Deep Pothole on Main Road",
            message = "Status updated to In Progress. Field crew assigned for Sector 17, Vashi.",
            time = "Aug 28, 2026",
            isRead = false,
            type = NotificationType.STATUS_UPDATE,
            reportId = "r1",
            userId = "municipal_officer_001",
            createdAt = System.currentTimeMillis() - 3600000L
        ),
        AppNotification(
            id = "admin_notif_stat_r3",
            title = "✅ Resolved: Overflowing Garbage Bin",
            message = "Status updated to Resolved. Garbage bin cleared near Sector 9, Nerul.",
            time = "Aug 26, 2026",
            isRead = true,
            type = NotificationType.RESOLVED,
            reportId = "r3",
            userId = "municipal_officer_001",
            createdAt = System.currentTimeMillis() - 86400000L
        ),
        AppNotification(
            id = "admin_notif_new_r4",
            title = "🚨 New Report: Water Pipeline Leakage on Sidewalk",
            message = "Water Leakage reported at Plot 42, Sector 12, Kharghar by Sneha Kulkarni.",
            time = "Aug 26, 2026",
            isRead = false,
            type = NotificationType.NEW_ISSUE,
            reportId = "r4",
            userId = "municipal_officer_001",
            createdAt = System.currentTimeMillis() - 172800000L
        ),
        AppNotification(
            id = "admin_notif_stat_r5",
            title = "👷 Assigned: Clogged Storm Drain with Stagnant Water",
            message = "Status updated to Assigned. Drain desilting team dispatched for Belapur Station area.",
            time = "Aug 27, 2026",
            isRead = true,
            type = NotificationType.STATUS_UPDATE,
            reportId = "r5",
            userId = "municipal_officer_001",
            createdAt = System.currentTimeMillis() - 259200000L
        )
    )

    val mockCategories: List<Category> = listOf(
        Category(id = "cat1", name = "Pothole",         description = "Road potholes & craters",      iconName = "Road"),
        Category(id = "cat2", name = "Garbage",         description = "Waste & dumping issues",        iconName = "DeleteSweep"),
        Category(id = "cat3", name = "Streetlight",     description = "Lighting problems",             iconName = "LightMode"),
        Category(id = "cat4", name = "Water Leakage",   description = "Pipe leaks & bursts",           iconName = "WaterDrop"),
        Category(id = "cat5", name = "Drainage",        description = "Blocked drains & flooding",     iconName = "InvertColors"),
        Category(id = "cat6", name = "Road Damage",     description = "Road surface damage",           iconName = "Construction"),
        Category(id = "cat7", name = "Public Property", description = "Damaged public assets",         iconName = "AccountBalance"),
        Category(id = "cat8", name = "Other",           description = "Other civic issues",            iconName = "MoreHoriz")
    )

    val impactStats = ImpactStats(
        reportsSubmitted = 12,
        inProgress = 4,
        resolved = 8
    )

    val quickActionCategories: List<Category> = mockCategories.take(4)
}