package com.fixmycity.app.ui.screens.report

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.fixmycity.app.ui.components.AppButton
import com.fixmycity.app.ui.theme.*
import com.fixmycity.app.utils.LocationUtils
import kotlinx.coroutines.launch
import kotlin.math.*

enum class MapLayerType(val label: String, val shortName: String) {
    STREET("🗺️ Street", "Street"),
    SATELLITE("🛰️ Satellite", "Satellite"),
    URBAN("🏙️ Urban Map", "Urban")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisualMapPickerDialog(
    initialLocation: String,
    onDismiss: () -> Unit,
    onLocationSelected: (locationName: String, latitude: Double, longitude: Double) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    // Coordinates state (Default to Navi Mumbai or initial location)
    var currentLat by remember { mutableDoubleStateOf(19.0330) }
    var currentLng by remember { mutableDoubleStateOf(73.0297) }
    var zoomLevel by remember { mutableIntStateOf(15) }
    var selectedLayer by remember { mutableStateOf(MapLayerType.STREET) }
    var selectedAddress by remember { mutableStateOf(initialLocation.ifBlank { "Navi Mumbai, Maharashtra" }) }
    var searchQuery by remember { mutableStateOf("") }
    var isGeocoding by remember { mutableStateOf(false) }

    // Try initial geocode if initialLocation has a known city
    LaunchedEffect(Unit) {
        val matchedCity = LocationUtils.geocodeCity(context, initialLocation)
        if (matchedCity != null) {
            currentLat = matchedCity.latitude
            currentLng = matchedCity.longitude
            selectedAddress = "${matchedCity.name}, ${matchedCity.stateOrCountry}"
        }
    }

    fun jumpToCoordinates(lat: Double, lng: Double, address: String) {
        currentLat = lat
        currentLng = lng
        selectedAddress = address
    }

    fun searchAndMove(query: String) {
        if (query.isBlank()) return
        focusManager.clearFocus()
        scope.launch {
            isGeocoding = true
            val city = LocationUtils.geocodeCity(context, query)
            isGeocoding = false
            if (city != null) {
                jumpToCoordinates(city.latitude, city.longitude, "${city.name}, ${city.stateOrCountry}")
            } else {
                selectedAddress = query.trim()
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.94f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Map, contentDescription = null, tint = Primary, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Interactive Visual Map", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Search city or drag map to place pin", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Search City Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search city (e.g. Pune, Delhi, Mumbai)...", fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = Primary)
                    },
                    trailingIcon = {
                        if (isGeocoding) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchAndMove(searchQuery) }) {
                                Icon(Icons.Default.ArrowForward, contentDescription = "Search", tint = Primary)
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { searchAndMove(searchQuery) }),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Primary,
                        unfocusedBorderColor = Border
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                // Quick City Suggestion Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val quickCities = listOf(
                        "Navi Mumbai", "Mumbai", "Pune", "New Delhi",
                        "Bengaluru", "Hyderabad", "Kolkata", "Chennai", "Jaipur", "Ahmedabad"
                    )
                    quickCities.forEach { cityName ->
                        val isSelected = selectedAddress.contains(cityName, ignoreCase = true)
                        SuggestionChip(
                            onClick = {
                                searchQuery = cityName
                                searchAndMove(cityName)
                            },
                            label = { Text(cityName, fontSize = 12.sp) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = if (isSelected) Primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                                labelColor = if (isSelected) Primary else MaterialTheme.colorScheme.onSurface
                            ),
                            border = SuggestionChipDefaults.suggestionChipBorder(
                                enabled = true,
                                borderColor = if (isSelected) Primary else Border
                            )
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                // ── Interactive Visual Map View (OpenStreetMap / Esri Tiles + Pin Overlay) ──
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .border(1.5.dp, Border, RoundedCornerShape(18.dp))
                ) {
                    val density = LocalDensity.current

                    // Map Tiles Grid
                    MapTilesGrid(
                        latitude = currentLat,
                        longitude = currentLng,
                        zoom = zoomLevel,
                        layerType = selectedLayer,
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                val tileSizePx = with(density) { 256.dp.toPx() }

                                detectDragGestures(
                                    onDragEnd = {
                                        scope.launch {
                                            val addr = LocationUtils.reverseGeocode(context, currentLat, currentLng)
                                            selectedAddress = addr
                                        }
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()

                                        val n = 1 shl zoomLevel
                                        // Dragging shifts longitude
                                        val dLng = -dragAmount.x / tileSizePx * (360.0 / n)
                                        currentLng = (currentLng + dLng).coerceIn(-180.0, 180.0)

                                        // Exact Mercator latitude translation
                                        val latRad = Math.toRadians(currentLat.coerceIn(-85.0, 85.0))
                                        val yExact = ((1.0 - asinh(tan(latRad)) / Math.PI) / 2.0 * n)
                                        val dyFraction = dragAmount.y / tileSizePx
                                        val newYExact = (yExact - dyFraction).coerceIn(0.001, n.toDouble() - 0.001)
                                        val sinhVal = sinh(Math.PI * (1.0 - 2.0 * newYExact / n))
                                        currentLat = Math.toDegrees(atan(sinhVal)).coerceIn(-85.0, 85.0)
                                    }
                                )
                            }
                            .pointerInput(Unit) {
                                val tileSizePx = with(density) { 256.dp.toPx() }

                                detectTapGestures { offset ->
                                    val n = 1 shl zoomLevel
                                    val centerX = size.width / 2f
                                    val centerY = size.height / 2f
                                    val dx = offset.x - centerX
                                    val dy = offset.y - centerY

                                    val dLng = dx / tileSizePx * (360.0 / n)
                                    currentLng = (currentLng + dLng).coerceIn(-180.0, 180.0)

                                    val latRad = Math.toRadians(currentLat.coerceIn(-85.0, 85.0))
                                    val yExact = ((1.0 - asinh(tan(latRad)) / Math.PI) / 2.0 * n)
                                    val dyFraction = -dy / tileSizePx
                                    val newYExact = (yExact - dyFraction).coerceIn(0.001, n.toDouble() - 0.001)
                                    val sinhVal = sinh(Math.PI * (1.0 - 2.0 * newYExact / n))
                                    currentLat = Math.toDegrees(atan(sinhVal)).coerceIn(-85.0, 85.0)

                                    scope.launch {
                                        val addr = LocationUtils.reverseGeocode(context, currentLat, currentLng)
                                        selectedAddress = addr
                                    }
                                }
                            }
                    )

                    // Layer Switcher (Top Left)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        shadowElevation = 3.dp,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            MapLayerType.values().forEach { layer ->
                                val isSelected = selectedLayer == layer
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) Primary else Color.Transparent)
                                        .clickable { selectedLayer = layer }
                                        .padding(horizontal = 8.dp, vertical = 5.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = layer.shortName,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    // Top Map HUD Header (shows active City / Area)
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.Black.copy(alpha = 0.65f),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.LocationCity, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = selectedAddress.substringBefore(","),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("• ${zoomLevel}x", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
                        }
                    }

                    // Center Location Pin with animated pulsing halo
                    CenterPinMarker()

                    // Floating Map Controls: Zoom in (+), Zoom out (-), Recenter
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Zoom In (+)
                        SmallFloatingActionButton(
                            onClick = { if (zoomLevel < 18) zoomLevel += 1 },
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = Primary,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Zoom In")
                        }

                        // Zoom Out (-)
                        SmallFloatingActionButton(
                            onClick = { if (zoomLevel > 10) zoomLevel -= 1 },
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = Primary,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Zoom Out")
                        }

                        // Reverse-geocode & Recenter
                        SmallFloatingActionButton(
                            onClick = {
                                scope.launch {
                                    val addr = LocationUtils.reverseGeocode(context, currentLat, currentLng)
                                    selectedAddress = addr
                                }
                            },
                            containerColor = Primary,
                            contentColor = Color.White,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(Icons.Default.MyLocation, contentDescription = "Pin Address")
                        }
                    }

                    // Bottom helper notice
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.White.copy(alpha = 0.88f),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(10.dp)
                    ) {
                        Text(
                            "👆 Drag map or tap to move pin",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = PrimaryDark,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Selected address confirmation card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = PrimaryLight.copy(alpha = 0.45f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = selectedAddress,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = PrimaryDark,
                                maxLines = 2
                            )
                            Text(
                                text = LocationUtils.formatFallback(currentLat, currentLng),
                                fontSize = 11.sp,
                                color = Primary.copy(alpha = 0.8f)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Action Buttons: Cancel and Confirm
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancel")
                    }

                    AppButton(
                        text = "Confirm Location",
                        onClick = {
                            onLocationSelected(selectedAddress, currentLat, currentLng)
                            onDismiss()
                        },
                        modifier = Modifier
                            .weight(1.5f)
                            .height(48.dp)
                    )
                }
            }
        }
    }
}

/**
 * Renders real OpenStreetMap / Esri map tiles positioned with sub-pixel Mercator accuracy.
 * Completely free, zero watermarks, and NO API key required.
 */
@Composable
internal fun MapTilesGrid(
    latitude: Double,
    longitude: Double,
    zoom: Int,
    layerType: MapLayerType,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val tileSizePx = with(density) { 256.dp.toPx() }
        val boxWidthPx = constraints.maxWidth.toFloat()
        val boxHeightPx = constraints.maxHeight.toFloat()
        val n = 1 shl zoom

        val xExact = ((longitude + 180.0) / 360.0 * n)
        val latRad = Math.toRadians(latitude.coerceIn(-85.0, 85.0))
        val yExact = ((1.0 - asinh(tan(latRad)) / Math.PI) / 2.0 * n)

        val centerTileX = floor(xExact).toInt()
        val centerTileY = floor(yExact).toInt()
        val fracX = (xExact - centerTileX).toFloat()
        val fracY = (yExact - centerTileY).toFloat()

        // Center of screen corresponds to exact (latitude, longitude)
        val originX = boxWidthPx / 2f - fracX * tileSizePx
        val originY = boxHeightPx / 2f - fracY * tileSizePx

        val minDx = floor(-originX / tileSizePx).toInt() - 1
        val maxDx = ceil((boxWidthPx - originX) / tileSizePx).toInt() + 1
        val minDy = floor(-originY / tileSizePx).toInt() - 1
        val maxDy = ceil((boxHeightPx - originY) / tileSizePx).toInt() + 1

        val baseBgColor = when (layerType) {
            MapLayerType.SATELLITE -> Color(0xFF0F172A)
            MapLayerType.URBAN -> Color(0xFFE2E8F0)
            MapLayerType.STREET -> Color(0xFFF2EFE9)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(baseBgColor)
        ) {
            // Render tiles covering the visible area
            for (dy in minDy..maxDy) {
                val tileY = centerTileY + dy
                if (tileY in 0 until n) {
                    for (dx in minDx..maxDx) {
                        val tileX = (centerTileX + dx).mod(n)
                        val tileLeftPx = originX + dx * tileSizePx
                        val tileTopPx = originY + dy * tileSizePx

                        // 100% Free, NO API Key tile providers:
                        // OpenStreetMap: tile.openstreetmap.org (Free community standard)
                        // Esri World Imagery: server.arcgisonline.com (Free public satellite imagery)
                        // Esri World Street: server.arcgisonline.com (Free public high-res street map)
                        val tileUrl = when (layerType) {
                            MapLayerType.STREET -> "https://tile.openstreetmap.org/$zoom/$tileX/$tileY.png"
                            MapLayerType.SATELLITE -> "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/$zoom/$tileY/$tileX"
                            MapLayerType.URBAN -> "https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile/$zoom/$tileY/$tileX"
                        }

                        key("$zoom-$tileX-$tileY") {
                            val request = remember(tileUrl) {
                                ImageRequest.Builder(context)
                                    .data(tileUrl)
                                    .addHeader("User-Agent", "FixMyCityApp/1.0 (Android; civic-service; support@fixmycity.org)")
                                    .crossfade(false)
                                    .build()
                            }

                            AsyncImage(
                                model = request,
                                contentDescription = null,
                                contentScale = ContentScale.FillBounds,
                                modifier = Modifier
                                    .offset { IntOffset(tileLeftPx.roundToInt(), tileTopPx.roundToInt()) }
                                    .size(with(density) { tileSizePx.toDp() })
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Animated Pin Marker centered on the map with glowing pulse halo.
 */
@Composable
private fun BoxScope.CenterPinMarker() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Column(
        modifier = Modifier.align(Alignment.Center),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Pin icon
        Box(contentAlignment = Alignment.Center) {
            // Pulse ring
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(Primary.copy(alpha = pulseAlpha))
            )

            // Pin Header
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .shadow(6.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Primary)
                    .border(2.5.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = "Pin",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Pin shadow dot on ground
        Box(
            modifier = Modifier
                .width(10.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.3f))
        )
    }
}
