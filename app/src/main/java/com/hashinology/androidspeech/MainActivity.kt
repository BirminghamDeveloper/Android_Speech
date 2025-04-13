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
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.Locale

data class Message(val text: String, val isUser: Boolean)

class MainActivity : ComponentActivity() {
    private lateinit var textToSpeech: TextToSpeech
    private lateinit var speechRecognizer: SpeechRecognizer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        textToSpeech = TextToSpeech(this) {}
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

        setContent {
            ChatScreen()
        }
    }

    override fun onDestroy() {
        textToSpeech.stop()
        textToSpeech.shutdown()
        speechRecognizer.destroy()
        super.onDestroy()
    }
}

@Composable
fun ChatScreen() {
    val context = LocalContext.current
    var userInput by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf(listOf<Message>()) }
    val coroutineScope = rememberCoroutineScope()
    val speechRecognizer = (context as MainActivity).speechRecognizer
    val textToSpeech = (context as MainActivity).textToSpeech

    // Voice input setup
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.let { data ->
                val spokenText = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0)
                spokenText?.let {
                    userInput = it
                    sendMessage(messages, userInput, textToSpeech, ::getBotResponse) { newMessages ->
                        messages = newMessages
                    }
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(8.dp)
        ) {
            items(messages) { message ->
                MessageBubble(message = message.text, isUser = message.isUser)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            TextField(
                value = userInput,
                onValueChange = { userInput = it },
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = {
                if (userInput.isNotBlank()) {
                    sendMessage(messages, userInput, textToSpeech, ::getBotResponse) { newMessages ->
                        messages = newMessages
                    }
                }
            }) {
                Icon(Icons.Default.Send, contentDescription = "Send")
            }
            IconButton(onClick = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(context as MainActivity, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
                } else {
                    startListening(speechLauncher)
                }
            }) {
                Icon(Icons.Default.Phone, contentDescription = "Speak")
            }
        }
    }
}

fun startListening(speechLauncher: ManagedActivityResultLauncher<Intent, *>) {
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
    }
    speechLauncher.launch(intent)
}

suspend fun sendMessage(
    currentMessages: List<Message>,
    input: String,
    tts: TextToSpeech,
    botResponseGenerator: (String) -> String,
    onMessagesUpdated: (List<Message>) -> Unit
) {
    val newUserMessage = Message(input, true)
    val newMessages = currentMessages + newUserMessage
    onMessagesUpdated(newMessages)

    // Simulate bot response
    coroutineScope {
        launch {
            val botResponse = botResponseGenerator(input)
            val newBotMessage = Message(botResponse, false)
            onMessagesUpdated(newMessages + newBotMessage)
            tts.speak(botResponse, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }
}

fun getBotResponse(input: String): String {
    // Replace with actual API call to OpenAI GPT
    return "Echo: $input (This is a mock response)"
}

@Composable
fun MessageBubble(message: String, isUser: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp)
            .padding(start = if (isUser) 64.dp else 0.dp)
            .padding(end = if (!isUser) 64.dp else 0.dp),
        colors = if (isUser) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurface
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodySmall
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