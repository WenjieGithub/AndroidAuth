package cn.moltres.android.auth

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.text.TextUtils
import org.json.JSONObject

abstract class AbsAuthBuildForGoogle : AbsAuthBuild() {
    /**
     * 可选，当有购买信息更新时回调（其他设备购买、线下购买），本机支付结果不走此回调
     */
    abstract fun setPurchasesUpdatedListener(listener: (result: List<JSONObject>?) -> Unit)

    /**
     * 支付商品列表查询
     * https://developer.huawei.com/consumer/cn/doc/development/HMSCore-References/productinfo-0000001050135784
     * @param productList 商品列表
     * @param productType 商品类型 一次性商品（消耗型、非消耗型）、订阅商品
     *
     * @return  List<GoogleProductDetails> 可空
     */
    abstract suspend fun payProductQuery(
        productList: List<String>,
        productType: GoogleProductType
    ): AuthResult

    /**
     * 启动购买流程
     * https://developer.android.com/google/play/billing/subscriptions?hl=zh-cn
     *
     * @param googleProductDetails 商品列表返回的某个商品
     * @param selectedOfferToken 订阅产品，一个订阅产品可能与多个产品/服务相关联。要确定用户有资格获得的产品/服务. subscriptionOfferDetails() 中找到对应的 offerToken
     * @param oldPurchaseToken 订阅产品，升降级时的上一个商品的购买令牌
     * @param prorationMode 订阅产品，升降级时要使用的按比例计费模式
     * @param isOfferPersonalized 如果您的应用可能会面向欧盟用户分发，请使用 setIsOfferPersonalized() 方法向用户披露您的商品价格已通过自动化决策进行了个性化设置，当该值为 true 时，Play 界面会包含披露声明。当该值为 false 时，Play 界面会忽略披露声明。默认值为 false。
     */
    abstract suspend fun pay(
        activity: Activity,
        googleProductDetails: GoogleProductDetails,
        selectedOfferToken: String?,
        oldPurchaseToken: String?,
        prorationMode: ProrationMode = ProrationMode.IMMEDIATE_AND_CHARGE_FULL_PRICE,
        isOfferPersonalized: Boolean = false
    ): AuthResult

    /**
     * 消耗型商品 消耗操作,确认购买
     * 使用关联的购买令牌来消耗商品
     *
     * 处理购买交易 非消耗型、订阅型可用后端API进行确认，如果您在三天内未确认购买交易，则用户会自动收到退款，并且 Google Play 会撤消该购买交易。
     * 由于消耗请求偶尔会失败，因此您必须检查安全的后端服务器，确保所有购买令牌都未被使用过，这样您的应用就不会针对同一购买交易多次授予权利。
     *
     * @param purchaseToken 消耗型商品, 消耗操作的商品购买令牌
     */
    abstract suspend fun payConsume(purchaseToken: String): AuthResult

    /**
     * 生效购买交易查询，返回有效订阅和非消耗型一次性购买交易。
     * 应用在 onResume() 方法中调用，以确保所有购买交易都得到成功处理，如处理购买交易中所述。
     * @param productType 一次性商品（消耗型、非消耗型）、订阅商品
     * @return List<JSONObject>
     */
    abstract suspend fun purchaseQuery(productType: GoogleProductType): AuthResult

    /**
     * 购买记录查询，返回用户针对每个商品发起的最近一笔购买记录，即使该购买交易已过期、已取消或已消耗，也仍会提取相关记录。
     * @param productType 一次性商品（消耗型、非消耗型）、订阅商品
     * @return List<JSONObject>
     */
    abstract suspend fun purchaseHistoryQuery(productType: GoogleProductType): AuthResult


    /**
     * 管理订阅 页面
     *
     * 如果用户有未到期的订阅，您可以将其转到与下面类似的网址，应将“your-sub-product-id”和“your-app-package”替换为订阅 ID 和应用软件包信息：
     * https://play.google.com/store/account/subscriptions?sku=your-sub-product-id&package=your-app-package
     *
     * 如果用户在您的应用中没有任何未到期的订阅，请使用以下网址将用户转到显示其所有其他订阅的页面，如图 5 和图 6 所示：
     * https://play.google.com/store/account/subscriptions
     *
     * 分享促销代码
     * https://play.google.com/redeem?code=promo_code
     */
    fun openGoogleSubscribe(sku: String? = null, packageName: String? = null) {
        val url = if (sku.isNullOrEmpty() || packageName.isNullOrEmpty()) {
            "https://play.google.com/store/account/subscriptions"
        } else {
            "https://play.google.com/store/account/subscriptions?sku=$sku&package=$packageName"
        }
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = Uri.parse(url)
        Auth.application.startActivity(intent)
    }
}
/**
 * Google 商品类型：一次性商品（消耗型、非消耗型）、订阅商品
 */
enum class GoogleProductType {
    INAPP, SUBS
}
enum class ProrationMode(val code: Int) {
    UNKNOWN_SUBSCRIPTION_UPGRADE_DOWNGRADE_POLICY(0),
    IMMEDIATE_WITH_TIME_PRORATION(1),
    IMMEDIATE_AND_CHARGE_PRORATED_PRICE(2),
    IMMEDIATE_WITHOUT_PRORATION(3),
    DEFERRED(4),
    IMMEDIATE_AND_CHARGE_FULL_PRICE(5)
}

/**
 * Google 商品详情
 * https://developer.android.com/reference/com/android/billingclient/api/ProductDetails?hl=zh-cn
 */
data class GoogleProductDetails(
    val productDetails: Any,
    val original: String,
    val productId: String?,
    val productType: String?,
    val title: String?,
    val name: String?,
    val packageName: String?,
    val description: String?,
    val oneTimePurchaseOfferDetails: OneTimePurchaseOfferDetails?,
    val subscriptionOfferDetails: List<SubscriptionOfferDetails>?,
) {
    override fun hashCode(): Int {
        return original.hashCode()
    }
    override fun toString(): String {
        return original
    }
    override fun equals(other: Any?): Boolean {
        return if (this === other) {
            true
        } else if (other !is GoogleProductDetails) {
            false
        } else {
            TextUtils.equals(original, other.original)
        }
    }

    data class PricingPhase(
        val formattedPrice: String,
        val priceAmountMicros: Long,
        val priceCurrencyCode: String,
        val billingPeriod: String,
        val billingCycleCount: Int,
        // INFINITE_RECURRING = 1; FINITE_RECURRING = 2; NON_RECURRING = 3;
        val recurrenceMode: Int,
    )

    data class PricingPhases(
        val pricingPhaseList: List<PricingPhase>?
    )

    data class OneTimePurchaseOfferDetails(
        val formattedPrice: String?,
        val priceAmountMicros: Long?,
        val priceCurrencyCode: String?,
        val offerIdToken: String?,
    )

    data class SubscriptionOfferDetails(
        val offerToken: String?,
        val offerTags: List<String>?,
        val pricingPhases: PricingPhases?,
    )
}