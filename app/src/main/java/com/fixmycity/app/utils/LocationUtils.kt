package com.fixmycity.app.utils

import android.annotation.SuppressLint
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

data class CityCoordinate(
    val name: String,
    val stateOrCountry: String,
    val latitude: Double,
    val longitude: Double
)

object LocationUtils {

    // Curated list of popular Indian and international cities for quick visual map jumping
    val POPULAR_CITIES = listOf(
        CityCoordinate("Navi Mumbai", "Maharashtra", 19.0330, 73.0297),
        CityCoordinate("Mumbai", "Maharashtra", 19.0760, 72.8777),
        CityCoordinate("Thane", "Maharashtra", 19.2183, 72.9781),
        CityCoordinate("Pune", "Maharashtra", 18.5204, 73.8567),
        CityCoordinate("New Delhi", "Delhi", 28.6139, 77.2090),
        CityCoordinate("Bengaluru", "Karnataka", 12.9716, 77.5946),
        CityCoordinate("Hyderabad", "Telangana", 17.3850, 78.4867),
        CityCoordinate("Chennai", "Tamil Nadu", 13.0827, 80.2707),
        CityCoordinate("Kolkata", "West Bengal", 22.5726, 88.3639),
        CityCoordinate("Ahmedabad", "Gujarat", 23.0225, 72.5714),
        CityCoordinate("Jaipur", "Rajasthan", 26.9124, 75.7873),
        CityCoordinate("Surat", "Gujarat", 21.1702, 72.8311),
        CityCoordinate("Lucknow", "Uttar Pradesh", 26.8467, 80.9462),
        CityCoordinate("Nagpur", "Maharashtra", 21.1458, 79.0882),
        CityCoordinate("Indore", "Madhya Pradesh", 22.7196, 75.8577),
        CityCoordinate("Bhopal", "Madhya Pradesh", 23.2599, 77.4126),
        CityCoordinate("Chandigarh", "Punjab/Haryana", 30.7333, 76.7794),
        CityCoordinate("Coimbatore", "Tamil Nadu", 11.0168, 76.9558),
        CityCoordinate("Kochi", "Kerala", 9.9312, 76.2673),
        CityCoordinate("Goa (Panaji)", "Goa", 15.4909, 73.8278)
    )

    fun findCityByName(query: String): CityCoordinate? {
        val clean = query.trim().lowercase()
        return POPULAR_CITIES.firstOrNull {
            it.name.lowercase().contains(clean) || clean.contains(it.name.lowercase())
        }
    }

    /**
     * Retrieves the best known current location from GPS or Network provider.
     */
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(context: Context): Location? = withContext(Dispatchers.IO) {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return@withContext null

        var bestLocation: Location? = null

        try {
            // Check GPS first
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                val gpsLoc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                if (gpsLoc != null) bestLocation = gpsLoc
            }

            // Check Network if GPS is null or older
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                val netLoc = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                if (netLoc != null) {
                    if (bestLocation == null || netLoc.time > bestLocation.time) {
                        bestLocation = netLoc
                    }
                }
            }

            // Check Passive provider as fallback
            if (bestLocation == null && locationManager.isProviderEnabled(LocationManager.PASSIVE_PROVIDER)) {
                bestLocation = locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        bestLocation
    }

    /**
     * Converts latitude and longitude into a readable location string.
     */
    suspend fun reverseGeocode(context: Context, latitude: Double, longitude: Double): String = withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                return@withContext suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocation(latitude, longitude, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            val address = addresses.firstOrNull()
                            cont.resume(formatAddress(address, latitude, longitude))
                        }

                        override fun onError(errorMessage: String?) {
                            cont.resume(formatFallback(latitude, longitude))
                        }
                    })
                }
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                val address = addresses?.firstOrNull()
                return@withContext formatAddress(address, latitude, longitude)
            }
        } catch (e: Exception) {
            return@withContext formatFallback(latitude, longitude)
        }
    }

    /**
     * Geocodes a city name to latitude and longitude.
     */
    suspend fun geocodeCity(context: Context, cityName: String): CityCoordinate? = withContext(Dispatchers.IO) {
        // 1. Check local indexed list first for instantaneous match
        val localMatch = findCityByName(cityName)
        if (localMatch != null) return@withContext localMatch

        // 2. Try Android Geocoder
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            val results = geocoder.getFromLocationName(cityName, 1)
            val match = results?.firstOrNull()
            if (match != null) {
                val name = match.locality ?: match.subAdminArea ?: match.adminArea ?: cityName
                val state = match.adminArea ?: match.countryName ?: ""
                return@withContext CityCoordinate(name, state, match.latitude, match.longitude)
            }
        } catch (e: Exception) {
            // Geocoder unavailable or offline
        }

        null
    }

    private fun formatAddress(address: Address?, latitude: Double, longitude: Double): String {
        if (address == null) return formatFallback(latitude, longitude)

        val parts = mutableListOf<String>()
        address.thoroughfare?.let { parts.add(it) }
        address.subLocality?.let { parts.add(it) }
        address.locality?.let { parts.add(it) }
        address.adminArea?.let { parts.add(it) }

        return if (parts.isNotEmpty()) {
            parts.distinct().joinToString(", ")
        } else {
            address.getAddressLine(0) ?: formatFallback(latitude, longitude)
        }
    }

    fun formatFallback(latitude: Double, longitude: Double): String {
        val latDir = if (latitude >= 0) "N" else "S"
        val lngDir = if (longitude >= 0) "E" else "W"
        return String.format(Locale.ENGLISH, "%.4f° %s, %.4f° %s", Math.abs(latitude), latDir, Math.abs(longitude), lngDir)
    }
}
