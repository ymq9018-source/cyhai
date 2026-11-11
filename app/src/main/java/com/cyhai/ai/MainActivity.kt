package com.cyhai.ai

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

private val Bg = Color(0xFF0D111B)
private val SurfaceDark = Color(0xFF171E2B)
private val SurfaceLight = Color(0xFF202A3A)
private val Accent = Color(0xFF8B9CFF)
private val Muted = Color(0xFF9AA7BB)
private val MainText = Color(0xFFF2F4FA)

data class ImportedModel(
    val id: String,
    val name: String,
    val uri: String,
    val format: String,
    val category: String
)

enum class AppTab(val label: String) {
    CHAT("聊天"), HISTORY("历史"), MODELS("模型"), SETTINGS("设置")
}

class MainActivity : ComponentActivity() {
    private val models = mutableStateListOf<ImportedModel>()
    private var selectedModelId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadModels()
        setContent {
            CyhaiTheme {
                CyhaiApp(
                    models = models,
                    selectedModelId = selectedModelId,
                    onSelectModel = { selectedModelId = it; saveModels() },
                    onImportModel = { uri -> importModel(uri) },
                    onRemoveModel = { model -> models.remove(model); saveModels() }
                )
            }
        }
    }

    private fun importModel(uri: Uri) {
        try { contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
        val name = queryDisplayName(uri) ?: (uri.lastPathSegment ?: "未命名模型")
        val extension = name.substringAfterLast('.', "unknown").lowercase()
        val category = when (extension) {
            "gguf", "ggml", "safetensors", "bin", "pt", "pth", "onnx", "tflite", "mlmodel" -> "文本/通用"
            "wav", "mp3", "flac", "ogg", "onnx" -> "音频"
            else -> "其他"
        }
        val model = ImportedModel(
            id = uri.toString(),
            name = name,
            uri = uri.toString(),
            format = extension.uppercase(),
            category = category
        )
        if (models.none { it.id == model.id }) models.add(model)
        if (selectedModelId == null) selectedModelId = model.id
        saveModels()
    }

    private fun queryDisplayName(uri: Uri): String? {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) return cursor.getString(index)
            }
        }
        return null
    }

    private fun saveModels() {
        val array = JSONArray()
        models.forEach {
            array.put(JSONObject().put("id", it.id).put("name", it.name).put("uri", it.uri)
                .put("format", it.format).put("category", it.category))
        }
        getSharedPreferences("cyhai", Context.MODE_PRIVATE).edit()
            .putString("models", array.toString())
            .putString("selected_model", selectedModelId)
            .apply()
    }

    private fun loadModels() {
        val prefs = getSharedPreferences("cyhai", Context.MODE_PRIVATE)
        selectedModelId = prefs.getString("selected_model", null)
        val raw = prefs.getString("models", "[]") ?: "[]"
        try {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                models.add(ImportedModel(
                    item.optString("id"), item.optString("name"), item.optString("uri"),
                    item.optString("format"), item.optString("category")
                ))
            }
        } catch (_: Exception) { }
    }
}

@Composable
private fun CyhaiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            secondary = Color(0xFF8ADBCB),
            background = Bg,
            surface = SurfaceDark,
            onPrimary = Bg,
            onBackground = MainText,
            onSurface = MainText
        ),
        content = content
    )
}

@Composable
private fun CyhaiApp(
    models: SnapshotStateList<ImportedModel>,
    selectedModelId: String?,
    onSelectModel: (String) -> Unit,
    onImportModel: (Uri) -> Unit,
    onRemoveModel: (ImportedModel) -> Unit
) {
    var tab by remember { mutableStateOf(AppTab.CHAT) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImportModel(uri)
    }

    Scaffold(
        containerColor = Bg,
        bottomBar = {
            NavigationBar(containerColor = SurfaceDark, contentColor = MainText) {
                listOf(
                    AppTab.CHAT to Icons.Filled.Chat,
                    AppTab.HISTORY to Icons.Filled.History,
                    AppTab.MODELS to Icons.Filled.Memory,
                    AppTab.SETTINGS to Icons.Filled.Settings
                ).forEach { (item, icon) ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Icon(icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Accent,
                            selectedTextColor = Accent,
                            indicatorColor = SurfaceLight,
                            unselectedIconColor = Muted,
                            unselectedTextColor = Muted
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                AppTab.CHAT -> InferenceChatScreen(models.firstOrNull { it.id == selectedModelId })
                AppTab.HISTORY -> HistoryScreen()
                AppTab.MODELS -> ModelScreen(
                    models = models,
                    selectedModelId = selectedModelId,
                    onSelect = onSelectModel,
                    onImport = { launcher.launch(arrayOf("*/*")) },
                    onRemove = onRemoveModel
                )
                AppTab.SETTINGS -> SettingsScreen()
            }
        }
    }
}

@Composable
private fun PageHeader(title: String, subtitle: String? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MainText)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        if (trailing != null) trailing()
    }
}

@Composable
private fun ChatScreen(activeModel: ImportedModel?) {
    var input by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf<Pair<Boolean, String>>() }
    Column(Modifier.fillMaxSize().background(Bg)) {
        PageHeader("Cyhai AI", "你的私人模型工作台") {
            Surface(color = SurfaceLight, shape = RoundedCornerShape(50)) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Circle, null, tint = if (activeModel != null) Color(0xFF76D6A5) else Muted, modifier = Modifier.size(8.dp))
                    Spacer(Modifier.width(7.dp))
                    Text(activeModel?.name ?: "未选择模型", color = MainText, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (messages.isEmpty()) {
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(Modifier.size(76.dp).clip(RoundedCornerShape(25.dp)).background(SurfaceLight), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.AutoAwesome, null, tint = Accent, modifier = Modifier.size(38.dp))
                }
                Spacer(Modifier.height(20.dp))
                Text("今天想聊些什么？", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (activeModel == null) "先到「模型」页面导入模型并选择它。" else "模型已登记。当前版本正在搭建推理引擎，暂时还不能生成模型回复。",
                    color = Muted, style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(messages) { message ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.first) Arrangement.End else Arrangement.Start) {
                        Surface(color = if (message.first) Color(0xFF303B5A) else SurfaceDark, shape = RoundedCornerShape(18.dp)) {
                            Text(message.second, Modifier.padding(14.dp), color = MainText)
                        }
                    }
                }
            }
        }
        Surface(color = SurfaceDark, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Column(Modifier.padding(10.dp)) {
                OutlinedTextField(
                    value = input, onValueChange = { input = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("输入消息…", color = Muted) },
                    minLines = 1, maxLines = 5,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = MainText, unfocusedTextColor = MainText, cursorColor = Accent
                    )
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row {
                        IconButton(onClick = { }) { Icon(Icons.Filled.Image, "图片入口", tint = Muted) }
                        IconButton(onClick = { }) { Icon(Icons.Filled.Mic, "语音入口", tint = Muted) }
                    }
                    IconButton(
                        onClick = {
                            val text = input.trim()
                            if (text.isNotEmpty()) {
                                messages.add(true to text)
                                messages.add(false to "消息已在界面中记录。当前版本尚未接入推理引擎，因此不会伪造模型回复。")
                                input = ""
                            }
                        },
                        enabled = input.isNotBlank()
                    ) {
                        Icon(Icons.Filled.Send, "发送", tint = if (input.isNotBlank()) Accent else Muted)
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryScreen() {
    Column(Modifier.fillMaxSize().background(Bg)) {
        PageHeader("历史记录", "搜索和继续之前的对话")
        OutlinedTextField(
            value = "", onValueChange = {}, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            placeholder = { Text("搜索历史记录", color = Muted) },
            leadingIcon = { Icon(Icons.Filled.Search, null, tint = Muted) },
            enabled = false,
            colors = OutlinedTextFieldDefaults.colors(disabledBorderColor = SurfaceLight, disabledTextColor = MainText)
        )
        Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.Forum, null, tint = Muted, modifier = Modifier.size(42.dp))
                Spacer(Modifier.height(12.dp))
                Text("还没有保存的对话", color = MainText, fontWeight = FontWeight.Medium)
                Text("后续版本会加入会话保存、搜索和管理。", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ModelScreen(
    models: SnapshotStateList<ImportedModel>,
    selectedModelId: String?,
    onSelect: (String) -> Unit,
    onImport: () -> Unit,
    onRemove: (ImportedModel) -> Unit
) {
    var filter by remember { mutableStateOf("全部") }
    val filters = listOf("全部", "文本", "视觉", "语音", "其他")
    val visibleModels = models.filter {
        filter == "全部" || when (filter) {
            "文本" -> it.category == "文本/通用"
            "视觉" -> it.category == "视觉"
            "语音" -> it.category == "音频"
            else -> it.category == "其他"
        }
    }
    Column(Modifier.fillMaxSize().background(Bg)) {
        PageHeader("模型管理", "导入、分类并选择你自己的模型") {
            FilledTonalButton(onClick = onImport, colors = ButtonDefaults.filledTonalButtonColors(containerColor = Accent, contentColor = Bg)) {
                Icon(Icons.Filled.Add, null)
                Spacer(Modifier.width(4.dp))
                Text("导入")
            }
        }
        Text("模型库", Modifier.padding(start = 20.dp, bottom = 10.dp), color = Muted, style = MaterialTheme.typography.labelLarge)
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            filters.forEach { item ->
                FilterChip(
                    selected = filter == item, onClick = { filter = item }, label = { Text(item) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = SurfaceLight, selectedLabelColor = Accent)
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        if (visibleModels.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.FolderOpen, null, tint = Muted, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(if (models.isEmpty()) "模型库还是空的" else "这个分类还没有模型", color = MainText, fontWeight = FontWeight.SemiBold)
                    Text("支持先登记常见模型文件；能否运行取决于后续接入的推理引擎。", color = Muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(onClick = onImport) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("选择模型文件") }
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(visibleModels, key = { it.id }) { model ->
                    ModelCard(model, selectedModelId == model.id, { onSelect(model.id) }, { onRemove(model) })
                }
            }
        }
        Surface(color = SurfaceDark, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Filled.Info, null, tint = Accent, modifier = Modifier.padding(end = 10.dp).size(20.dp))
                Text("导入只会登记文件位置，不会自动转换格式或启动模型。模型文件保留在原位置；请勿移动或删除它。", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ModelCard(model: ImportedModel, selected: Boolean, onSelect: () -> Unit, onRemove: () -> Unit) {
    Surface(color = if (selected) Color(0xFF202B45) else SurfaceDark, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().clickable { onSelect() }) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(13.dp)).background(SurfaceLight), contentAlignment = Alignment.Center) {
                Icon(
                    when (model.category) {
                        "视觉" -> Icons.Filled.Visibility
                        "音频" -> Icons.Filled.GraphicEq
                        else -> Icons.Filled.Memory
                    }, null, tint = Accent
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(model.name, color = MainText, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${model.format} · ${model.category}", color = Muted, style = MaterialTheme.typography.bodySmall)
                Text("已登记 · 推理引擎待接入", color = Color(0xFFE6C27A), style = MaterialTheme.typography.labelSmall)
            }
            if (selected) Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF83D8AE))
            IconButton(onClick = onRemove) { Icon(Icons.Filled.DeleteOutline, "移除", tint = Muted) }
        }
    }
}

@Composable
private fun SettingsScreen() {
    Column(Modifier.fillMaxSize().background(Bg)) {
        PageHeader("设置", "管理客户端偏好与扩展功能")
        SettingsRow(Icons.Filled.Memory, "模型管理", "导入和切换本地模型", "请使用底部「模型」标签")
        SettingsRow(Icons.Filled.Palette, "外观", "深色主题", "当前")
        SettingsRow(Icons.Filled.Storage, "数据与存储", "聊天记录和模型索引", "本地")
        SettingsRow(Icons.Filled.Info, "关于 Cyhai AI", "版本 0.1.0 · 初始开发版", "Android")
        Spacer(Modifier.height(16.dp))
        Text("模型兼容性说明", Modifier.padding(horizontal = 20.dp), color = MainText, fontWeight = FontWeight.SemiBold)
        Text(
            "客户端采用可扩展的模型适配架构。GGUF、ONNX、TFLite、Safetensors 等文件格式需要各自匹配的运行时；文件可被选择不代表当前版本已经能够推理。",
            Modifier.padding(horizontal = 20.dp, vertical = 8.dp), color = Muted, style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun SettingsRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, value: String) {
    Surface(color = SurfaceDark, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Accent, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = MainText, fontWeight = FontWeight.Medium)
                Text(subtitle, color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            Text(value, color = Muted, style = MaterialTheme.typography.labelSmall)
        }
    }
}
