package com.example.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.BusinessExpense
import com.example.data.model.LedgerTransaction
import com.example.data.model.ScannedLedgerRow
import com.example.data.network.NetworkMonitor
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class OfflinePersistenceTest : FirestoreEmulatorTestBase() {

    @After
    fun restoreNetwork() {
        runBlocking {
            try {
                firestore.enableNetwork().await()
            } catch (_: Exception) {
            }
        }
    }

    @Test
    fun persistentCacheSettings_unlimitedSizeIsSupported() {
        val settings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(
                PersistentCacheSettings.newBuilder()
                    .setSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                    .build()
            )
            .build()

        val cacheSettings = settings.cacheSettings as PersistentCacheSettings
        assertEquals(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED, cacheSettings.sizeBytes)
    }

    @Test
    fun networkMonitor_doesNotCrashInRobolectricContext() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Just verify ConnectivityManager check runs cleanly without throwing
        NetworkMonitor.isCurrentlyOnline(context)
    }

    @Test
    fun offlineWrites_returnImmediatelyUpdateLocalCacheAndSyncWhenOnline() = runBlocking {
        val uid = signInTestUser("offline.trader@test.com")
        val repository = LedgerRepository(
            db = firestore,
            isNetworkAvailable = { false },
        )

        // Disable Firestore network stack to simulate true offline mode
        firestore.disableNetwork().await()

        val customerName = "Offline Rice Shop ${UUID.randomUUID().toString().take(6)}"
        val addCustomerResult = withTimeout(OFFLINE_WRITE_TIMEOUT_MS) {
            repository.addCustomer(
                userId = uid,
                name = customerName,
                phone = "01799001122",
                shopAddress = "Khatunganj",
                openingBalance = 15000.0,
            )
        }
        assertTrue("Offline addCustomer should succeed immediately", addCustomerResult.isSuccess)
        val customerId = addCustomerResult.getOrThrow()

        val saleResult = withTimeout(OFFLINE_WRITE_TIMEOUT_MS) {
            repository.recordTransaction(
                userId = uid,
                transaction = LedgerTransaction(
                    userId = uid,
                    customerId = customerId,
                    customerName = customerName,
                    type = LedgerTransaction.TYPE_SALE,
                    riceVariety = "Miniket",
                    unit = LedgerTransaction.UNIT_BAG,
                    quantity = 10.0,
                    costPricePerUnit = 3300.0,
                    sellingPricePerUnit = 3550.0,
                    totalAmount = 35500.0,
                    paidAmount = 20000.0,
                    dueAdded = 15500.0,
                    grossProfit = 2500.0,
                    transactionDateMillis = System.currentTimeMillis(),
                    notes = "Recorded while offline",
                ),
            )
        }
        assertTrue("Offline recordTransaction should succeed immediately", saleResult.isSuccess)

        val expenseResult = withTimeout(OFFLINE_WRITE_TIMEOUT_MS) {
            repository.addExpense(
                userId = uid,
                frequency = BusinessExpense.FREQ_DAILY,
                category = "Transport / Van",
                amount = 900.0,
                notes = "Offline van fare",
                expenseDateMillis = System.currentTimeMillis(),
            )
        }
        assertTrue("Offline addExpense should succeed immediately", expenseResult.isSuccess)

        val loanResult = withTimeout(OFFLINE_WRITE_TIMEOUT_MS) {
            repository.addLoan(
                userId = uid,
                lenderName = "Sonali Bank",
                principalAmount = 100000.0,
                annualInterestRate = 13.0,
                startDateMillis = System.currentTimeMillis(),
                notes = "Offline loan",
            )
        }
        assertTrue("Offline addLoan should succeed immediately", loanResult.isSuccess)

        val importResult = withTimeout(OFFLINE_WRITE_TIMEOUT_MS) {
            repository.importScannedRows(
                userId = uid,
                existingCustomers = emptyList(),
                rows = listOf(
                    ScannedLedgerRow(
                        localId = "row_1",
                        customerName = customerName,
                        dateText = "12 Nov",
                        totalAmount = "5000",
                        paidAmount = "3000",
                        dueAmount = "2000",
                        notes = "Offline bulk entry",
                    )
                ),
            )
        }
        assertTrue("Offline importScannedRows should succeed immediately", importResult.isSuccess)
        assertEquals(1, importResult.getOrNull())

        // Verify local listeners emit the offline writes with hasPendingWrites = true
        val offlineCustomers = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeCustomersWithSync(uid).first { synced ->
                synced.items.any { it.id == customerId && it.name == customerName }
            }
        }
        assertTrue("Offline write should mark snapshot hasPendingWrites=true", offlineCustomers.hasPendingWrites)

        val offlineTransactions = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeTransactionsWithSync(uid).first { synced ->
                synced.items.any { it.customerId == customerId }
            }
        }
        assertTrue(offlineTransactions.hasPendingWrites)

        // Restore network and verify Firestore drains pending writes to the emulator
        firestore.enableNetwork().await()
        val syncedCustomers = withTimeout(SYNC_TIMEOUT_MS) {
            repository.observeCustomersWithSync(uid).first { synced ->
                synced.items.any { it.id == customerId } && !synced.hasPendingWrites
            }
        }
        assertFalse("Pending writes should clear once back online", syncedCustomers.hasPendingWrites)
    }

    @Test
    fun offlineClearAllUserData_readsCacheAndDeletesWithoutBlocking() = runBlocking {
        val uid = signInTestUser("offline.clear@test.com")
        val repository = LedgerRepository(
            db = firestore,
            isNetworkAvailable = { false },
        )

        firestore.disableNetwork().await()

        val id = withTimeout(OFFLINE_WRITE_TIMEOUT_MS) {
            repository.addCustomer(uid, "Temp Buyer", "01700", "Dhaka", 500.0).getOrThrow()
        }
        withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeCustomers(uid).first { list -> list.any { it.id == id } }
        }

        val clearResult = withTimeout(OFFLINE_WRITE_TIMEOUT_MS) {
            repository.clearAllUserData(uid)
        }
        assertTrue("Offline clearAllUserData should succeed", clearResult.isSuccess)

        val remaining = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeCustomers(uid).first { list -> list.none { it.id == id } }
        }
        assertTrue(remaining.none { it.id == id })
    }

    private companion object {
        const val OFFLINE_WRITE_TIMEOUT_MS = 2000L
        const val FLOW_TIMEOUT_MS = 3000L
        const val SYNC_TIMEOUT_MS = 6000L
    }
}
