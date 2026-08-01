package com.example.weatherlocalapp

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.weatherlocalapp.data.*
import com.example.weatherlocalapp.manager.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

sealed interface WeatherUiState {
    object Loading : WeatherUiState
    data class Success(val forecast: List<ForecastResponse>, val warning: WarningResponse) : WeatherUiState
    data class Error(val message: String) : WeatherUiState
}

sealed interface VoiceVoxUiState {
    object Idle : VoiceVoxUiState
    object Loading : VoiceVoxUiState
    data class Success(val uri: Uri, val fileName: String) : VoiceVoxUiState
    data class Error(val message: String) : VoiceVoxUiState
}

sealed interface UpdateUiState {
    object Idle : UpdateUiState
    object Checking : UpdateUiState
    data class UpdateAvailable(val info: VersionInfo) : UpdateUiState
    data class Downloading(val progress: Float) : UpdateUiState
    data class Downloaded(val file: File) : UpdateUiState
    data class Error(val message: String) : UpdateUiState
    object UpToDate : UpdateUiState
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val timelineDao = db.timelineDao()
    private val weatherRepository = WeatherRepository()
    private val voiceVoxManager = VoiceVoxManager()
    private val appUpdater = AppUpdater()

    private val prefs = application.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    // SharedPreferences State
    private val _selectedAreaCode = MutableStateFlow(prefs.getString("selected_area_code", "130000") ?: "130000")
    val selectedAreaCode: StateFlow<String> = _selectedAreaCode.asStateFlow()

    // Room DB Timeline Flow
    val timelineList: StateFlow<List<TimelineEntity>> = timelineDao.getAllTimelines()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Weather Fetching UI State
    private val _weatherUiState = MutableStateFlow<WeatherUiState>(WeatherUiState.Loading)
    val weatherUiState: StateFlow<WeatherUiState> = _weatherUiState.asStateFlow()

    // VOICEVOX Synthesis UI State
    private val _voiceVoxUiState = MutableStateFlow<VoiceVoxUiState>(VoiceVoxUiState.Idle)
    val voiceVoxUiState: StateFlow<VoiceVoxUiState> = _voiceVoxUiState.asStateFlow()

    // App Updater State
    private val _updateUiState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val updateUiState: StateFlow<UpdateUiState> = _updateUiState.asStateFlow()

    // VOICEVOX Speakers Preset
    val voiceVoxSpeakers: List<VoiceVoxSpeaker> = voiceVoxManager.speakers

    // Basetime for Map radar
    private val _radarBasetime = MutableStateFlow<String?>(null)
    val radarBasetime: StateFlow<String?> = _radarBasetime.asStateFlow()

    init {
        fetchWeatherInfo(_selectedAreaCode.value)
        fetchRadarBasetime()
    }

    // --- Weather Actions ---
    fun updateAreaCode(areaCode: String) {
        viewModelScope.launch {
            _selectedAreaCode.value = areaCode
            prefs.edit().putString("selected_area_code", areaCode).apply()
            fetchWeatherInfo(areaCode)
        }
    }

    fun fetchWeatherInfo(areaCode: String) {
        viewModelScope.launch {
            _weatherUiState.value = WeatherUiState.Loading
            
            val forecastResult = weatherRepository.getForecast(areaCode)
            val warningResult = weatherRepository.getWarning(areaCode)

            if (forecastResult.isSuccess && warningResult.isSuccess) {
                _weatherUiState.value = WeatherUiState.Success(
                    forecastResult.getOrThrow(),
                    warningResult.getOrThrow()
                )
            } else {
                val errorMsg = forecastResult.exceptionOrNull()?.message 
                    ?: warningResult.exceptionOrNull()?.message 
                    ?: "気象データの取得に失敗しました。"
                _weatherUiState.value = WeatherUiState.Error(errorMsg)
            }
        }
    }

    fun fetchRadarBasetime() {
        viewModelScope.launch {
            weatherRepository.getRadarBasetime().onSuccess { response ->
                _radarBasetime.value = response.basetime
            }
        }
    }

    // --- Timeline Database Actions ---
    fun addTimeline(title: String, description: String, timeMinutes: Int, name: String, phone: String) {
        viewModelScope.launch {
            timelineDao.insertTimeline(
                TimelineEntity(
                    title = title,
                    description = description,
                    timeMinutes = timeMinutes,
                    contactName = name,
                    contactPhone = phone
                )
            )
        }
    }

    fun deleteTimeline(entity: TimelineEntity) {
        viewModelScope.launch {
            timelineDao.deleteTimeline(entity)
        }
    }

    // --- VOICEVOX Actions ---
    fun synthesizeVoice(text: String, speakerId: Int, hostIp: String) {
        viewModelScope.launch {
            _voiceVoxUiState.value = VoiceVoxUiState.Loading
            
            voiceVoxManager.synthesizeVoice(text, speakerId, hostIp)
                .onSuccess { bytes ->
                    val fileName = "voicevox_${System.currentTimeMillis()}.wav"
                    voiceVoxManager.saveWavFile(getApplication(), bytes, fileName)
                        .onSuccess { uri ->
                            _voiceVoxUiState.value = VoiceVoxUiState.Success(uri, fileName)
                        }
                        .onFailure { error ->
                            _voiceVoxUiState.value = VoiceVoxUiState.Error("保存に失敗しました: ${error.message}")
                        }
                }
                .onFailure { error ->
                    _voiceVoxUiState.value = VoiceVoxUiState.Error("音声合成に失敗しました: ${error.message}")
                }
        }
    }

    fun shareWav(uri: Uri) {
        voiceVoxManager.shareWavFile(getApplication(), uri)
    }

    fun clearVoiceState() {
        _voiceVoxUiState.value = VoiceVoxUiState.Idle
    }

    // --- App Updater Actions ---
    fun checkForUpdates(currentVersionCode: Int, url: String) {
        viewModelScope.launch {
            _updateUiState.value = UpdateUiState.Checking
            appUpdater.checkForUpdates(currentVersionCode, url)
                .onSuccess { info ->
                    if (info != null) {
                        _updateUiState.value = UpdateUiState.UpdateAvailable(info)
                    } else {
                        _updateUiState.value = UpdateUiState.UpToDate
                    }
                }
                .onFailure { error ->
                    _updateUiState.value = UpdateUiState.Error("アップデートの確認に失敗しました: ${error.message}")
                }
        }
    }

    fun downloadAndInstallUpdate(info: VersionInfo) {
        viewModelScope.launch {
            _updateUiState.value = UpdateUiState.Downloading(0f)
            appUpdater.downloadApk(getApplication(), info.apkUrl) { progress ->
                _updateUiState.value = UpdateUiState.Downloading(progress)
            }
                .onSuccess { apkFile ->
                    _updateUiState.value = UpdateUiState.Downloaded(apkFile)
                    installApk(apkFile)
                }
                .onFailure { error ->
                    _updateUiState.value = UpdateUiState.Error("APKのダウンロードに失敗しました: ${error.message}")
                }
        }
    }

    fun checkInstallPermission(context: Context): Boolean {
        return appUpdater.canInstallPackages(context)
    }

    fun requestInstallPermission(context: Context) {
        appUpdater.requestInstallPermission(context)
    }

    fun installApk(file: File) {
        appUpdater.installApk(getApplication(), file)
    }

    fun clearUpdateState() {
        _updateUiState.value = UpdateUiState.Idle
    }
}
