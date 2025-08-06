package com.invoice.myapplication

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.invoice.myapplication.ui.theme.MyApplicationTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*

class CreateInvoiceActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MyApplicationTheme { EditableReceiptScreen() } }

        if (!hasStoragePermission(this)) {
            ActivityCompat.requestPermissions(
                this,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
                else
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                1001
            )
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditableReceiptScreen() {
    val context = LocalContext.current
    var transactionId by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("") }
    var sender by remember { mutableStateOf("") }
    var senderAccount by remember { mutableStateOf("") }
    var receiver by remember { mutableStateOf("") }
    var receiverAccount by remember { mutableStateOf("") }
    var bank by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var invoiceFormat by remember { mutableStateOf("") }

    val openDateDialog = remember { mutableStateOf(false) }
    var openTimeDialog by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()
    var selectedImageBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            selectedImageBitmap = bitmap

            analyzeImageText(context, bitmap) { _, fields ->
                invoiceFormat = fields["format"] ?: ""
                transactionId = fields["transactionId"] ?: ""
                date = fields["date"] ?: ""
                time = fields["time"] ?: ""
                sender = fields["sender"] ?: ""
                senderAccount = fields["senderAccount"] ?: ""
                receiver = fields["receiver"] ?: ""
                receiverAccount = fields["receiverAccount"] ?: ""
                bank = fields["bank"] ?: ""
                amount = fields["amount"] ?: ""
            }
        }
    }

    if (openDateDialog.value) {
        DatePickerDialog(
            onDismissRequest = { openDateDialog.value = false },
            confirmButton = {
                TextButton(onClick = {
                    openDateDialog.value = false
                    date = datePickerState.selectedDateMillis?.let {
                        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(it))
                    } ?: ""
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { openDateDialog.value = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (openTimeDialog) {
        TimePickerDialogCustom(
            onDismissRequest = { openTimeDialog = false },
            onTimeSelected = { hour, minute ->
                val meridiem = if (hour < 12) "AM" else "PM"
                time = String.format("%02d:%02d %s", hour % 12, minute, meridiem)
            }
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Edit Transaction") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { launcher.launch("image/*") }) {
                Text("Pick")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            selectedImageBitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "Selected Image",
                    modifier = Modifier.fillMaxWidth().height(200.dp)
                )
            }

            OutlinedTextField(value = transactionId, onValueChange = { transactionId = it }, label = { Text("Transaction ID") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = date,
                    onValueChange = {},
                    label = { Text("Date") },
                    trailingIcon = {
                        Icon(Icons.Default.Close, "Pick date", Modifier.clickable { openDateDialog.value = true })
                    },
                    readOnly = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = time,
                    onValueChange = {},
                    label = { Text("Time") },
                    trailingIcon = {
                        Icon(Icons.Default.AccountBox, "Pick time", Modifier.clickable { openTimeDialog = true })
                    },
                    readOnly = true,
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(value = sender, onValueChange = { sender = it }, label = { Text("Sender") }, modifier = Modifier.fillMaxWidth())
            if (invoiceFormat == "jazzcash") {
                OutlinedTextField(value = senderAccount, onValueChange = { senderAccount = it }, label = { Text("Sender Account") }, modifier = Modifier.fillMaxWidth())
            }

            OutlinedTextField(value = receiver, onValueChange = { receiver = it }, label = { Text("Receiver") }, modifier = Modifier.fillMaxWidth())
            if (invoiceFormat == "jazzcash") {
                OutlinedTextField(value = receiverAccount, onValueChange = { receiverAccount = it }, label = { Text("Receiver Account") }, modifier = Modifier.fillMaxWidth())
            }

            OutlinedTextField(value = bank, onValueChange = { bank = it }, label = { Text("Bank") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Amount") }, modifier = Modifier.fillMaxWidth())
        }
    }
}

// 📸 OCR Handler
fun analyzeImageText(context: Context, bitmap: Bitmap, onResult: (String, Map<String, String>) -> Unit) {
    val image = InputImage.fromBitmap(bitmap, 0)
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    CoroutineScope(Dispatchers.IO).launch {
        try {
            val result = recognizer.process(image).await()
            val rawText = result.text
            val fields = extractFieldsFromText(rawText)
            withContext(Dispatchers.Main) { onResult(rawText, fields) }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) { onResult("", emptyMap()) }
        }
    }
}

// 🧠 Smart Text Extractor with Format Detection
fun extractFieldsFromText(text: String): Map<String, String> {
    val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
    val map = mutableMapOf<String, String>()
    try {
        val joinedText = lines.joinToString(" ").lowercase()
        if (joinedText.contains("fee/charge") || joinedText.contains("from acc#")) {
            map["format"] = "jazzcash"
            map["transactionId"] = lines.getOrNull(7)?.substringAfter(":")?.trim() ?: ""
            val dateTime = lines.getOrNull(6)?.substringAfter("on")?.split("at") ?: listOf("", "")
            map["date"] = dateTime.getOrNull(0)?.trim() ?: ""
            map["time"] = dateTime.getOrNull(1)?.trim() ?: ""
            map["receiver"] = lines.getOrNull(13) ?: ""
            map["receiverAccount"] = lines.getOrNull(14) ?: ""
            map["sender"] = lines.getOrNull(15) ?: ""
            map["senderAccount"] = lines.getOrNull(16) ?: ""
            map["bank"] = lines.getOrNull(17) ?: ""
            map["amount"] = lines.getOrNull(11)?.replace(Regex("[^0-9.]"), "") ?: ""
        } else if (joinedText.contains("transaction id") && joinedText.contains("sender:")) {
            map["format"] = "easypaisa"
            map["transactionId"] = lines.getOrNull(1)?.substringAfter(":")?.trim() ?: ""
            map["date"] = lines.getOrNull(8) ?: ""
            map["sender"] = lines.getOrNull(9) ?: ""
            map["bank"] = lines.getOrNull(10) ?: ""
            val time = lines.getOrNull(11) ?: ""
            val meridiem = lines.getOrNull(14) ?: ""
            map["time"] = "$time ${meridiem.uppercase()}".trim()
            map["receiver"] = lines.getOrNull(12) ?: ""
            map["amount"] = lines.getOrNull(13) ?: ""
        } else {
            map["format"] = "unknown"
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return map
}

// 🕒 Time Picker
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialogCustom(onDismissRequest: () -> Unit, onTimeSelected: (Int, Int) -> Unit) {
    val timePickerState = rememberTimePickerState()
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = {
                onTimeSelected(timePickerState.hour, timePickerState.minute)
                onDismissRequest()
            }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) { Text("Cancel") }
        },
        text = { TimePicker(state = timePickerState) }
    )
}
