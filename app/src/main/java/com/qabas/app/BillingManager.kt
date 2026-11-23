package com.qabas.app

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BillingManager(private val context: Context) : PurchasesUpdatedListener {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var billingClient: BillingClient

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _products = MutableStateFlow<Map<String, ProductDetails>>(emptyMap())
    val products: StateFlow<Map<String, ProductDetails>> = _products.asStateFlow()


    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Callbacks for purchases
    var onPurchaseSuccess: ((String) -> Unit)? = null
    var onPurchaseCanceled: (() -> Unit)? = null
    var onPurchaseError: ((String) -> Unit)? = null

    companion object {
        private const val TAG = "BillingManager"

        // Subscription Product IDs
        const val SUB_MONTHLY_PRO = "pro_1m"
        const val SUB_ANNUAL_PRO = "pro_1y"

        // In-App Coins Product IDs
        const val COIN_500 = "coin_500"
        const val COIN_1200 = "coin_1200"
        const val COIN_3000 = "coin_3000"
        const val COIN_10000 = "coin_10000"

        @Volatile
        private var INSTANCE: BillingManager? = null

        fun getInstance(context: Context): BillingManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BillingManager(context.applicationContext ?: context).also { INSTANCE = it }
            }
        }
    }

    init {
        val pendingPurchasesParams = PendingPurchasesParams.newBuilder()
            .enableOneTimeProducts()
            .build()

        billingClient = BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases(pendingPurchasesParams)
            .build()

        startConnection()
    }

    private fun startConnection() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Billing Setup Finished successfully.")
                    _isReady.value = true
                    scope.launch {
                        queryAllProducts()
                        checkActiveSubscriptions()
                    }
                } else {
                    _isReady.value = false
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.BILLING_UNAVAILABLE ||
                        billingResult.responseCode == BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED) {
                        Log.i(TAG, "Google Play Billing is not supported or unavailable on this device/environment (code=${billingResult.responseCode}). Safe fallback active.")
                    } else {
                        Log.w(TAG, "Billing Setup status: code=${billingResult.responseCode} msg=${billingResult.debugMessage}")
                    }
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.d(TAG, "Billing Service Disconnected.")
                val wasReady = _isReady.value
                _isReady.value = false
                if (wasReady) {
                    scope.launch {
                        delay(5000)
                        if (!_isReady.value) {
                            startConnection()
                        }
                    }
                }
            }
        })
    }

    suspend fun queryAllProducts() {
        if (!billingClient.isReady) {
            Log.w(TAG, "queryAllProducts: BillingClient is not ready")
            return
        }

        try {
            val productList = listOf(
                // In-App Consumable Products
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(COIN_500)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build(),
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(COIN_1200)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build(),
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(COIN_3000)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build(),
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(COIN_10000)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build(),
                // Subscriptions
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(SUB_MONTHLY_PRO)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build(),
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(SUB_ANNUAL_PRO)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            )

            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(productList)
                .build()

            val result = billingClient.queryProductDetails(params)
            if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val list = result.productDetailsList ?: emptyList()
                val map = list.associateBy { it.productId }
                _products.value = map
                Log.d(TAG, "Fetched ${list.size} products from Google Play")
            } else {
                Log.w(TAG, "Error querying product details: ${result.billingResult.debugMessage}")
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Log.e(TAG, "Exception querying products: ${e.message}", e)
        }
    }

    suspend fun checkActiveSubscriptions(): Boolean {
        if (!billingClient.isReady) return false

        try {
            val params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()

            val purchasesResult = billingClient.queryPurchasesAsync(params)
            if (purchasesResult.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val purchases = purchasesResult.purchasesList
                val hasActiveSub = purchases.any { purchase ->
                    purchase.purchaseState == Purchase.PurchaseState.PURCHASED
                }

                if (hasActiveSub) {
                    val accountService = AppServices.getAccountService(context)
                    accountService.isPremium = true
                }

                // Process unacknowledged active purchases
                for (purchase in purchases) {
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED && !purchase.isAcknowledged) {
                        acknowledgePurchase(purchase)
                    }
                }
                return hasActiveSub
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Log.e(TAG, "Exception checking active subscriptions: ${e.message}", e)
        }
        return false
    }

    fun restorePurchases(onResult: (Boolean, String) -> Unit) {
        scope.launch {
            _isLoading.value = true
            if (!billingClient.isReady) {
                _isLoading.value = false
                withContext(Dispatchers.Main) {
                    onResult(false, Translator.tr("خدمة الدفع غير متصلة، يرجى المحاولة لاحقاً"))
                }
                return@launch
            }

            try {
                val subParams = QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
                val subResult = billingClient.queryPurchasesAsync(subParams)

                val inAppParams = QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
                val inAppResult = billingClient.queryPurchasesAsync(inAppParams)

                // (R48) فحص صادق لنتيجتي الاستعلام: عند فشل الاستعلام تكون
                // purchasesList فارغة، فكان الفشل يُقرأ سابقاً «لا مشتريات».
                if (subResult.billingResult.responseCode != BillingClient.BillingResponseCode.OK ||
                    inAppResult.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                    Log.w(TAG, "restorePurchases: query failed sub=${subResult.billingResult.responseCode} inApp=${inAppResult.billingResult.responseCode}")
                    withContext(Dispatchers.Main) {
                        _isLoading.value = false
                        onResult(false, Translator.tr("تعذّر الوصول إلى Google Play للتحقق من مشترياتك، تحقق من اتصال الإنترنت وحاول لاحقاً"))
                    }
                    return@launch
                }

                val activeSubPurchases = subResult.purchasesList.filter {
                    it.purchaseState == Purchase.PurchaseState.PURCHASED
                }

                if (activeSubPurchases.isNotEmpty()) {
                    val accountService = AppServices.getAccountService(context)
                    accountService.isPremium = true

                    activeSubPurchases.forEach { purchase ->
                        if (!purchase.isAcknowledged) {
                            acknowledgePurchase(purchase)
                        }
                    }
                }

                // (R48) مشتريات الدفع الواحد: كانت inAppResult تُحسب ولا تُفحص
                // أبداً، فأي رصيد عملات اشتُري ولم يُستهلك (انقطاع أو إغلاق
                // بين الشراء والاستهلاك) لا يُستعاد أبداً. المنتجات لمرة
                // واحدة هنا حزم عملات استهلاكية فقط (لا منتج غير استهلاكي
                // مباع اليوم)، فالمسار الصحيح لها الاستهلاك لا الإقرار
                // (الإقرار لغير الاستهلاكي). المنح مباشرة عبر PaymentService
                // لا عبر onPurchaseSuccess: خانة رد النداء الواحدة مشتركة
                // بين الشاشات، وPremiumUpgradeScreen تمنح Premium لأي
                // productId فكانت ستمنح برو مقابل حزمة عملات، وPaymentScreen
                // كانت ستمنح مرتين إن منحت هنا واستدعيت رد النداء أيضاً.
                var restoredCoins = 0
                var failedCoinRestores = 0
                for (purchase in inAppResult.purchasesList) {
                    if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) continue
                    val productId = purchase.products.firstOrNull() ?: continue
                    val coins = coinsForProduct(productId)
                    if (coins > 0) {
                        val consumeParams = ConsumeParams.newBuilder()
                            .setPurchaseToken(purchase.purchaseToken)
                            .build()
                        val consumeResult = billingClient.consumePurchase(consumeParams)
                        if (consumeResult.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                            CloudServices.Database.recordPurchase(productId, purchase.purchaseToken)
                            AppServices.getPaymentService(context).recharge(coins)
                            restoredCoins += coins
                            Log.d(TAG, "Restored $coins coins from $productId")
                        } else {
                            failedCoinRestores++
                            Log.w(TAG, "restorePurchases: consume failed for $productId code=${consumeResult.billingResult.responseCode}")
                        }
                    } else if (!purchase.isAcknowledged) {
                        // منتج لمرة واحدة غير استهلاكي (لا يُباع اليوم):
                        // إقرار فقط حتى لا يسترجعه Play بعد 3 أيام، بلا منح
                        // ميزة غير معروفة.
                        acknowledgePurchase(purchase)
                        CloudServices.Database.recordPurchase(productId, purchase.purchaseToken)
                    }
                }

                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                    val restoredSubs = activeSubPurchases.isNotEmpty()
                    if (restoredSubs || restoredCoins > 0) {
                        // نجاح (كامل أو جزئي): أي استعادة فاشلة تُذكر في
                        // الرسالة نفسها لا تُبتلع بصمت.
                        val message = buildString {
                            if (restoredSubs) append("تمت استعادة اشتراكك بنجاح!")
                            if (restoredCoins > 0) {
                                if (isNotEmpty()) append(" ")
                                if (restoredSubs) append("واستُعيدت $restoredCoins عملة إلى رصيدك.")
                                else append("تمت استعادة $restoredCoins عملة إلى رصيدك بنجاح!")
                            }
                            if (failedCoinRestores > 0) {
                                if (isNotEmpty()) append(" ")
                                append("تعذّرت استعادة بعض المشتريات، حاول لاحقاً.")
                            }
                        }
                        onResult(true, Translator.tr(message))
                    } else if (failedCoinRestores > 0) {
                        onResult(false, Translator.tr("تعذّرت استعادة مشترياتك السابقة، حاول لاحقاً"))
                    } else {
                        onResult(false, Translator.tr("لم يتم العثور على اشتراكات أو مشتريات سابقة في حساب Google Play"))
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                    onResult(false, Translator.tr("حدث خطأ أثناء استعادة المشتريات: ${e.localizedMessage}"))
                }
            }
        }
    }

    fun launchBillingFlow(
        activity: Activity,
        productId: String
    ) {
        if (!billingClient.isReady) {
            Log.w(TAG, "BillingClient is not ready.")
            scope.launch(Dispatchers.Main) {
                onPurchaseError?.invoke("خدمة الدفع غير جاهزة حالياً، يرجى التأكد من اتصال الإنترنت وتسجيل الدخول في Google Play")
            }
            return
        }

        val productDetails = _products.value[productId]
        if (productDetails == null) {
            Log.w(TAG, "Product $productId not found in Google Play products.")
            scope.launch(Dispatchers.Main) {
                onPurchaseError?.invoke("المنتج غير متوفر حالياً في متجر Google Play")
            }
            return
        }

        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .apply {
                    val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken
                    if (offerToken != null) {
                        setOfferToken(offerToken)
                    }
                }
                .build()
        )

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        val response = billingClient.launchBillingFlow(activity, billingFlowParams)
        if (response.responseCode != BillingClient.BillingResponseCode.OK) {
            Log.e(TAG, "launchBillingFlow failed: code=${response.responseCode} msg=${response.debugMessage}")
            scope.launch(Dispatchers.Main) {
                onPurchaseError?.invoke(response.debugMessage.ifEmpty { "فشل بدء عملية الشراء (كود: ${response.responseCode})" })
            }
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                handlePurchase(purchase)
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Log.d(TAG, "User canceled the purchase")
            scope.launch(Dispatchers.Main) {
                onPurchaseCanceled?.invoke()
            }
        } else {
            Log.e(TAG, "Purchase error: code=${billingResult.responseCode} msg=${billingResult.debugMessage}")
            scope.launch(Dispatchers.Main) {
                onPurchaseError?.invoke(billingResult.debugMessage.ifEmpty { "Error code: ${billingResult.responseCode}" })
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        scope.launch {
            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                val isConsumable = purchase.products.any { it.startsWith("coin_") }
                val productId = purchase.products.firstOrNull() ?: ""

                if (isConsumable) {
                    val consumeParams = ConsumeParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()
                    val consumeResult = billingClient.consumePurchase(consumeParams)
                    if (consumeResult.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        Log.d(TAG, "Purchase consumed successfully: $productId")
                        CloudServices.Database.recordPurchase(productId, purchase.purchaseToken)
                        withContext(Dispatchers.Main) {
                            onPurchaseSuccess?.invoke(productId)
                        }
                    }
                } else {
                    // Subscription or non-consumable
                    if (!purchase.isAcknowledged) {
                        val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(purchase.purchaseToken)
                            .build()
                        val ackResult = billingClient.acknowledgePurchase(acknowledgePurchaseParams)
                        if (ackResult.responseCode == BillingClient.BillingResponseCode.OK) {
                            Log.d(TAG, "Subscription purchase acknowledged: $productId")
                            CloudServices.Database.recordPurchase(productId, purchase.purchaseToken)
                            val accountService = AppServices.getAccountService(context)
                            accountService.isPremium = true
                            withContext(Dispatchers.Main) {
                                onPurchaseSuccess?.invoke(productId)
                            }
                        }
                    } else {
                        val accountService = AppServices.getAccountService(context)
                        accountService.isPremium = true
                        withContext(Dispatchers.Main) {
                            onPurchaseSuccess?.invoke(productId)
                        }
                    }
                }
            }
        }
    }

    private suspend fun acknowledgePurchase(purchase: Purchase) {
        try {
            val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            val ackResult = billingClient.acknowledgePurchase(acknowledgePurchaseParams)
            if (ackResult.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.d(TAG, "Background acknowledge success: ${purchase.products}")
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Log.e(TAG, "Failed to acknowledge purchase: ${e.message}")
        }
    }

    /**
     * (R48) عدد عملات حزمة الدفع الواحد، أو 0 إن لم تكن حزمة عملات معروفة.
     * نفس التعيين في PaymentScreen.kt (منح الشراء الفوري).
     */
    private fun coinsForProduct(productId: String): Int = when (productId) {
        COIN_500 -> 500
        COIN_1200 -> 1200
        COIN_3000 -> 3000
        COIN_10000 -> 10000
        else -> 0
    }

    fun getFormattedPrice(productId: String, fallbackPrice: String): String {
        val details = _products.value[productId] ?: return fallbackPrice
        return when {
            details.productType == BillingClient.ProductType.SUBS -> {
                details.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice
                    ?: fallbackPrice
            }
            details.productType == BillingClient.ProductType.INAPP -> {
                details.oneTimePurchaseOfferDetails?.formattedPrice ?: fallbackPrice
            }
            else -> fallbackPrice
        }
    }
}
