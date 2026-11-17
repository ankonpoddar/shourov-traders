package com.example.data.backup

import com.example.data.model.BankLoan
import com.example.data.model.BusinessExpense
import com.example.data.model.Customer
import com.example.data.model.LedgerTransaction
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object LedgerBackupSerializer {

    private val FIRESTORE_ID_REGEX = Regex("^[a-zA-Z0-9_\\-]+$")

    fun toJson(payload: ShouravBackupPayload): String {
        val root = JSONObject().apply {
            put("schemaVersion", payload.schemaVersion)
            put("appName", payload.appName)
            put("userId", payload.userId)
            put("userEmail", payload.userEmail)
            put("exportedAtMillis", payload.exportedAtMillis)
            put("customers", JSONArray().apply {
                payload.customers.forEach { customer ->
                    put(
                        JSONObject().apply {
                            put("id", customer.id)
                            put("name", customer.name)
                            put("phone", customer.phone)
                            put("shopAddress", customer.shopAddress)
                            put("openingBalance", sanitizeFinite(customer.openingBalance))
                        }
                    )
                }
            })
            put("transactions", JSONArray().apply {
                payload.transactions.forEach { tx ->
                    put(
                        JSONObject().apply {
                            put("id", tx.id)
                            put("customerId", tx.customerId)
                            put("customerName", tx.customerName)
                            put("type", tx.type)
                            put("riceVariety", tx.riceVariety)
                            put("unit", tx.unit)
                            put("quantity", sanitizeFinite(tx.quantity))
                            put("costPricePerUnit", sanitizeFinite(tx.costPricePerUnit))
                            put("sellingPricePerUnit", sanitizeFinite(tx.sellingPricePerUnit))
                            put("totalAmount", sanitizeFinite(tx.totalAmount))
                            put("paidAmount", sanitizeFinite(tx.paidAmount))
                            put("dueAdded", sanitizeFinite(tx.dueAdded))
                            put("grossProfit", sanitizeFinite(tx.grossProfit))
                            put("transactionDateMillis", tx.transactionDateMillis.coerceAtLeast(0L))
                            put("notes", tx.notes)
                        }
                    )
                }
            })
            put("expenses", JSONArray().apply {
                payload.expenses.forEach { exp ->
                    put(
                        JSONObject().apply {
                            put("id", exp.id)
                            put("frequency", exp.frequency)
                            put("category", exp.category)
                            put("amount", sanitizeFinite(exp.amount))
                            put("notes", exp.notes)
                            put("expenseDateMillis", exp.expenseDateMillis.coerceAtLeast(0L))
                        }
                    )
                }
            })
            put("loans", JSONArray().apply {
                payload.loans.forEach { loan ->
                    put(
                        JSONObject().apply {
                            put("id", loan.id)
                            put("lenderName", loan.lenderName)
                            put("principalAmount", sanitizeFinite(loan.principalAmount))
                            put("annualInterestRate", sanitizeFinite(loan.annualInterestRate))
                            put("startDateMillis", loan.startDateMillis.coerceAtLeast(0L))
                            put("paidInstallmentsCount", loan.paidInstallmentsCount.coerceAtLeast(0L))
                            put("totalPrincipalRepaid", sanitizeFinite(loan.totalPrincipalRepaid))
                            put("notes", loan.notes)
                        }
                    )
                }
            })
        }
        return root.toString(2)
    }

    fun fromJson(jsonString: String): Result<ShouravBackupPayload> {
        val trimmed = jsonString.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Backup file is empty."))
        }
        return try {
            val root = JSONObject(trimmed)
            val appName = root.optString("appName", "")
            val hasAnyLedgerKey = root.has("customers") ||
                root.has("transactions") ||
                root.has("expenses") ||
                root.has("loans")
            if (!hasAnyLedgerKey || (appName.isNotEmpty() && appName != DriveBackupContract.APP_IDENTIFIER)) {
                return Result.failure(
                    IllegalArgumentException("This JSON file is not a valid Shourav Traders ledger backup.")
                )
            }

            val schemaVersion = root.optInt("schemaVersion", DriveBackupContract.SCHEMA_VERSION)
            if (schemaVersion < 1) {
                return Result.failure(IllegalArgumentException("Unsupported backup schema version: $schemaVersion"))
            }

            val customerIdRemap = mutableMapOf<String, String>()
            val customers = mutableListOf<Customer>()
            val customersArray = root.optJSONArray("customers") ?: JSONArray()
            for (i in 0 until customersArray.length()) {
                val obj = customersArray.optJSONObject(i) ?: continue
                val name = obj.optString("name", "").trim().take(100)
                if (name.isEmpty()) continue
                val rawId = obj.optString("id", "").trim()
                val safeId = ensureValidFirestoreId(rawId, "cust")
                if (rawId.isNotEmpty() && rawId != safeId) {
                    customerIdRemap[rawId] = safeId
                }
                customers.add(
                    Customer(
                        id = safeId,
                        name = name,
                        phone = obj.optString("phone", "").trim().take(30),
                        shopAddress = obj.optString("shopAddress", "").trim().take(200),
                        openingBalance = sanitizeFinite(obj.optDouble("openingBalance", 0.0)).coerceAtLeast(0.0),
                    )
                )
            }

            val transactions = mutableListOf<LedgerTransaction>()
            val txArray = root.optJSONArray("transactions") ?: JSONArray()
            for (i in 0 until txArray.length()) {
                val obj = txArray.optJSONObject(i) ?: continue
                val rawCustomerId = obj.optString("customerId", "").trim()
                val resolvedCustomerId = (customerIdRemap[rawCustomerId] ?: rawCustomerId).take(128)
                val customerName = obj.optString("customerName", "")
                    .trim()
                    .ifEmpty { "Walk-in Cash Customer" }
                    .take(100)
                val type = when (obj.optString("type", LedgerTransaction.TYPE_SALE)) {
                    LedgerTransaction.TYPE_PAYMENT -> LedgerTransaction.TYPE_PAYMENT
                    else -> LedgerTransaction.TYPE_SALE
                }
                val unit = when (obj.optString("unit", LedgerTransaction.UNIT_BAG)) {
                    LedgerTransaction.UNIT_KG -> LedgerTransaction.UNIT_KG
                    else -> LedgerTransaction.UNIT_BAG
                }
                transactions.add(
                    LedgerTransaction(
                        id = ensureValidFirestoreId(obj.optString("id", "").trim(), "tx"),
                        customerId = resolvedCustomerId,
                        customerName = customerName,
                        type = type,
                        riceVariety = obj.optString("riceVariety", "").trim().take(80),
                        unit = unit,
                        quantity = sanitizeFinite(obj.optDouble("quantity", 0.0)).coerceAtLeast(0.0),
                        costPricePerUnit = sanitizeFinite(obj.optDouble("costPricePerUnit", 0.0)).coerceAtLeast(0.0),
                        sellingPricePerUnit = sanitizeFinite(obj.optDouble("sellingPricePerUnit", 0.0)).coerceAtLeast(0.0),
                        totalAmount = sanitizeFinite(obj.optDouble("totalAmount", 0.0)).coerceAtLeast(0.0),
                        paidAmount = sanitizeFinite(obj.optDouble("paidAmount", 0.0)).coerceAtLeast(0.0),
                        dueAdded = sanitizeFinite(obj.optDouble("dueAdded", 0.0)),
                        grossProfit = sanitizeFinite(obj.optDouble("grossProfit", 0.0)),
                        transactionDateMillis = obj.optLong("transactionDateMillis", 0L).coerceAtLeast(0L),
                        notes = obj.optString("notes", "").trim().take(300),
                    )
                )
            }

            val expenses = mutableListOf<BusinessExpense>()
            val expArray = root.optJSONArray("expenses") ?: JSONArray()
            for (i in 0 until expArray.length()) {
                val obj = expArray.optJSONObject(i) ?: continue
                val amount = sanitizeFinite(obj.optDouble("amount", 0.0))
                if (amount <= 0.0) continue
                val frequency = when (obj.optString("frequency", BusinessExpense.FREQ_DAILY)) {
                    BusinessExpense.FREQ_MONTHLY -> BusinessExpense.FREQ_MONTHLY
                    else -> BusinessExpense.FREQ_DAILY
                }
                expenses.add(
                    BusinessExpense(
                        id = ensureValidFirestoreId(obj.optString("id", "").trim(), "exp"),
                        frequency = frequency,
                        category = obj.optString("category", "").trim().ifEmpty { "Misc" }.take(60),
                        amount = amount,
                        notes = obj.optString("notes", "").trim().take(300),
                        expenseDateMillis = obj.optLong("expenseDateMillis", 0L).coerceAtLeast(0L),
                    )
                )
            }

            val loans = mutableListOf<BankLoan>()
            val loansArray = root.optJSONArray("loans") ?: JSONArray()
            for (i in 0 until loansArray.length()) {
                val obj = loansArray.optJSONObject(i) ?: continue
                val principal = sanitizeFinite(obj.optDouble("principalAmount", 0.0))
                if (principal <= 0.0) continue
                loans.add(
                    BankLoan(
                        id = ensureValidFirestoreId(obj.optString("id", "").trim(), "loan"),
                        lenderName = obj.optString("lenderName", "").trim().ifEmpty { "Bank Loan" }.take(100),
                        principalAmount = principal,
                        annualInterestRate = sanitizeFinite(obj.optDouble("annualInterestRate", 13.0)).coerceIn(0.0, 100.0),
                        startDateMillis = obj.optLong("startDateMillis", 0L).coerceAtLeast(0L),
                        paidInstallmentsCount = obj.optLong("paidInstallmentsCount", 0L).coerceAtLeast(0L),
                        totalPrincipalRepaid = sanitizeFinite(obj.optDouble("totalPrincipalRepaid", 0.0)).coerceAtLeast(0.0),
                        notes = obj.optString("notes", "").trim().take(300),
                    )
                )
            }

            Result.success(
                ShouravBackupPayload(
                    schemaVersion = schemaVersion,
                    appName = appName.ifEmpty { DriveBackupContract.APP_IDENTIFIER },
                    userId = root.optString("userId", ""),
                    userEmail = root.optString("userEmail", ""),
                    exportedAtMillis = root.optLong("exportedAtMillis", 0L).coerceAtLeast(0L),
                    customers = customers,
                    transactions = transactions,
                    expenses = expenses,
                    loans = loans,
                )
            )
        } catch (e: Exception) {
            Result.failure(
                IllegalArgumentException("Unable to read backup JSON: ${e.localizedMessage ?: "Invalid format"}", e)
            )
        }
    }

    private fun ensureValidFirestoreId(rawId: String, prefix: String): String {
        if (rawId.length in 1..128 && FIRESTORE_ID_REGEX.matches(rawId)) {
            return rawId
        }
        return "${prefix}_${UUID.randomUUID().toString().replace("-", "")}"
    }

    private fun sanitizeFinite(value: Double): Double =
        if (value.isFinite()) value else 0.0
}
