package com.fixmycity.app.ui.screens.report

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fixmycity.app.data.repository.FixMyCityRepository
import com.fixmycity.app.utils.ServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class ReportUiState(
    val selectedImageUri: Uri? = null,
    val selectedCategory: String = "",
    val description: String = "",
    val location: String = "Vashi, Navi Mumbai",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val isLocating: Boolean = false,
    val locationMessage: String? = null,
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val descriptionError: String? = null,
    val categoryError: String? = null,
    val submissionError: String? = null
)

class ReportViewModel(
    private val repository: FixMyCityRepository = FixMyCityRepository.getInstance()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportUiState())
    val uiState: StateFlow<ReportUiState> = _uiState.asStateFlow()

    fun setImage(uri: Uri?) {
        _uiState.value = _uiState.value.copy(selectedImageUri = uri)
    }

    fun setCategory(category: String) {
        _uiState.value = _uiState.value.copy(selectedCategory = category, categoryError = null)
    }

    fun setDescription(desc: String) {
        if (desc.length <= 500) {
            _uiState.value = _uiState.value.copy(description = desc, descriptionError = null)
        }
    }

    fun setLocation(loc: String, lat: Double? = null, lng: Double? = null) {
        _uiState.value = _uiState.value.copy(
            location = loc,
            latitude = lat,
            longitude = lng,
            locationMessage = "Selected on Map"
        )
    }

    fun fetchCurrentGpsLocation(context: Context) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLocating = true, locationMessage = "Acquiring GPS location...")
            try {
                val loc = com.fixmycity.app.utils.LocationUtils.getCurrentLocation(context)
                if (loc != null) {
                    val address = com.fixmycity.app.utils.LocationUtils.reverseGeocode(context, loc.latitude, loc.longitude)
                    _uiState.value = _uiState.value.copy(
                        location = address,
                        latitude = loc.latitude,
                        longitude = loc.longitude,
                        isLocating = false,
                        locationMessage = "GPS Location Confirmed"
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLocating = false,
                        locationMessage = "GPS fix unavailable. Select city on visual map."
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLocating = false,
                    locationMessage = "Could not fetch GPS: ${e.message}"
                )
            }
        }
    }

    fun submitReport(context: Context, onSuccess: () -> Unit) {
        val state = _uiState.value
        var hasError = false
        if (state.selectedCategory.isBlank()) {
            _uiState.value = _uiState.value.copy(categoryError = "Please select a category")
            hasError = true
        }
        if (state.description.isBlank()) {
            _uiState.value = _uiState.value.copy(descriptionError = "Please describe the issue")
            hasError = true
        }
        if (hasError) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, submissionError = null)

            // Upload selected image directly to local laptop server, or fallback to server stock image
            var finalImageUrl: String? = null
            if (state.selectedImageUri != null) {
                finalImageUrl = withContext(Dispatchers.IO) {
                    try {
                        val tempFile = File(context.cacheDir, "upload_${System.currentTimeMillis()}.jpg")
                        context.contentResolver.openInputStream(state.selectedImageUri!!)?.use { input ->
                            tempFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }

                        // Upload to laptop PC server
                        val uploadedUrl = ServerConfig.uploadImage(context, tempFile)
                        tempFile.delete()
                        uploadedUrl
                    } catch (e: Exception) {
                        e.printStackTrace()
                        null
                    }
                }
            }

            // If user did not pick an image or if upload failed, assign category stock image from PC server
            if (finalImageUrl.isNullOrBlank()) {
                finalImageUrl = ServerConfig.getCategoryImageUrl(context, state.selectedCategory)
            }

            val result = repository.createReport(
                category = state.selectedCategory,
                description = state.description,
                location = state.location,
                title = "${state.selectedCategory} Issue",
                imageUrl = finalImageUrl,
                latitude = state.latitude,
                longitude = state.longitude
            )

            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(isLoading = false, isSuccess = true)
                onSuccess()
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    submissionError = result.exceptionOrNull()?.message ?: "Submission failed"
                )
            }
        }
    }

    fun reset() {
        _uiState.value = ReportUiState()
    }

    fun setInitialCategory(category: String?) {
        if (!category.isNullOrBlank()) {
            _uiState.value = _uiState.value.copy(selectedCategory = category)
        }
    }
}
