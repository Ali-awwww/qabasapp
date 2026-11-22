package com.qabas.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    override var userBalance: Int
        get() = AppServices.getAccountService(context).walletBalance
        set(value) {
            AppServices.getAccountService(context).walletBalance = value.coerceAtLeast(0)
        }

    override suspend fun initialize(context: Context, publishableKey: String) {
        withContext(Dispatchers.IO) {
            // Initialize Google Play Billing connection transparently instead of simulated Stripe stubs
            BillingManager.getInstance(context)
        }
    }

    override suspend fun createPaymentIntent(amount: Int, currency: String): String = withContext(Dispatchers.IO) {
        require(amount > 0) { "Payment amount must be greater than zero" }
        throw IllegalStateException("بوابة الدفع المباشر بالبطاقة غير مفعلة — استخدم متجر Google Play Billing المعتمد.")
    }

    override suspend fun confirmPayment(context: Context, clientSecret: String): Boolean = withContext(Dispatchers.IO) {
        // Never confirm test/simulated Stripe secrets
        false
    }

    override suspend fun processPayment(amount: Int): Boolean = withContext(Dispatchers.IO) {
        if (amount <= 0) return@withContext false
        val current = userBalance
        if (current >= amount) {
            userBalance = current - amount
            true
        } else {
            false
        }
    }

    override suspend fun recharge(amount: Int) = withContext(Dispatchers.IO) {
        require(amount > 0) { "Recharge amount must be greater than zero" }
        userBalance += amount
    }

    override suspend fun generateInvoice(context: Context, projectId: String, amount: Int): String = withContext(Dispatchers.IO) {
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        val invoiceText = """
            ===============================
               فاتورة استوديو خادم المسلم
            ===============================
            رقم المشروع: $projectId
            التاريخ: $date
            المبلغ المدفوع: $amount عملة
            حالة الدفع: ناجح
            ===============================
            شكراً لثقتكم بنا!
        """.trimIndent()

        val file = File(context.cacheDir, "invoice_$projectId.txt")
        file.writeText(invoiceText)
        file.absolutePath
    }
}
