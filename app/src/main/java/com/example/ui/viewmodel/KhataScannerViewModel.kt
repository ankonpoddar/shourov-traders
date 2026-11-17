package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Customer
import com.example.data.model.ScannedLedgerRow
import com.example.data.repository.KhataAiRepository
import com.example.data.repository.LedgerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface KhataScanUiState {
    data object Idle : KhataScanUiState
    data class Scanning(val imageUri: Uri) : KhataScanUiState
    data class Review(
        val imageUri: Uri?,
        val rows: List<ScannedLedgerRow>,
        val isImporting: Boolean = false,
    ) : KhataScanUiState

    data class Imported(val count: Int) : KhataScanUiState
    data class Error(val message: String, val imageUri: Uri? = null) : KhataScanUiState
}

class KhataScannerViewModel(
    private val aiRepository: KhataAiRepository,
    private val ledgerRepository: LedgerRepository,
    private val currentUserId: String,
) : ViewModel() {

    private val _scanState = MutableStateFlow<KhataScanUiState>(KhataScanUiState.Idle)
    val scanState: StateFlow<KhataScanUiState> = _scanState.asStateFlow()

    fun analyzeKhataImage(context: Context, imageUri: Uri) {
        viewModelScope.launch {
            _scanState.value = KhataScanUiState.Scanning(imageUri)
            val result = aiRepository.scanLedgerImage(context.applicationContext, imageUri)
            result.fold(
                onSuccess = { rows ->
                    if (rows.isNotEmpty()) {
                        _scanState.value = KhataScanUiState.Review(imageUri = imageUri, rows = rows)
                    } else {
                        _scanState.value = KhataScanUiState.Error(
                            message = "No ledger rows detected. Try a clearer photo or use manual entry.",
                            imageUri = imageUri,
                        )
                    }
                },
                onFailure = { error ->
                    _scanState.value = KhataScanUiState.Error(
                        message = error.localizedMessage ?: "Failed to scan Khata page.",
                        imageUri = imageUri,
                    )
                },
            )
        }
    }

    fun startManualTable() {
        _scanState.value = KhataScanUiState.Review(
            imageUri = null,
            rows = listOf(
                ScannedLedgerRow(
                    localId = UUID.randomUUID().toString(),
                    customerName = "",
                    dateText = "16 Nov",
                    totalAmount = "0",
                    paidAmount = "0",
                    dueAmount = "0",
                    notes = "",
                )
            ),
        )
    }

    fun updateRow(updated: ScannedLedgerRow) {
        val current = _scanState.value as? KhataScanUiState.Review ?: return
        _scanState.value = current.copy(
            rows = current.rows.map { if (it.localId == updated.localId) updated else it }
        )
    }

    fun addRow() {
        val current = _scanState.value as? KhataScanUiState.Review ?: return
        _scanState.value = current.copy(
            rows = current.rows + ScannedLedgerRow(
                localId = UUID.randomUUID().toString(),
                customerName = "",
                dateText = "",
                totalAmount = "0",
                paidAmount = "0",
                dueAmount = "0",
                notes = "",
            )
        )
    }

    fun removeRow(localId: String) {
        val current = _scanState.value as? KhataScanUiState.Review ?: return
        _scanState.value = current.copy(
            rows = current.rows.filterNot { it.localId == localId }
        )
    }

    fun importAll(existingCustomers: List<Customer>) {
        val current = _scanState.value as? KhataScanUiState.Review ?: return
        val validRows = current.rows.filter { it.customerName.isNotBlank() }
        if (validRows.isEmpty()) {
            _scanState.value = KhataScanUiState.Error(
                message = "Please enter at least one customer name before importing.",
                imageUri = current.imageUri,
            )
            return
        }

        viewModelScope.launch {
            _scanState.value = current.copy(isImporting = true)
            val result = ledgerRepository.importScannedRows(currentUserId, existingCustomers, validRows)
            result.fold(
                onSuccess = { count ->
                    _scanState.value = KhataScanUiState.Imported(count)
                },
                onFailure = { error ->
                    _scanState.value = KhataScanUiState.Error(
                        message = error.localizedMessage ?: "Failed to import ledger rows.",
                        imageUri = current.imageUri,
                    )
                },
            )
        }
    }

    fun reset() {
        _scanState.value = KhataScanUiState.Idle
    }
}
