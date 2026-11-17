package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.data.model.Customer
import com.example.data.model.ScannedLedgerRow
import com.example.data.preferences.LanguageMode
import com.example.data.preferences.bi
import com.example.ui.theme.CrimsonDue
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.HarvestAmber
import com.example.ui.theme.ShouravColors
import com.example.ui.viewmodel.KhataScanUiState
import java.io.File

@Composable
fun KhataScannerScreen(
    scanState: KhataScanUiState,
    existingCustomers: List<Customer>,
    mode: LanguageMode,
    isOnline: Boolean,
    onAnalyzeImage: (Context, Uri) -> Unit,
    onStartManualTable: () -> Unit,
    onUpdateRow: (ScannedLedgerRow) -> Unit,
    onAddRow: () -> Unit,
    onRemoveRow: (String) -> Unit,
    onImportAll: (List<Customer>) -> Unit,
    onReset: () -> Unit,
    onGoToKhata: () -> Unit,
) {
    val context = LocalContext.current
    var pendingCameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var permissionNotice by remember { mutableStateOf<String?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                permissionNotice = null
                onAnalyzeImage(context, uri)
            }
        },
    )

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { success ->
            val uri = pendingCameraUri
            if (success && uri != null) {
                permissionNotice = null
                onAnalyzeImage(context, uri)
            }
        },
    )

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (granted) {
                permissionNotice = null
                val uri = createTempCameraUri(context)
                pendingCameraUri = uri
                cameraLauncher.launch(uri)
            } else {
                permissionNotice = bi(
                    "Camera permission denied. You can still upload a Khata photo from Gallery.",
                    "ক্যামেরা অনুমতি দেওয়া হয়নি। আপনি গ্যালারি থেকে ছবি আপলোড করতে পারেন।",
                    mode,
                )
            }
        },
    )

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
                .testTag("khata_scanner_list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = bi("AI Notebook Scanner (Scan Khata)", "পুরনো খাতা স্ক্যান করুন (AI)", mode),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = bi(
                            "Upload or photograph handwritten Bengali/English ledger pages. Review & edit extracted rows before importing.",
                            "হাতে লেখা বাংলা বা ইংরেজি বাকী খাতার ছবি তুলুন বা আপলোড করুন। যাচাই করে ডাটাবেসে যোগ করুন।",
                            mode,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Capture / Upload Action Card
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Button(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                enabled = isOnline,
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                                    .testTag("scan_upload_photo_button"),
                            ) {
                                Icon(imageVector = Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(bi("Upload Photo", "ছবি আপলোড", mode), maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = {
                                    val hasCamera = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.CAMERA,
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (hasCamera) {
                                        val uri = createTempCameraUri(context)
                                        pendingCameraUri = uri
                                        cameraLauncher.launch(uri)
                                    } else {
                                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                },
                                enabled = isOnline,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, if (isOnline) ShouravColors.emeraldIconTint else MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                                    .testTag("scan_camera_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CameraAlt,
                                    contentDescription = null,
                                    tint = if (isOnline) ShouravColors.emeraldIconTint else ShouravColors.subtleText,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = bi("Camera", "ক্যামেরা", mode),
                                    color = if (isOnline) ShouravColors.emeraldInk else ShouravColors.subtleText,
                                    maxLines = 1,
                                )
                            }
                        }

                        if (!isOnline) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                border = BorderStroke(1.dp, ShouravColors.amberBorder),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = bi(
                                        "AI photo scanning requires internet. You can still use the blank bulk entry table below offline.",
                                        "এআই ছবি স্ক্যান করতে ইন্টারনেট প্রয়োজন। তবে নিচের খালি টেবিল দিয়ে অফলাইনেও হিসাব যোগ করতে পারবেন।",
                                        mode,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = HarvestAmber,
                                    modifier = Modifier.padding(10.dp),
                                )
                            }
                        }

                        TextButton(
                            onClick = onStartManualTable,
                            modifier = Modifier.align(Alignment.End),
                        ) {
                            Icon(imageVector = Icons.Filled.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(bi("Or open blank bulk entry table", "অথবা খালি টেবিলে লিখুন", mode))
                        }

                        if (permissionNotice != null) {
                            Text(
                                text = permissionNotice.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = CrimsonDue,
                            )
                        }
                    }
                }
            }

            when (scanState) {
                is KhataScanUiState.Idle -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(28.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DocumentScanner,
                                    contentDescription = null,
                                    tint = ShouravColors.emeraldIconTint,
                                    modifier = Modifier.size(40.dp),
                                )
                                Text(
                                    text = bi(
                                        "Ready to scan Bengali or English Khata pages",
                                        "বাংলা বা ইংরেজি খাতার পাতা স্ক্যানের জন্য প্রস্তুত",
                                        mode,
                                    ),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }

                is KhataScanUiState.Scanning -> {
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            border = BorderStroke(1.dp, ShouravColors.emeraldBorder),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                AsyncImage(
                                    model = scanState.imageUri,
                                    contentDescription = "Selected Khata page",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(160.dp)
                                        .clip(RoundedCornerShape(12.dp)),
                                )
                                CircularProgressIndicator(color = ShouravColors.emeraldIconTint)
                                Text(
                                    text = bi(
                                        "Gemini Vision is reading ledger rows...",
                                        "জেমিনি এআই খাতার হিসাব পড়ছে...",
                                        mode,
                                    ),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = ShouravColors.emeraldInk,
                                )
                            }
                        }
                    }
                }

                is KhataScanUiState.Error -> {
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            border = BorderStroke(1.dp, CrimsonDue.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = bi("Scan Error", "স্ক্যান ত্রুটি", mode),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = CrimsonDue,
                                )
                                Text(
                                    text = scanState.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = onReset) {
                                        Text(bi("Try Again", "আবার চেষ্টা করুন", mode))
                                    }
                                    TextButton(onClick = onStartManualTable) {
                                        Text(bi("Enter Manually", "হাতে লিখুন", mode))
                                    }
                                }
                            }
                        }
                    }
                }

                is KhataScanUiState.Imported -> {
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            border = BorderStroke(1.dp, ShouravColors.emeraldBorder),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = ShouravColors.emeraldIconTint,
                                    modifier = Modifier.size(44.dp),
                                )
                                Text(
                                    text = "${scanState.count} ${bi("ledger entries imported!", "টি হিসাব ডাটাবেসে যোগ হয়েছে!", mode)}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = ShouravColors.emeraldInk,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Button(
                                        onClick = onGoToKhata,
                                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                                    ) {
                                        Text(bi("View Baki Khata", "বাকী খাতা দেখুন", mode))
                                    }
                                    OutlinedButton(onClick = onReset) {
                                        Text(bi("Scan Another", "আরেকটি স্ক্যান", mode))
                                    }
                                }
                            }
                        }
                    }
                }

                is KhataScanUiState.Review -> {
                    if (scanState.imageUri != null) {
                        item {
                            AsyncImage(
                                model = scanState.imageUri,
                                contentDescription = "Scanned Khata Image",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                                    .clip(RoundedCornerShape(14.dp)),
                            )
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "${bi("Editable Review Table", "যাচাই ও সম্পাদনা টেবিল", mode)} (${scanState.rows.size})",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            TextButton(onClick = onAddRow) {
                                Icon(imageVector = Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(bi("Add Row", "সারি যোগ", mode))
                            }
                        }
                    }

                    items(scanState.rows, key = { it.localId }) { row ->
                        EditableScannedRowCard(
                            row = row,
                            mode = mode,
                            onChange = onUpdateRow,
                            onRemove = { onRemoveRow(row.localId) },
                        )
                    }

                    item {
                        Button(
                            onClick = { onImportAll(existingCustomers) },
                            enabled = !scanState.isImporting && scanState.rows.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("import_all_khata_button"),
                        ) {
                            if (scanState.isImporting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(bi("Importing...", "ইম্পোর্ট হচ্ছে...", mode))
                            } else {
                                Icon(imageVector = Icons.Filled.CloudUpload, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(bi("Import All to Database", "সব ডাটাবেসে যোগ করুন", mode))
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun EditableScannedRowCard(
    row: ScannedLedgerRow,
    mode: LanguageMode,
    onChange: (ScannedLedgerRow) -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = row.customerName,
                    onValueChange = { onChange(row.copy(customerName = it)) },
                    label = { Text(bi("Customer Name", "ক্রেতার নাম", mode)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = "Remove row",
                        tint = ShouravColors.subtleText,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = row.dateText,
                    onValueChange = { onChange(row.copy(dateText = it)) },
                    label = { Text(bi("Date", "তারিখ", mode)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = row.totalAmount,
                    onValueChange = { onChange(row.copy(totalAmount = it)) },
                    label = { Text(bi("Total (৳)", "মোট (৳)", mode)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = row.paidAmount,
                    onValueChange = { onChange(row.copy(paidAmount = it)) },
                    label = { Text(bi("Paid (৳)", "জমা (৳)", mode)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = row.dueAmount,
                    onValueChange = { onChange(row.copy(dueAmount = it)) },
                    label = { Text(bi("Due (৳)", "বাকী (৳)", mode)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedTextField(
                value = row.notes,
                onValueChange = { onChange(row.copy(notes = it)) },
                label = { Text(bi("Notes / Rice Variety", "নোট / চালের বিবরণ", mode)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun createTempCameraUri(context: Context): Uri {
    val dir = File(context.cacheDir, "khata_captures").apply { mkdirs() }
    val file = File(dir, "khata_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
