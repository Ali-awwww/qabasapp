package com.qabas.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

interface PaymentService {
    var userBalance: Int
    suspend fun initialize(context: Context, publishableKey: String)
    suspend fun createPaymentIntent(amount: Int, currency: String): String
    suspend fun confirmPayment(context: Context, clientSecret: String): Boolean
    suspend fun processPayment(amount: Int): Boolean
    suspend fun recharge(amount: Int)
    suspend fun generateInvoice(context: Context, projectId: String, amount: Int): String
}

class RealPaymentService(private val context: Context) : PaymentService {
    private val prefs = context.getSharedPreferences("real_payment_db", Context.MODE_PRIVATE)
    
    override var userBalance: Int
        get() {
            val accountService = AppServices.getAccountService(context)
            return accountService.walletBalance
        }
        set(value) {
            val accountService = AppServices.getAccountService(context)
            accountService.walletBalance = value
        }

    override suspend fun initialize(context: Context, publishableKey: String) {
        // In a real Stripe integration, we would call PaymentConfiguration.init(context, publishableKey)
        withContext(Dispatchers.IO) {
            delay(500)
            println("Stripe initialized with key: $publishableKey")
        }
    }

    override suspend fun createPaymentIntent(amount: Int, currency: String): String = withContext(Dispatchers.IO) {
        // Normally calls backend to get client_secret. Here we simulate a real backend response.
        delay(1000)
        return@withContext "pi_test_123_secret_${System.currentTimeMillis()}"
    }

    override suspend fun confirmPayment(context: Context, clientSecret: String): Boolean = withContext(Dispatchers.IO) {
        // Normally calls PaymentSheet to confirm. Simulate success.
        delay(1500)
        return@withContext clientSecret.startsWith("pi_test")
    }

    override suspend fun processPayment(amount: Int): Boolean = withContext(Dispatchers.IO) {
        delay(1000)
        if (userBalance >= amount) {
            userBalance -= amount
            return@withContext true
        }
        return@withContext false
    }

    override suspend fun recharge(amount: Int) = withContext(Dispatchers.IO) {
        // In reality, this is called after successful confirmPayment
        userBalance += amount
    }

    override suspend fun generateInvoice(context: Context, projectId: String, amount: Int): String = withContext(Dispatchers.IO) {
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        val invoiceText = """
            ===============================
               فاتورة استوديو قبس
            ===============================
            رقم المشروع: $projectId
            التاريخ: $date
            المبلغ المدفوع: $amount$
            حالة الدفع: ناجح
            ===============================
            شكراً لثقتكم بنا!
        """.trimIndent()
        
        val file = File(context.cacheDir, "invoice_$projectId.txt")
        file.writeText(invoiceText)
        return@withContext file.absolutePath
    }
}

