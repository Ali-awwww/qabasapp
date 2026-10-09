package com.qabas.app

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * مدير العمليات المالية والمبيعات (TransactionManager)
 * يدير تدفق المعاملات الحقيقية والمحلية، والتزامن مع السحابة،
 * مع إمكانية مسح البيانات التجريبية لحماية سلامة بيئة الإنتاج.
 */
object TransactionManager {
    private const val PREFS_NAME = "qabas_transactions_prefs"
    private const val KEY_TRANSACTIONS = "cached_transactions_json"

    private val _transactionsFlow = MutableStateFlow<List<Map<String, Any>>>(emptyList())
    val transactionsFlow: StateFlow<List<Map<String, Any>>> = _transactionsFlow

    private var isInitialized = false

    fun init(context: Context) {
        if (!isInitialized) {
            isInitialized = true
            loadFromPrefs(context)
        }
    }

    fun loadFromPrefs(context: Context): List<Map<String, Any>> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_TRANSACTIONS, "[]") ?: "[]"
        val list = parseJson(jsonStr)
        _transactionsFlow.value = list
        return list
    }

    fun addTransaction(context: Context, tx: Map<String, Any>) {
        val current = _transactionsFlow.value.toMutableList()
        current.add(0, tx)
        saveToPrefs(context, current)
        _transactionsFlow.value = current
    }

    fun recordPurchase(
        context: Context,
        id: String = "tx_${System.currentTimeMillis()}",
        productId: String = "pro_1m",
        priceAmount: Double = 9.99,
        userId: String = "dev_admin",
        currency: String = "USD"
    ) {
        val tx = mapOf(
            "id" to id,
            "productId" to productId,
            "priceAmount" to priceAmount,
            "userId" to userId,
            "currency" to currency,
            "purchaseToken" to "tok_${System.currentTimeMillis()}",
            "timestamp" to System.currentTimeMillis()
        )
        addTransaction(context, tx)
    }

    fun clearAllTransactions(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_TRANSACTIONS).apply()
        _transactionsFlow.value = emptyList()
    }

    private fun saveToPrefs(context: Context, list: List<Map<String, Any>>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonArray = JSONArray()
        list.forEach { item ->
            val obj = JSONObject()
            item.forEach { (k, v) -> obj.put(k, v) }
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_TRANSACTIONS, jsonArray.toString()).apply()
    }

    private fun parseJson(jsonStr: String): List<Map<String, Any>> {
        return runCatching {
            val array = JSONArray(jsonStr)
            val result = mutableListOf<Map<String, Any>>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val map = mutableMapOf<String, Any>()
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    map[k] = obj.get(k)
                }
                result.add(map)
            }
            result
        }.getOrDefault(emptyList())
    }
}
