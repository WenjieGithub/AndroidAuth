package cn.moltres.android.auth

import android.app.Activity

abstract class AbsAuthBuildForHW : AbsAuthBuild("HW") {
    /** 检查沙盒测试, 结果通过日志方式输出 */
    abstract fun isSandboxActivated(activity: Activity)

    /** 程序启动后主页面调用 */
    abstract fun onActivityCreate(activity: Activity, forceUpdate: Boolean = false)

    /**
     * 跳转到管理订阅页, 使用前先调用登陆来确保登陆
     */
    abstract suspend fun jumpToManageSubsPage(activity: Activity): AuthResult

    /** 登录功能 授权 */
    abstract suspend fun login(): AuthResult
    /** 取消授权 */
    abstract suspend fun cancelAuth(activity: Activity? = null): AuthResult

    /**
     * 购买记录查询
     * @param activity
     * @param priceType 0：消耗型商品; 1：非消耗型商品; 2：订阅型商品
     * @param record 记录模式，默认 false；
     * record 为 true: 仅支持消耗型商品、订阅型商品；对于消耗型商品，获取用户所有已消耗的商品信息。订阅型商品，获取用户的订阅收据。
     * record 为 false: 支持所有类型商品；应用启动时：
     *      非消耗型商品：查询购买信息列表不为空，请确认每个购买信息的purchaseState字段。若purchaseState为0，您需要提供相应的商品服务。
     *      订阅型商品：判断用户已购的订阅型商品的状态，以决定是否应该提供对应的服务
     *      消耗型商品：应用启动时
     *                购买请求返回-1（OrderStatusCode.ORDER_STATE_FAILED）时。
     *                购买请求返回60051（OrderStatusCode.ORDER_PRODUCT_OWNED）时。
     *                购买请求返回1（OrderStatusCode.ORDER_STATE_DEFAULT_CODE）时。
     *                      解析出purchaseState字段，当purchaseState为0时表示此次交易是成功的，您的应用仅需要对这部分商品进行补发货操作。
     * @return JSONArray
     */
    abstract suspend fun purchaseHistoryQuery(
        priceType: HWPriceType,
        record: Boolean = false,
        activity: Activity? = null
    ): AuthResult

    /**
     * 支付检查，是否支持华为支付，作为支付的前提
     */
    abstract suspend fun payCheck(): AuthResult

    /**
     * 支付商品列表查询
     * https://developer.huawei.com/consumer/cn/doc/development/HMSCore-References/productinfo-0000001050135784
     * @param productListSubs 商品列表, 订阅型商品
     * @param productListConsumable 商品列表, 消耗型商品
     * @param productListNonConsumable 商品列表, 非消耗型商品
     * priceType 商品类型 0：消耗型商品; 1：非消耗型商品; 2：订阅型商品
     *
     * @return List<JSONObject>
     */
    abstract suspend fun payProductQuery(
        productListSubs: List<String>? = null,
        productListConsumable: List<String>? = null,
        productListNonConsumable: List<String>? = null,
        activity: Activity? = null
    ): AuthResult

    /**
     * 消耗型商品 消耗操作 确认购买
     * @param purchaseToken 消耗型商品, 消耗操作的商品 Token，json 字段名：purchaseToken
     */
    abstract suspend fun payConsume(purchaseToken: String, activity: Activity? = null): AuthResult

    /**
     * 购买PMS商品
     * PMS商品指在华为AppGallery Connect网站上配置的商品，支持消耗型、非消耗型和订阅型商品。
     * @param productId AppGallery Connect网站上配置的商品 Id
     * @param priceType: 0：消耗型商品; 1：非消耗型商品; 2：订阅型商品
     * @param developerPayload 商户侧保留信息。若该字段有值，在支付成功后的回调结果中会原样返回给应用。注意：该参数长度限制为(0, 128)。
     * @param publicKey 支付公钥, 用于验签, 如果为空则使用初始化时的默认值
     *
     * @return JSONObject
     */
    abstract suspend fun payPMS(
        productId: String,
        priceType: HWPriceType,
        developerPayload: String? = null,
        publicKey: String? = null,
    ): AuthResult

    /**
     * 购买非PMS商品 仅支持消耗型商品和非消耗型商品
     * @param priceType: 0: 消耗型; 1: 非消耗型
     * @param productId 应用自定义的商品ID，商品ID用于唯一标识一个商品，不能重复
     * @param productName 商品名称，由应用自定义。
     * @param amount 商品金额，中国大陆该字段单位为元。此金额将会在支付时显示给用户确认。
     * @param sdkChannel 渠道信息。0：代表自有应用，无渠道  1：代表应用市场渠道 2：代表预装渠道 3：代表游戏中心 4：代表运动健康渠道
     * @param country 国家码，用于区分国家信息，必须符合ISO 3166标准。如果不传该参数，默认为CN。
     * @param currency 用于支付该商品的币种，必须符合ISO 4217标准。如果不传该参数，默认为CNY。
     * @param developerPayload 商户侧保留信息。若该字段有值，在支付成功后的回调结果中会原样返回给应用。注意：该参数长度限制为(0, 128)。
     * @param serviceCatalog 商品所属的产品类型  应用设置为”X5”(默认)
     * @param publicKey 支付公钥, 用于验签, 如果为空则使用初始化时的默认值
     */
    abstract suspend fun payAmount(
        priceType: HWPriceType,
        productId: String,
        productName: String,
        amount: String,
        sdkChannel: String = "1",
        country: String = "CN",
        currency: String = "CNY",
        developerPayload: String? = null,
        serviceCatalog: String = "X5",
        publicKey: String? = null,
    ): AuthResult
}


/**
 * 华为商品价格类型：消耗型商品、非消耗型商品、订阅商品
 * 0：消耗型商品; 1：非消耗型商品; 2：订阅型商品
 */
enum class HWPriceType(val code: Int) {
    Consumable(0), NonConsumable(1), Subscription(2)
}
