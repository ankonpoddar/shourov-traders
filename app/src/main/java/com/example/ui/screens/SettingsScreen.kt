package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.backup.DriveBackupContract
import com.example.data.backup.DriveBackupUiState
import com.example.data.backup.DriveBackupWorkStatus
import com.example.data.preferences.LanguageMode
import com.example.data.preferences.bi
import com.example.ui.theme.CrimsonDue
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ForestHeroEnd
import com.example.ui.theme.ForestHeroStart
import com.example.ui.theme.HarvestAmber
import com.example.ui.theme.PlumBadge
import com.example.ui.theme.ShouravColors
import com.example.ui.theme.SkyChart
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    userEmail: String,
    languageMode: LanguageMode,
    isOnline: Boolean,
    hasPendingWrites: Boolean,
    customerCount: Int,
    onSelectLanguageMode: (LanguageMode) -> Unit,
    onOpenAnalytics: () -> Unit,
    onOpenScanner: () -> Unit,
    onExportMonthlyPdf: () -> Unit,
    onLoadSampleData: () -> Unit,
    onRequestResetData: () -> Unit,
    onSignOut: () -> Unit,
    driveBackupState: DriveBackupUiState = DriveBackupUiState(),
    onBackupToGoogleDrive: () -> Unit = {},
    onRestoreFromGoogleDrive: () -> Unit = {},
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
                .testTag("settings_screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text(
                    text = bi("Settings", "সেটিংস", languageMode),
                    style = MaterialTheme.typography.headlineMedium,
                    color = ShouravColors.emeraldInk,
                    fontWeight = FontWeight.Bold,
                )
            }

            // App Branding Card (matching Screenshot 1)
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    Brush.linearGradient(listOf(ForestHeroStart, ForestHeroEnd))
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Shourav Traders",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = bi(
                                    "Your trusted rice & Baki Khata ledger",
                                    "আপনার বিশ্বস্ত চাল ও হিসাব খাতা সহকারী",
                                    languageMode,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (userEmail.isNotBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "$userEmail · $customerCount ${bi("customers", "ক্রেতা", languageMode)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ShouravColors.emeraldInk,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            // Display & Language Section
            item {
                SectionHeading(
                    text = bi("Display & App Theme", "ডিসপ্লে ও ভাষা", languageMode)
                )
            }

            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Segmented Language Picker matching Screenshot 1
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                IconBadge(
                                    icon = Icons.Filled.Language,
                                    tint = ShouravColors.emeraldIconTint,
                                    bg = MaterialTheme.colorScheme.primaryContainer,
                                )
                                Text(
                                    text = bi("Language", "ভাষা পরিবর্তন", languageMode),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(
                                    LanguageMode.BENGALI to "বাংলা",
                                    LanguageMode.ENGLISH to "EN",
                                    LanguageMode.BILINGUAL to "Both",
                                ).forEach { (modeOption, label) ->
                                    val selected = languageMode == modeOption
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (selected) EmeraldPrimary else MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier
                                            .defaultMinSize(minHeight = 38.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { onSelectLanguageMode(modeOption) }
                                            .testTag("settings_lang_${modeOption.name.lowercase()}"),
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                                        ) {
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        SettingsActionRow(
                            icon = Icons.Filled.Analytics,
                            iconTint = PlumBadge,
                            iconBg = ShouravColors.plumContainer,
                            title = bi("7-Day & 30-Day Analytics", "৭ দিন ও ৩০ দিনের বিশ্লেষণ", languageMode),
                            subtitle = bi("Sales, Nagad, Due & Net Profit reports", "বিক্রি, নগদ, বাকী ও নিট লাভ রিপোর্ট", languageMode),
                            onClick = onOpenAnalytics,
                            testTag = "settings_analytics_row",
                        )
                    }
                }
            }

            // Data Management Section (matching Screenshots 1 & 4)
            item {
                SectionHeading(
                    text = bi("Data Management & Reports", "ডাটা ও রিপোর্ট ব্যবস্থাপনা", languageMode)
                )
            }

            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SettingsActionRow(
                            icon = Icons.Filled.PictureAsPdf,
                            iconTint = ShouravColors.emeraldIconTint,
                            iconBg = MaterialTheme.colorScheme.primaryContainer,
                            title = bi("Export Monthly PDF Report", "PDF রিপোর্ট এক্সপোর্ট করুন", languageMode),
                            subtitle = bi("Share, save or print full Baki Khata", "সম্পূর্ণ খাতা ও লাভের PDF ডাউনলোড করুন", languageMode),
                            onClick = onExportMonthlyPdf,
                            testTag = "settings_export_pdf_row",
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        SettingsActionRow(
                            icon = Icons.Filled.DocumentScanner,
                            iconTint = SkyChart,
                            iconBg = ShouravColors.skyContainer,
                            title = bi("AI Notebook Khata Scanner", "পুরনো খাতা স্ক্যান করুন (AI)", languageMode),
                            subtitle = bi("Scan paper ledger rows with camera or gallery", "ছবি তুলে খাতার হিসাব স্বয়ংক্রিয়ভাবে যোগ করুন", languageMode),
                            onClick = onOpenScanner,
                            testTag = "settings_scanner_row",
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        val isBackingUp = driveBackupState.workStatus == DriveBackupWorkStatus.BACKING_UP ||
                            driveBackupState.workStatus == DriveBackupWorkStatus.AUTHORIZING
                        val isRestoring = driveBackupState.workStatus == DriveBackupWorkStatus.FETCHING_BACKUP ||
                            driveBackupState.workStatus == DriveBackupWorkStatus.RESTORING
                        val backupSubtitle = when {
                            isBackingUp -> bi(
                                "Syncing JSON backup to Google Drive...",
                                "গুগল ড্রাইভে JSON ব্যাকআপ আপলোড হচ্ছে...",
                                languageMode,
                            )
                            driveBackupState.lastBackupAtMillis != null -> bi(
                                "Last backed up ${formatDriveTimestamp(driveBackupState.lastBackupAtMillis)} · ${driveBackupState.lastRemoteFileName ?: DriveBackupContract.BACKUP_FILE_NAME}",
                                "সর্বশেষ ব্যাকআপ: ${formatDriveTimestamp(driveBackupState.lastBackupAtMillis)}",
                                languageMode,
                            )
                            else -> bi(
                                "Save secondary JSON (${DriveBackupContract.BACKUP_FILE_NAME}) to Drive",
                                "গুগল ড্রাইভে খাতার JSON ব্যাকআপ সংরক্ষণ করুন",
                                languageMode,
                            )
                        }
                        SettingsActionRow(
                            icon = Icons.Filled.Backup,
                            iconTint = ShouravColors.emeraldIconTint,
                            iconBg = MaterialTheme.colorScheme.primaryContainer,
                            title = bi("Backup Data to Google Drive", "ডাটা ব্যাকআপ নিন (Drive)", languageMode),
                            subtitle = backupSubtitle,
                            onClick = if (driveBackupState.isWorking) null else onBackupToGoogleDrive,
                            isLoading = isBackingUp,
                            testTag = "settings_drive_backup_row",
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        val restoreSubtitle = when {
                            isRestoring -> bi(
                                "Loading & restoring Drive JSON backup...",
                                "গুগল ড্রাইভ থেকে ব্যাকআপ রিস্টোর হচ্ছে...",
                                languageMode,
                            )
                            driveBackupState.lastRestoreAtMillis != null -> bi(
                                "Last restored ${formatDriveTimestamp(driveBackupState.lastRestoreAtMillis)} from Google Drive",
                                "সর্বশেষ রিস্টোর: ${formatDriveTimestamp(driveBackupState.lastRestoreAtMillis)}",
                                languageMode,
                            )
                            else -> bi(
                                "Restore customers, sales & loans from Drive JSON",
                                "গুগল ড্রাইভের JSON ফাইল থেকে সব হিসাব ফিরিয়ে আনুন",
                                languageMode,
                            )
                        }
                        SettingsActionRow(
                            icon = Icons.Filled.CloudDownload,
                            iconTint = PlumBadge,
                            iconBg = ShouravColors.plumContainer,
                            title = bi("Restore from Google Drive", "ডাটা রিস্টোর করুন (Drive)", languageMode),
                            subtitle = restoreSubtitle,
                            onClick = if (driveBackupState.isWorking) null else onRestoreFromGoogleDrive,
                            isLoading = isRestoring,
                            testTag = "settings_drive_restore_row",
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        SettingsActionRow(
                            icon = if (isOnline) Icons.Filled.CloudDone else Icons.Filled.CloudOff,
                            iconTint = if (isOnline) ShouravColors.emeraldIconTint else HarvestAmber,
                            iconBg = if (isOnline) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer,
                            title = bi("Firestore Live Sync", "ফায়ারস্টোর লাইভ সিঙ্ক", languageMode),
                            subtitle = when {
                                !isOnline -> bi("Offline · Saved locally, syncs automatically", "অফলাইন · লোকালে সংরক্ষিত, অনলাইনে সিঙ্ক হবে", languageMode)
                                hasPendingWrites -> bi("Online · Syncing pending edits...", "অনলাইন · পরিবর্তন সিঙ্ক হচ্ছে...", languageMode)
                                else -> bi("Synced with Google Cloud Firestore", "সকল ডাটা নিরাপদে ক্লাউডে সংরক্ষিত", languageMode)
                            },
                            onClick = null,
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        SettingsActionRow(
                            icon = Icons.Filled.AutoAwesome,
                            iconTint = HarvestAmber,
                            iconBg = MaterialTheme.colorScheme.tertiaryContainer,
                            title = bi("Load Sample Rice Business Data", "নমুনা ডাটা লোড করুন", languageMode),
                            subtitle = bi("Add sample customers, sales & 13% bank loan", "নমুনা ক্রেতা, বিক্রি ও লোন যোগ করুন", languageMode),
                            onClick = onLoadSampleData,
                            testTag = "settings_sample_data_row",
                        )
                    }
                }
            }

            // Danger Zone & Account (matching Screenshot 4)
            item {
                Text(
                    text = bi("Danger Zone & Account", "বিপজ্জনক জোন ও অ্যাকাউন্ট", languageMode),
                    style = MaterialTheme.typography.titleSmall,
                    color = CrimsonDue,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }

            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, ShouravColors.crimsonBorder),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SettingsActionRow(
                            icon = Icons.Filled.DeleteSweep,
                            iconTint = CrimsonDue,
                            iconBg = MaterialTheme.colorScheme.errorContainer,
                            title = bi("Reset / Clear All Data", "সব ডাটা মুছুন (রিসেট)", languageMode),
                            subtitle = bi("Delete all customers, sales & expenses", "সব ক্রেতা, বিক্রি ও খরচ স্থায়ীভাবে মুছুন", languageMode),
                            titleColor = CrimsonDue,
                            onClick = onRequestResetData,
                            testTag = "settings_reset_row",
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        SettingsActionRow(
                            icon = Icons.AutoMirrored.Filled.Logout,
                            iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                            iconBg = MaterialTheme.colorScheme.surfaceVariant,
                            title = bi("Sign Out", "লগ আউট করুন", languageMode),
                            subtitle = userEmail.ifBlank { bi("Google account", "গুগল অ্যাকাউন্ট", languageMode) },
                            onClick = onSignOut,
                            testTag = "settings_sign_out_row",
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun IconBadge(
    icon: ImageVector,
    tint: Color,
    bg: Color,
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)?,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    isLoading: Boolean = false,
    testTag: String? = null,
) {
    var rowModifier = Modifier
        .fillMaxWidth()
        .defaultMinSize(minHeight = 60.dp)
    if (onClick != null) {
        rowModifier = rowModifier.clickable(onClick = onClick)
    }
    if (testTag != null) {
        rowModifier = rowModifier.testTag(testTag)
    }
    Row(
        modifier = rowModifier.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconBadge(icon = icon, tint = iconTint, bg = iconBg)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = titleColor,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = ShouravColors.emeraldIconTint,
                strokeWidth = 2.dp,
            )
        } else if (onClick != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = ShouravColors.subtleText,
            )
        }
    }
}

private fun formatDriveTimestamp(millis: Long): String {
    return SimpleDateFormat("dd MMM yy, hh:mm a", Locale.ENGLISH).format(Date(millis))
}
