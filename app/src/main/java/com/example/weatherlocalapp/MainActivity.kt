package com.example.weatherlocalapp

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.weatherlocalapp.data.ForecastResponse
import com.example.weatherlocalapp.data.TimelineEntity
import com.example.weatherlocalapp.data.WarningResponse
import com.example.weatherlocalapp.worker.WeatherCheckWorker
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.style.layers.PropertyFactory
import com.mapbox.mapboxsdk.style.layers.RasterLayer
import com.mapbox.mapboxsdk.style.sources.RasterSource
import com.mapbox.mapboxsdk.style.sources.TileSet
import java.util.concurrent.TimeUnit

// --- Dark Theme Palette ---
private val DarkColorPalette = darkColorScheme(
    primary = Color(0xFF3B82F6),       // Electric Blue
    secondary = Color(0xFF10B981),     // Emerald Green
    background = Color(0xFF0F172A),    // Deep Space Blue
    surface = Color(0xFF1E293B),       // Dark Slate
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFFF1F5F9),  // Ice White
    onSurface = Color(0xFFE2E8F0),     // Soft Grey
    error = Color(0xFFEF4444)          // Alert Red
)

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: MainViewModel

    // Permission Launcher
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val postNotificationsGranted = permissions[Manifest.permission.POST_NOTIFICATIONS] ?: false
        val writeStorageGranted = permissions[Manifest.permission.WRITE_EXTERNAL_STORAGE] ?: false
        
        if (!postNotificationsGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(this, "警報通知を受け取るには通知権限が必要です。", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize MapLibre Native SDK (must be done before inflation)
        Mapbox.getInstance(this, null)

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        // Schedule Weather Monitoring background work
        scheduleBackgroundWeatherCheck()

        // Check/Request necessary permissions
        checkAndRequestPermissions()

        setContent {
            MaterialTheme(colorScheme = DarkColorPalette) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppMainScreen(viewModel)
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsNeeded = mutableListOf<String>()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        if (permissionsNeeded.isNotEmpty()) {
            requestPermissionsLauncher.launch(permissionsNeeded.toTypedArray())
        }
    }

    private fun scheduleBackgroundWeatherCheck() {
        val workRequest = PeriodicWorkRequestBuilder<WeatherCheckWorker>(15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "WeatherCheckWork",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}

// --- Main UI Frame with Navigation ---
@Composable
fun AppMainScreen(viewModel: MainViewModel) {
    var currentTab by remember { mutableStateOf(0) }
    val tabTitles = listOf("雨雲レーダー", "天気・警報", "タイムライン", "VOICEVOX")
    val tabIcons = listOf(
        Icons.Default.Map,
        Icons.Default.Cloud,
        Icons.Default.List,
        Icons.Default.VolumeUp
    )

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                tabTitles.forEachIndexed { index, title ->
                    NavigationBarItem(
                        icon = { Icon(tabIcons[index], contentDescription = title) },
                        label = { Text(title) },
                        selected = currentTab == index,
                        onClick = { currentTab = index },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (currentTab) {
                0 -> RadarMapScreen(viewModel)
                1 -> WeatherForecastScreen(viewModel)
                2 -> TimelineScreen(viewModel)
                3 -> VoiceVoxScreen(viewModel)
            }
            
            // Render Global Dialog overlays (e.g. Updater downloads)
            UpdateDialogOverlay(viewModel)
        }
    }
}

// --- TAB 1: MapLibre Rain Radar Screen ---
@Composable
fun RadarMapScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val basetime by viewModel.radarBasetime.collectAsState()
    
    val mapView = remember {
        MapView(context).apply {
            onCreate(null)
        }
    }
    
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
        }
    }

    var mapboxMapInstance by remember { mutableStateOf<MapboxMap?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
            update = { _ ->
                if (mapboxMapInstance == null) {
                    mapView.getMapAsync { map ->
                        mapboxMapInstance = map
                        map.setStyle("asset://map_style.json") { style ->
                            map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(35.6812, 139.7671), 5.0))
                            basetime?.let { time ->
                                updateRadarLayer(map, time)
                            }
                        }
                    }
                } else {
                    val map = mapboxMapInstance
                    if (map != null && map.style != null) {
                        basetime?.let { time ->
                            updateRadarLayer(map, time)
                        }
                    }
                }
            }
        )

        // Overlay control Card
        Card(
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.BottomCenter),
            colors = CardDefaults.cardColors(containerColor = Color(0xDD1E293B)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (basetime != null) "降水レーダー: ${formatBasetime(basetime!!)}" else "レーダーデータ取得中...",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        viewModel.fetchRadarBasetime()
                        Toast.makeText(context, "レーダー情報を更新しました", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "更新")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("レーダー更新")
                }
            }
        }
    }
}

private fun updateRadarLayer(map: MapboxMap, basetime: String) {
    val style = map.style ?: return
    if (style.getLayer("radar-layer") != null) {
        style.removeLayer("radar-layer")
    }
    if (style.getSource("radar-source") != null) {
        style.removeSource("radar-source")
    }
    val tileUrl = "https://www.jma.go.jp/bosai/jmaradar/data/nowcast/forecast/tile/$basetime/$basetime/{z}/{x}/{y}.png"
    val source = RasterSource("radar-source", TileSet("2.2.0", tileUrl), 256)
    style.addSource(source)
    val layer = RasterLayer("radar-layer", "radar-source").apply {
        setProperties(PropertyFactory.rasterOpacity(0.6f))
    }
    style.addLayer(layer)
}

fun formatBasetime(basetime: String): String {
    if (basetime.length < 12) return basetime
    val year = basetime.substring(0, 4)
    val month = basetime.substring(4, 6)
    val day = basetime.substring(6, 8)
    val hour = basetime.substring(8, 10)
    val min = basetime.substring(10, 12)
    return "${year}/${month}/${day} ${hour}:${min}"
}

// --- TAB 2: Weather & Warning Alerts Screen ---
@Composable
fun WeatherForecastScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val areaCode by viewModel.selectedAreaCode.collectAsState()
    val uiState by viewModel.weatherUiState.collectAsState()

    val areas = listOf(
        Pair("東京都", "130000"),
        Pair("大阪府", "270000"),
        Pair("愛知県", "230000"),
        Pair("福岡県", "400000"),
        Pair("北海道(石狩)", "016000"),
        Pair("沖縄県(本島)", "471000")
    )
    var showMenu by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Area Selector Header
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("現在選択中の地域", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                        Text(
                            text = areas.find { it.second == areaCode }?.first ?: "東京",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Box {
                        Button(onClick = { showMenu = true }) {
                            Text("地域変更")
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            areas.forEach { (name, code) ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        viewModel.updateAreaCode(code)
                                        showMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Render based on API Fetch result
        when (val state = uiState) {
            is WeatherUiState.Loading -> {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
            is WeatherUiState.Error -> {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0x33EF4444)),
                        border = BorderStroke(1.dp, Color.Red)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("エラーが発生しました", color = Color.Red, fontWeight = FontWeight.Bold)
                            Text(state.message, style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = { viewModel.fetchWeatherInfo(areaCode) }) {
                                Text("再試行")
                            }
                        }
                    }
                }
            }
            is WeatherUiState.Success -> {
                // 1. Warnings Section
                item {
                    val headline = state.warning.headline
                    if (!headline.isNullOrBlank()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0x44EF4444)),
                            border = BorderStroke(1.5.dp, Color(0xFFEF4444))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Warning, contentDescription = "警告", tint = Color(0xFFEF4444))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("現在発表中の警報・注意報", fontWeight = FontWeight.Bold, color = Color(0xFFEF4444))
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(headline, style = MaterialTheme.typography.bodyMedium, color = Color.White)
                            }
                        }
                    } else {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0x3310B981)),
                            border = BorderStroke(1.dp, Color(0xFF10B981))
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = "正常", tint = Color(0xFF10B981))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("現在、発表中の警報・特別警報はありません。", color = Color(0xFFE2E8F0))
                            }
                        }
                    }
                }

                // 2. Weather Forecast Card
                val forecast = state.forecast.firstOrNull()
                if (forecast != null) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "天気予報（${forecast.publishingOffice ?: "気象庁"}発表）",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    "発表時刻: ${forecast.reportDatetime ?: ""}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray
                                )
                                Spacer(modifier = Modifier.height(12.dp))

                                forecast.timeSeries?.firstOrNull()?.areas?.forEachIndexed { index, areaForecast ->
                                    Text(
                                        text = "・${areaForecast.area?.name ?: "対象地域"}",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    val weathers = areaForecast.weathers
                                    if (weathers != null) {
                                        weathers.forEachIndexed { dayIdx, weatherDesc ->
                                            Text(
                                                text = "　第${dayIdx + 1}期: $weatherDesc",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = Color.LightGray
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // App Self Updater Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("システム設定・更新", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("現在バージョン: 1.0.0 (Code: 1)", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            // Dummy raw JSON link containing version update structure for demo (normally hosting on release site)
                            viewModel.checkForUpdates(
                                currentVersionCode = 1,
                                url = "https://raw.githubusercontent.com/harukiy1225/WeatherLocalApp-MockRelease/main/version.json"
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Default.SystemUpdate, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("アップデート確認")
                    }
                }
            }
        }
    }
}

// --- TAB 3: Disaster Prevention Timeline Screen ---
@Composable
fun TimelineScreen(viewModel: MainViewModel) {
    val timelineItems by viewModel.timelineList.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "追加", tint = Color.White)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(
                "マイタイムライン (個人避難計画)",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "警戒レベルや時系列に合わせた個人の行動計画と連絡先を保存します。",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (timelineItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("計画が登録されていません。右下のボタンから登録してください。", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(timelineItems) { item ->
                        TimelineItemCard(item = item, onDelete = { viewModel.deleteTimeline(item) })
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        TimelineAddDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { title, desc, time, cName, cPhone ->
                viewModel.addTimeline(title, desc, time, cName, cPhone)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun TimelineItemCard(item: TimelineEntity, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (item.timeMinutes < 0) Color(0xFFF59E0B) else Color(0xFFEF4444),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (item.timeMinutes < 0) "${item.timeMinutes}分前" else "+${item.timeMinutes}分",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(item.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(item.description, style = MaterialTheme.typography.bodyMedium, color = Color.LightGray)
                
                if (item.contactName.isNotBlank() || item.contactPhone.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Divider(color = Color.DarkGray)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "緊急連絡先: ${item.contactName} (${item.contactPhone})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "削除", tint = Color.Gray)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineAddDialog(onDismiss: () -> Unit, onAdd: (String, String, Int, String, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var timeStr by remember { mutableStateOf("-30") }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("避難計画の追加") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("計画タイトル (例: 避難所へ移動)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("具体的な詳細・持ち物") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = timeStr,
                    onValueChange = { timeStr = it },
                    label = { Text("時間指定 (単位:分 負数は事前行動)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("緊急連絡先の氏名 (任意)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("緊急連絡先の電話番号 (任意)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val minutes = timeStr.toIntOrNull() ?: 0
                    if (title.isNotBlank()) {
                        onAdd(title, desc, minutes, name, phone)
                    }
                }
            ) {
                Text("追加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}

// --- TAB 4: VOICEVOX Speech Synthesis Screen ---
@Composable
fun VoiceVoxScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val state by viewModel.voiceVoxUiState.collectAsState()
    
    var inputText by remember { mutableStateOf("大雨警報が発表されました。ただちに避難計画を確認してください。") }
    var selectedSpeaker by remember { mutableStateOf(viewModel.voiceVoxSpeakers.first()) }
    var hostIp by remember { mutableStateOf("127.0.0.1") }
    var showSpeakerMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "VOICEVOX 音声合成・共有",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            "ローカルのVOICEVOXエンジンから音声を生成し、動画編集用WAVとして保存・共有します。",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )

        // Configuration Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Host IP Field (Termux = 127.0.0.1, PC Emulator = 10.0.2.2)
                OutlinedTextField(
                    value = hostIp,
                    onValueChange = { hostIp = it },
                    label = { Text("VOICEVOX 接続先IP") },
                    placeholder = { Text("127.0.0.1 または 10.0.2.2") },
                    modifier = Modifier.fillMaxWidth()
                )

                // Speaker Dropdown
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedSpeaker.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("話者（キャラクター）") },
                        trailingIcon = {
                            IconButton(onClick = { showSpeakerMenu = true }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().clickable { showSpeakerMenu = true }
                    )
                    DropdownMenu(
                        expanded = showSpeakerMenu,
                        onDismissRequest = { showSpeakerMenu = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        viewModel.voiceVoxSpeakers.forEach { speaker ->
                            DropdownMenuItem(
                                text = { Text(speaker.name) },
                                onClick = {
                                    selectedSpeaker = speaker
                                    showSpeakerMenu = false
                                }
                            )
                        }
                    }
                }
            }
        }

        // Text input field
        OutlinedTextField(
            value = inputText,
            onValueChange = { inputText = it },
            label = { Text("読み上げテキスト") },
            maxLines = 5,
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
        )

        // Synthesize Button
        Button(
            onClick = {
                if (inputText.isNotBlank()) {
                    viewModel.synthesizeVoice(inputText, selectedSpeaker.id, hostIp)
                } else {
                    Toast.makeText(context, "テキストを入力してください", Toast.LENGTH_SHORT).show()
                }
            },
            enabled = state != VoiceVoxUiState.Loading,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            if (state == VoiceVoxUiState.Loading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
            } else {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("音声生成＆WAV書き出し")
            }
        }

        // Result Card
        when (val voiceState = state) {
            is VoiceVoxUiState.Success -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0x2210B981)),
                    border = BorderStroke(1.dp, Color(0xFF10B981))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("合成成功", color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                        Text("保存先: Download/VOICEVOX/${voiceState.fileName}", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Play Button
                            Button(
                                onClick = { playAudio(context, voiceState.uri) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                            ) {
                                Icon(Icons.Default.VolumeUp, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("再生")
                            }
                            // Share Button
                            Button(
                                onClick = { viewModel.shareWav(voiceState.uri) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("共有")
                            }
                        }
                    }
                }
            }
            is VoiceVoxUiState.Error -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0x22EF4444)),
                    border = BorderStroke(1.dp, Color(0xFFEF4444))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("エラーが発生しました", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                        Text(voiceState.message, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            else -> {}
        }
    }
}

private fun playAudio(context: Context, uri: Uri) {
    try {
        val mediaPlayer = MediaPlayer().apply {
            setDataSource(context, uri)
            prepare()
            start()
        }
        mediaPlayer.setOnCompletionListener {
            it.release()
        }
    } catch (e: Exception) {
        Toast.makeText(context, "再生に失敗しました: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

// --- GLOBAL OVERLAY: Update Dialog Manager ---
@Composable
fun UpdateDialogOverlay(viewModel: MainViewModel) {
    val context = LocalContext.current
    val updateState by viewModel.updateUiState.collectAsState()

    when (val state = updateState) {
        is UpdateUiState.Checking -> {
            Dialog(onDismissRequest = {}) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .background(Color(0xCC1E293B), shape = RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
        is UpdateUiState.UpdateAvailable -> {
            AlertDialog(
                onDismissRequest = { viewModel.clearUpdateState() },
                title = { Text("アップデート通知") },
                text = { Text("新しいバージョン ${state.info.versionName} が利用可能です。ダウンロードして更新しますか？") },
                confirmButton = {
                    Button(
                        onClick = { viewModel.downloadAndInstallUpdate(state.info) }
                    ) {
                        Text("更新")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.clearUpdateState() }) {
                        Text("キャンセル")
                    }
                }
            )
        }
        is UpdateUiState.Downloading -> {
            Dialog(onDismissRequest = {}) {
                Card(
                    modifier = Modifier.padding(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("アップデートをダウンロード中...", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(16.dp))
                        LinearProgressIndicator(
                            progress = state.progress,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("${(state.progress * 100).toInt()}%")
                    }
                }
            }
        }
        is UpdateUiState.Downloaded -> {
            AlertDialog(
                onDismissRequest = { viewModel.clearUpdateState() },
                title = { Text("ダウンロード完了") },
                text = { Text("ダウンロードが完了しました。インストールを開始してください。") },
                confirmButton = {
                    Button(
                        onClick = {
                            if (viewModel.checkInstallPermission(context)) {
                                viewModel.installApk(state.file)
                            } else {
                                Toast.makeText(context, "インストール権限を許可してください", Toast.LENGTH_LONG).show()
                                viewModel.requestInstallPermission(context)
                            }
                        }
                    ) {
                        Text("インストール")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.clearUpdateState() }) {
                        Text("閉じる")
                    }
                }
            )
        }
        is UpdateUiState.UpToDate -> {
            LaunchedEffect(state) {
                Toast.makeText(context, "アプリは最新版です", Toast.LENGTH_SHORT).show()
                viewModel.clearUpdateState()
            }
        }
        is UpdateUiState.Error -> {
            AlertDialog(
                onDismissRequest = { viewModel.clearUpdateState() },
                title = { Text("更新エラー") },
                text = { Text(state.message) },
                confirmButton = {
                    Button(onClick = { viewModel.clearUpdateState() }) {
                        Text("確認")
                    }
                }
            )
        }
        else -> {}
    }
}
