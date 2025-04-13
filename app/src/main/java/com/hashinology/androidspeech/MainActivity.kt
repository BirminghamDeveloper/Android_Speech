package com.hashinology.androidspeech

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.hashinology.androidspeech.ui.theme.AndroidSpeechTheme
import android.Manifest
import android.content.Intent
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var textToSpeech: TextToSpeech
    private val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.language = Locale.US
            }
        }

        setContent {
            val context = LocalContext.current
            var chatMessages by remember { mutableStateOf(listOf<ChatMessage>()) }
            var currentInput by remember { mutableStateOf("") }
            var isListening by remember { mutableStateOf(false) }
            var isLoading by remember { mutableStateOf(false) }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(chatMessages) { message ->
                        Box(modifier = Modifier.padding(vertical = 8.dp)) {
                            if (message.isUser) {
                                Row(modifier = Modifier.align(Alignment.TopEnd)) {
                                    ChatBubble(message.text, true)
                                }
                            } else {
                                Row(modifier = Modifier.align(Alignment.TopStart)) {
                                    ChatBubble(message.text, false)
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextField(
                        value = currentInput,
                        onValueChange = { currentInput = it },
                        placeholder = { Text("Type your message...") },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (currentInput.isNotBlank()) {
                                sendMessage(currentInput, chatMessages, ::setChatMessages)
                            }
                        },
                        enabled = currentInput.isNotBlank()
                    ) {
                        Text("Send")
                    }
                    IconButton(
                        onClick = {
                            requestPermission(
                                Manifest.permission.RECORD_AUDIO,
                                onGranted = {
                                    startListening()
                                    isListening = true
                                },
                                onDenied = { Toast.makeText(context, "Permission denied", Toast.LENGTH_SHORT).show() }
                            )
                        },
                        enabled = !isListening
                    ) {
                        Icon(
                            painter = painterResource(id = android.R.drawable.ic_input_get),
                            contentDescription = "Voice Input",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            LaunchedEffect(isLoading) {
                if (isLoading) {
                    // Simulate API delay
                    delay(1500)
                    val response = "AI Response: ${currentInput} (Mock)"
                    chatMessages += ChatMessage(response, false)
                    speakText(response)
                    isLoading = false
                }
            }
        }
    }

    private fun sendMessage(input: String, currentMessages: List<ChatMessage>, updateMessages: (List<ChatMessage>) -> Unit) {
        currentMessages += ChatMessage(input, true)
        updateMessages(currentMessages)
        currentInput = ""
        isLoading = true
    }

    private fun speakText(text: String) {
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun startListening() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                isListening = false
                Toast.makeText(this@MainActivity, "Error occurred", Toast.LENGTH_SHORT).show()
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (matches != null && matches.isNotEmpty()) {
                    currentInput = matches[0]
                    sendMessage(matches[0], chatMessages, ::setChatMessages)
                }
                isListening = false
            }

            override fun onPartialResults(p0: Bundle?) {}
            override fun onEvent(p0: Int, p1: Bundle?) {}
        })
        speechRecognizer.startListening(speechIntent)
    }

    private fun requestPermission(permission: String, onGranted: () -> Unit, onDenied: () -> Unit) {
        val permissionResult = remember { mutableStateOf(false) }
        val permissionLauncher = rememberActivityResultLauncher(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                onGranted()
            } else {
                onDenied()
            }
        }
        if (!permissionResult.value) {
            permissionLauncher.launch(permission)
            permissionResult.value = true
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer.destroy()
        textToSpeech.stop()
        textToSpeech.shutdown()
    }
}

data class ChatMessage(val text: String, val isUser: Boolean)

@Composable
fun ChatBubble(text: String, isUser: Boolean) {
    Surface(
        modifier = Modifier
            .padding(4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
        color = Color.Transparent
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(16.dp),
            color = if (isUser) Color.White else Color.Black,
            fontSize = 16.sp,
            fontWeight = FontWeight.Normal
        )
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    AndroidSpeechTheme {
        ChatScreen()
    }
}