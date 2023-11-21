package cn.moltres.android.auth


abstract class AbsAuthBuildForRY : AbsAuthBuild("RY") {
    /** 登录功能 授权 */
    abstract suspend fun login(): AuthResult

    /** 支付检查，返回信息判断用户当前登录的荣耀帐号所在的服务地是否支持荣耀支付 */
    abstract suspend fun payCheck(): AuthResult

    /**
     * 支付商品列表查询, 包含PMS商品，则需要在荣耀HONOR Developers平台上完成商品的配置。商品配置完成后，查商品信息
     * @param productList 商品ID列表
     * @param priceType 商品类型 0：消耗型商品; 1：非消耗型商品; 2：订阅型商品
     *
     * @return List<JSONObject>
     */
    abstract suspend fun payProductQuery(productList: List<String>, priceType: RYPriceType): AuthResult

    /**
     * 购买PMS商品
     * PMS商品指在荣耀HONOR Developers平台上配置的商品，包含消耗型、非消耗型和订阅型商品。
     * 防止掉单, 创建订单前，需要查询已购买，未消耗的商品，进行消耗
     * @param productId 平台上配置的商品 Id
     * @param priceType: 0：消耗型商品; 1：非消耗型商品; 2：订阅型商品
     * @param developerPayload 商户侧保留信息。若该字段有值，在支付成功后的回调结果中会原样返回给应用。
     *
     * @return JSONObject
     */
    abstract suspend fun payPMS(
        productId: String,
        priceType: RYPriceType,
        developerPayload: String? = null
    ): AuthResult

    /**
     * 消耗型商品 消耗操作 确认购买
     * @param purchaseToken 消耗型商品, 消耗操作的商品 Token，json 字段名：purchaseToken
     */
    abstract suspend fun payConsume(purchaseToken: String): AuthResult


    /**
     * 购买记录查询
     * 获取结果后需要公钥验签, purchaseList 和 sigList 一一对应
     * @param priceType 0：消耗型商品(查询已购买未消耗的列表); 1：非消耗型商品; 2：订阅型商品
     * @param continueToken 传入上一次查询得到的 continueToken，获取新的数据，第一次传空
     * @param record 记录模式，默认 false；查看用户历史购买记录;
     * @return JSONObject
     */
    abstract suspend fun purchaseHistoryQuery(
        priceType: RYPriceType,
        continueToken: String? = null,
        record: Boolean = false
    ): AuthResult
}


/**
 * 荣耀商品价格类型：消耗型商品、非消耗型商品、订阅商品
 * 0：消耗型商品; 1：非消耗型商品; 2：订阅型商品
 */
enum class RYPriceType(val code: Int) {
    Consumable(0), NonConsumable(1), Subscription(2)
}
