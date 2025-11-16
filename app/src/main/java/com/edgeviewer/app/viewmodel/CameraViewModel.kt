package com.edgeviewer.app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

enum class CameraState {
    IDLE,
    INITIALIZING,
    READY,
    RUNNING,
    ERROR,
    PERMISSION_DENIED
}

enum class ViewMode {
    RAW,
    PROCESSED
}

enum class FilterMode {
    NONE,
    INVERT,
    THRESHOLD
}

data class AppState(
    val cameraState: CameraState = CameraState.IDLE,
    val viewMode: ViewMode = ViewMode.RAW,
    val filterMode: FilterMode = FilterMode.NONE,
    val fps: Double = 0.0,
    val errorMessage: String? = null,
    val isProcessing: Boolean = false
)

class CameraViewModel : ViewModel() {

    private val _appState = MutableLiveData<AppState>(AppState())
    val appState: LiveData<AppState> = _appState

    fun updateCameraState(state: CameraState) {
        viewModelScope.launch {
            val current = _appState.value ?: AppState()
            _appState.value = current.copy(
                cameraState = state,
                errorMessage = if (state == CameraState.ERROR) null else current.errorMessage
            )
        }
    }

    fun setError(message: String) {
        viewModelScope.launch {
            val current = _appState.value ?: AppState()
            _appState.value = current.copy(
                cameraState = CameraState.ERROR,
                errorMessage = message
            )
        }
    }

    fun clearError() {
        viewModelScope.launch {
            val current = _appState.value ?: AppState()
            _appState.value = current.copy(
                errorMessage = null,
                cameraState = if (current.cameraState == CameraState.ERROR) CameraState.IDLE else current.cameraState
            )
        }
    }

    fun toggleViewMode() {
        viewModelScope.launch {
            val current = _appState.value ?: AppState()
            _appState.value = current.copy(
                viewMode = if (current.viewMode == ViewMode.PROCESSED) ViewMode.RAW else ViewMode.PROCESSED
            )
        }
    }

    fun setViewMode(mode: ViewMode) {
        viewModelScope.launch {
            val current = _appState.value ?: AppState()
            _appState.value = current.copy(viewMode = mode)
        }
    }

    fun nextFilter() {
        viewModelScope.launch {
            val current = _appState.value ?: AppState()
            val nextFilter = when (current.filterMode) {
                FilterMode.NONE -> FilterMode.INVERT
                FilterMode.INVERT -> FilterMode.THRESHOLD
                FilterMode.THRESHOLD -> FilterMode.NONE
            }
            _appState.value = current.copy(filterMode = nextFilter)
        }
    }

    fun updateFps(fps: Double) {
        viewModelScope.launch {
            val current = _appState.value ?: AppState()
            _appState.value = current.copy(fps = fps)
        }
    }

    fun setProcessing(processing: Boolean) {
        viewModelScope.launch {
            val current = _appState.value ?: AppState()
            _appState.value = current.copy(isProcessing = processing)
        }
    }

    override fun onCleared() {
        super.onCleared()
        Timber.d("ViewModel cleared")
    }
}

