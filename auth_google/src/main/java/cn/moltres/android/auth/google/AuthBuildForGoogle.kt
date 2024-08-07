package cn.moltres.android.auth.google

import android.app.Activity
import cn.moltres.android.auth.AbsAuthBuildForGoogle
import cn.moltres.android.auth.GoogleProductDetails
import cn.moltres.android.auth.GoogleProductType
import cn.moltres.android.auth.Auth
import cn.moltres.android.auth.ReplacementMode
import com.android.billingclient.api.*
import com.android.billingclient.api.BillingClient.ProductType
import kotlinx.coroutines.*
import org.json.JSONObject
import kotlin.coroutines.resume

class AuthBuildForGoogle: AbsAuthBuildForGoogle() {
    companion object {
        private var mClient: BillingClient? = null
        private var mClientListener: ((billingResult: BillingResult, purchases: List<Purchase>?) -> Unit)? = null
        private var mClientExtListener: ((result: List<JSONObject>?) -> Unit)? = null
        private var mConnectionListener: (() -> Unit)? = null
    }

    private fun initClient(
        clientListener: ((billingResult: BillingResult, purchases: List<Purchase>?) -> Unit)? = null,
        connectionListener: () -> Unit
    ) {
        mClientListener = clientListener
        mConnectionListener = connectionListener
        if (mClient == null) {
            // 构建 Client, 添加交易更新监听; listener 可接收应用中所有购买交易的更新
            mClient = BillingClient.newBuilder(Auth.application)
                .setListener { billingResult, purchases ->
                    if (mClientListener != null) {
                        mClientListener?.invoke(billingResult, purchases)
                    } else if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        mClientExtListener?.invoke(
                            purchases?.map { JSONObject(it.originalJson) }.apply {
                                Auth.logCallback?.invoke("$with-setPurchasesUpdatedListener: $this")
                            }
                        )
                    } else {
                        Auth.logCallback?.invoke("newBuilder Listener: ${billingResult.responseCode}  ${billingResult.debugMessage}")
                    }
                }
                .enablePendingPurchases()
                .build()
        }
        if (mClient?.connectionState != BillingClient.ConnectionState.CONNECTED) {
            // 开始尝试建立与 Google Play 的连接
            mClient!!.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    when (result.responseCode) {
                        BillingClient.BillingResponseCode.OK -> mConnectionListener?.invoke()
                        BillingClient.BillingResponseCode.USER_CANCELED -> resultCancel()
                        else -> resultError("GooglePlay 连接失败 请重试: code=${result.responseCode}  msg=${result.debugMessage}", null, null, 1002)
                    }
                }
                override fun onBillingServiceDisconnected() {
                    resultError("GooglePlay 断开连接, 请重试", null, null, 1001)
                }
            })
        } else {
            mConnectionListener?.invoke()
        }
    }

    override fun destroy(activity: Activity?) {
        super.destroy(activity)
        mClientListener = null
        mConnectionListener = null
    }

    override fun setPurchasesUpdatedListener(listener: (result: List<JSONObject>?) -> Unit) {
        mClientExtListener = listener
    }

    override suspend fun payProductQuery(
        productList: List<String>,
        productType: GoogleProductType
    ) = suspendCancellableCoroutine { coroutine ->
        mAction = "payProductQuery"
        mCallback = { coroutine.resume(it) }
        if (productList.isEmpty()) {
            resultError("payProductQuery productList 参数不能为空")
        } else {
            initClient {
                val queryProductDetailsParams = QueryProductDetailsParams.newBuilder()
                    .setProductList(productList.map { productId ->
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(productId)
                            .setProductType(
                                when (productType) {
                                    GoogleProductType.INAPP -> ProductType.INAPP
                                    GoogleProductType.SUBS -> ProductType.SUBS
                                }
                            )
                            .build()
                    })
                    .build()
                mClient?.queryProductDetailsAsync(queryProductDetailsParams) { billingResult, productDetailsList ->
                    when (billingResult.responseCode) {
                        BillingClient.BillingResponseCode.OK -> {
                            val list = productDetailsList.map { productDetails ->
                                val oneTimePurchaseOfferDetails = productDetails.oneTimePurchaseOfferDetails?.let {
                                    GoogleProductDetails.OneTimePurchaseOfferDetails(
                                        it.formattedPrice,
                                        it.priceAmountMicros,
                                        it.priceCurrencyCode,
                                        it.zza()
                                    )
                                }
                                val subscriptionOfferDetails = productDetails.subscriptionOfferDetails?.let { list ->
                                    list.map {
                                        val pricingPhases = GoogleProductDetails.PricingPhases(
                                            it.pricingPhases.pricingPhaseList.map { pp ->
                                                GoogleProductDetails.PricingPhase(
                                                    pp.formattedPrice,
                                                    pp.priceAmountMicros,
                                                    pp.priceCurrencyCode,
                                                    pp.billingPeriod,
                                                    pp.billingCycleCount,
                                                    pp.recurrenceMode
                                                )
                                            }
                                        )
                                        GoogleProductDetails.SubscriptionOfferDetails(
                                            it.offerToken,
                                            it.offerTags,
                                            pricingPhases
                                        )
                                    }
                                }
                                GoogleProductDetails(
                                    productDetails,
                                    productDetails.toString(),
                                    productDetails.productId,
                                    productDetails.productType,
                                    productDetails.title,
                                    productDetails.name,
                                    productDetails.zza(),
                                    productDetails.description,
                                    oneTimePurchaseOfferDetails,
                                    subscriptionOfferDetails
                                )
                            }
                            resultSuccess("查询商品成功", null, null, list)
                        }
                        BillingClient.BillingResponseCode.USER_CANCELED -> resultCancel()
                        else -> resultError("查询商品失败: code=${billingResult.responseCode}  msg=${billingResult.debugMessage}")
                    }
                }
            }
        }
    }

    private fun getActivity(activity: Activity?, callback: (Activity, Activity?) -> Unit) {
        if (activity == null) {
            AuthActivityForGoogle.callbackActivity = {
                callback(it, it)
            }
            startAuthActivity(AuthActivityForGoogle::class.java)
        } else {
            callback(activity, null)
        }
    }

    override suspend fun pay(
        activity: Activity,
        googleProductDetails: GoogleProductDetails,
        selectedOfferToken: String?,
        oldPurchaseToken: String?,
        replacementMode: ReplacementMode,
        isOfferPersonalized: Boolean,
    ) = suspendCancellableCoroutine { coroutine ->
        mAction = "pay"
        mCallback = { coroutine.resume(it) }
        initClient({ billingResult, purchases ->
            when (billingResult.responseCode) {
                BillingClient.BillingResponseCode.OK -> {
                    val list = purchases?.map { JSONObject(it.originalJson) }
                    resultSuccess("购买交易更新", null, null, list)
                }
                BillingClient.BillingResponseCode.USER_CANCELED -> resultCancel()
                else -> resultError("购买交易更新: code=${billingResult.responseCode}  msg=${billingResult.debugMessage}")
            }
        }, {
            val pd = googleProductDetails.productDetails as ProductDetails
            val productDetailsParamsList = listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    // retrieve a value for "productDetails" by calling queryProductDetailsAsync()
                    .setProductDetails(pd)
                    .apply {
                        // to get an offer token, call ProductDetails.subscriptionOfferDetails()
                        // for a list of offers that are available to the user
                        selectedOfferToken?.let { setOfferToken(selectedOfferToken) }
                    }
                    .build()
            )
            val billingFlowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(productDetailsParamsList)
                .apply { // 升降级时参数设置
                    if (!oldPurchaseToken.isNullOrEmpty()) {
                        setSubscriptionUpdateParams(
                            BillingFlowParams.SubscriptionUpdateParams.newBuilder()
                                .setOldPurchaseToken(oldPurchaseToken)
                                .setSubscriptionReplacementMode(replacementMode.code)
                                .build()
                        )
                    }
                }
                .setIsOfferPersonalized(isOfferPersonalized)
                .build()
            // Launch the billing flow
            val billingResult = mClient!!.launchBillingFlow(activity, billingFlowParams)
            when (billingResult.responseCode) {
                BillingClient.BillingResponseCode.OK -> {} // 走 newClient 回调监听
                BillingClient.BillingResponseCode.USER_CANCELED -> resultCancel()
                else -> resultError("启动购买流程失败: code=${billingResult.responseCode}  msg=${billingResult.debugMessage}")
            }
        })
    }

    override suspend fun payConsume(purchaseToken: String) = suspendCancellableCoroutine { coroutine ->
        mAction = "payConsume"
        mCallback = { coroutine.resume(it) }
        initClient {
            val consumeParams = ConsumeParams.newBuilder()
                .setPurchaseToken(purchaseToken)
                .build()
            mClient!!.consumeAsync(consumeParams) { billingResult, purchaseToken ->
                when (billingResult.responseCode) {
                    BillingClient.BillingResponseCode.OK -> resultSuccess(null, purchaseToken, null)
                    BillingClient.BillingResponseCode.USER_CANCELED -> resultCancel()
                    else -> resultError("token=$purchaseToken code=${billingResult.responseCode}  msg=${billingResult.debugMessage}")
                }
            }
        }
    }

    override suspend fun purchaseQuery(productType: GoogleProductType) = suspendCancellableCoroutine { coroutine ->
        mAction = "purchaseQuery"
        mCallback = { coroutine.resume(it) }
        initClient {
            mClient!!.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(
                when (productType) {
                    GoogleProductType.INAPP -> ProductType.INAPP
                    GoogleProductType.SUBS -> ProductType.SUBS
                }
            ).build()) { billingResult, purchases ->
                when (billingResult.responseCode) {
                    BillingClient.BillingResponseCode.OK -> {
                        resultSuccess(null, null, null, purchases.map { JSONObject(it.originalJson) })
                    }
                    BillingClient.BillingResponseCode.USER_CANCELED -> resultCancel()
                    else -> resultError("code=${billingResult.responseCode}  msg=${billingResult.debugMessage}")
                }
            }
        }
    }

    override suspend fun purchaseHistoryQuery(productType: GoogleProductType) = suspendCancellableCoroutine { coroutine ->
        mAction = "purchaseHistoryQuery"
        mCallback = { coroutine.resume(it) }
        initClient {
            mClient!!.queryPurchaseHistoryAsync(QueryPurchaseHistoryParams.newBuilder().setProductType(
                when (productType) {
                    GoogleProductType.INAPP -> ProductType.INAPP
                    GoogleProductType.SUBS -> ProductType.SUBS
                }
            ).build()) { billingResult, purchasesHistoryList ->
                when (billingResult.responseCode) {
                    BillingClient.BillingResponseCode.OK -> {
                        resultSuccess( null, null, null, purchasesHistoryList?.map { JSONObject(it.originalJson) })
                    }
                    BillingClient.BillingResponseCode.USER_CANCELED -> resultCancel()
                    else -> resultError("code=${billingResult.responseCode}  msg=${billingResult.debugMessage}")
                }
            }
        }
    }
}