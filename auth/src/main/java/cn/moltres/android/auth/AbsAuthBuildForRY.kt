package cn.moltres.android.auth

import android.app.Activity


abstract class AbsAuthBuildForRY : AbsAuthBuild("RY") {
    /**
     * TODO 暂不支持 跳转到管理订阅页, 使用前先调用登陆来确保登陆
     */
    abstract suspend fun jumpToManageSubsPage(activity: Activity): AuthResult

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
     * 购买非PMS商品 仅支持消耗型商品和非消耗型商品
     *
     * @param  priceType            商品类型，目前仅支持：0是消耗型商品，1为非消耗型商品
     * @param  productId            应用自定义的商品ID，商品ID用于唯一标识一个商品，不能重复
     * @param  price                商品金额，商品价格为1元时，此处传参100
     * @param  promotionPrice       优惠价格
     * @param  productName          商品名称
     * @param  bizOrderNo           业务订单号,可以理解为游戏或app自定义订单号
     * @param  developerPayload     商户侧保留信息，支付结果会按传入内容返回
     * @param  currency             币种，默认中国：CNY
     * @param  needSandboxTest      传1为沙盒测试，0为正式支付, 默认 0
//     * @param  subPeriod            订购周期，priceType 为 2 时必传。
//     * @param  periodUnit           订购周期单位（W：周，M：月，Y：年。订阅商品有效）
//     * @param  secondChargeTime     第二次扣费时间（订阅型商品时传入）,格式yyyy-MM-dd
     */
    abstract suspend fun payAmount(
        priceType: RYPriceType,
        productId: String,
        price: Long,
        promotionPrice: Long,
        productName: String,
        bizOrderNo: String,
        developerPayload: String? = null,

        currency: String = "CNY",
        needSandboxTest: Int = 0,
//        subPeriod: Int,
//        periodUnit: String,
//        secondChargeTime: String,
    ): AuthResult

    /**
     * 购买PMS商品
     * PMS商品指在荣耀HONOR Developers平台上配置的商品，包含消耗型、非消耗型和订阅型商品。
     * 防止掉单, 创建订单前，需要查询已购买，未消耗的商品，进行消耗
     * @param productId 平台上配置的商品 Id
     * @param priceType: 0：消耗型商品; 1：非消耗型商品; 2：订阅型商品
     * @param developerPayload 商户侧保留信息。若该字段有值，在支付成功后的回调结果中会原样返回给应用。
     * @param  needSandboxTest      传1为沙盒测试，0为正式支付, 默认 0
     *
     * @return JSONObject
     */
    abstract suspend fun payPMS(
        productId: String,
        priceType: RYPriceType,
        developerPayload: String? = null,
        needSandboxTest: Int = 0,
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
