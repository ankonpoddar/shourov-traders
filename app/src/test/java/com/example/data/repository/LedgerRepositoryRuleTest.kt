package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.BusinessExpense
import com.example.data.model.LedgerTransaction
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class LedgerRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun addAndObserveCustomer_authenticatedOwner_succeeds() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = LedgerRepository(firestore)
        val uniqueName = "Rahim Store ${UUID.randomUUID().toString().take(6)}"

        val createResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.addCustomer(
                userId = uid,
                name = uniqueName,
                phone = "01711223344",
                shopAddress = "Badamtoli",
                openingBalance = 5000.0
            )
        }
        assertTrue(createResult.isSuccess)
        val customerId = createResult.getOrThrow()

        val customers = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeCustomers(uid).first { list -> list.any { it.id == customerId } }
        }
        assertTrue(customers.any { it.id == customerId && it.name == uniqueName })
    }

    @Test
    fun recordSaleExpenseLoan_authenticatedOwner_succeeds() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = LedgerRepository(firestore)

        val txResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.recordTransaction(
                userId = uid,
                transaction = LedgerTransaction(
                    userId = uid,
                    customerId = "",
                    customerName = "Walk-in Cash Customer",
                    type = LedgerTransaction.TYPE_SALE,
                    riceVariety = "Miniket",
                    unit = LedgerTransaction.UNIT_BAG,
                    quantity = 5.0,
                    costPricePerUnit = 3200.0,
                    sellingPricePerUnit = 3500.0,
                    totalAmount = 17500.0,
                    paidAmount = 17500.0,
                    dueAdded = 0.0,
                    grossProfit = 1500.0,
                    transactionDateMillis = System.currentTimeMillis(),
                    notes = "Cash counter sale"
                )
            )
        }
        assertTrue(txResult.isSuccess)

        val expResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.addExpense(
                userId = uid,
                frequency = BusinessExpense.FREQ_DAILY,
                category = "Transport / Van",
                amount = 750.0,
                notes = "Van rent",
                expenseDateMillis = System.currentTimeMillis()
            )
        }
        assertTrue(expResult.isSuccess)

        val loanResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.addLoan(
                userId = uid,
                lenderName = "Islami Bank",
                principalAmount = 200000.0,
                annualInterestRate = 13.0,
                startDateMillis = System.currentTimeMillis(),
                notes = "Working capital"
            )
        }
        assertTrue(loanResult.isSuccess)
    }

    @Test
    fun observeCustomers_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = LedgerRepository(firestore)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.addCustomer(aliceUid, "Alice Buyer", "01700", "Dhaka", 1000.0).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = LedgerRepository(firestore)
        val exception = runCatching {
            withTimeout(FLOW_TIMEOUT_MS) {
                bobRepo.observeCustomers(aliceUid).first()
            }
        }.exceptionOrNull()

        val firestoreException = checkNotNull(exception?.unwrapFirestoreException()) {
            "Expected nested FirebaseFirestoreException, got $exception"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
    }

    @Test
    fun observeCustomers_unauthenticated_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = LedgerRepository(firestore)

        val exception = runCatching {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeCustomers("some_user_uid").first()
            }
        }.exceptionOrNull()

        val firestoreException = checkNotNull(exception?.unwrapFirestoreException()) {
            "Expected nested FirebaseFirestoreException, got $exception"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
    }

    private companion object {
        const val ALICE_EMAIL = "alice.rice@test.com"
        const val BOB_EMAIL = "bob.rice@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
