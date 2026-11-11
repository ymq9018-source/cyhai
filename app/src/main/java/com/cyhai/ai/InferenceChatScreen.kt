package com.cyhai.ai

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File

@Composable
fun InferenceChatScreen(activeModel: ImportedModel?) {
    val context = LocalContext.current
    val engine = remember { AiChat.getInferenceEngine(context.applicationContext) }
    val engineState by engine.state.collectAsState()
    val messages = remember { mutableStateListOf<Pair<Boolean, String>>() }
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("正在初始化本地推理引擎…") }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(activeModel?.id) {
        if (activeModel == null) {
            status = "请先导入并选择 GGUF 模型"
            return@LaunchedEffect
        }
        if (!activeModel.name.endsWith(".gguf", ignoreCase = true)) {
            status = "当前引擎只运行 GGUF 格式，其他格式暂不支持"
            return@LaunchedEffect
        }
        busy = true
        status = "正在复制模型文件到应用内部存储…"
        try {
            // Copy first: a native-engine initialization stall must not prevent the model
            // file from being prepared or leave the UI stuck on a misleading status.
            val modelFile = withContext(Dispatchers.IO) {
                val dir = File(context.filesDir, "models").apply { mkdirs() }
                val safeName = activeModel.name.replace(Regex("[^A-Za-z0-9._-]"), "_")
                val target = File(dir, safeName)
                val temp = File(dir, "$safeName.part")
                val source = context.contentResolver.openInputStream(Uri.parse(activeModel.uri))
                    ?: error("无法读取所选模型文件")
                source.use { stream -> temp.outputStream().use { output -> stream.copyTo(output) } }
                if (target.exists()) target.delete()
                check(temp.renameTo(target)) { "模型文件保存失败，请检查存储空间" }
                target
            }
            status = "模型文件已准备，正在初始化推理引擎…"
            val readyState = withTimeout(180_000) {
                engine.state.first {
                    it is InferenceEngine.State.Initialized ||
                        it is InferenceEngine.State.ModelReady ||
                        it is InferenceEngine.State.Error
                }
            }
            if (readyState is InferenceEngine.State.Error) throw readyState.exception
            if (readyState is InferenceEngine.State.ModelReady) engine.cleanUp()

            status = "正在加载模型，首次加载可能需要一些时间…"
            engine.loadModel(modelFile.absolutePath)
            engine.setSystemPrompt("你是 Cyhai AI，一个运行在用户手机本地的中文 AI 助手。请清晰、准确地回答。")
            status = "模型已加载，可以开始聊天"
        } catch (e: Exception) {
            status = if (e is kotlinx.coroutines.TimeoutCancellationException) {
                "推理引擎初始化超时（180 秒）。请退出应用重开；若仍失败，需要检查原生库初始化日志。"
            } else {
                "模型加载失败：${e.message ?: e.javaClass.simpleName}"
            }
        } finally {
            busy = false
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF0D111B))) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text("Cyhai AI", color = Color(0xFFF2F4FA), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(status, color = Color(0xFF9AA7BB), style = MaterialTheme.typography.bodySmall)
            if (engineState is InferenceEngine.State.Generating) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
            }
        }
        if (messages.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    if (activeModel == null) "先到「模型」页面导入 GGUF 模型。" else "本地模型聊天已接入。模型文件只在本机读取。",
                    color = Color(0xFF9AA7BB)
                )
            }
        } else {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(messages) { _, message ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.first) Arrangement.End else Arrangement.Start) {
                        Surface(color = if (message.first) Color(0xFF303B5A) else Color(0xFF171E2B), shape = RoundedCornerShape(18.dp)) {
                            Text(message.second.ifEmpty { "…" }, Modifier.padding(14.dp), color = Color(0xFFF2F4FA))
                        }
                    }
                }
            }
        }
        Surface(color = Color(0xFF171E2B), shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("输入消息…") },
                    maxLines = 4,
                    enabled = !busy && engineState is InferenceEngine.State.ModelReady,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = Color(0xFFF2F4FA),
                        unfocusedTextColor = Color(0xFFF2F4FA)
                    )
                )
                IconButton(
                    enabled = input.isNotBlank() && !busy && engineState is InferenceEngine.State.ModelReady,
                    onClick = {
                        val prompt = input.trim()
                        input = ""
                        messages.add(true to prompt)
                        messages.add(false to "")
                        busy = true
                        scope.launch {
                            try {
                                engine.sendUserPrompt(prompt, 512).collect { token ->
                                    val last = messages.lastOrNull()
                                    if (last != null && !last.first) {
                                        messages[messages.lastIndex] = false to (last.second + token)
                                    }
                                }
                            } catch (e: Exception) {
                                messages.add(false to "推理出错：${e.message ?: "未知错误"}")
                            } finally {
                                busy = false
                            }
                        }
                    }
                ) {
                    Icon(Icons.Filled.Send, "发送", tint = if (input.isNotBlank() && !busy) Color(0xFF8B9CFF) else Color(0xFF9AA7BB))
                }
            }
        }
    }
}
