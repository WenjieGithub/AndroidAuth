package cn.moltres.android.auth

import android.app.Activity

abstract class AbsAuthBuildForXM : AbsAuthBuild("XM") {
    /** 程序启动后主页面调用 接入活动弹窗, 页面级别初始化  设置是否显示错误对话框,默认不显示   设置是否显示错误Toast,默认不显示 */
    abstract fun onActivityCreate(
        activity: Activity,
        isDialog: Boolean = false,
        isToast: Boolean = false
    )

    /** 接入活动弹窗, 页面销毁 */
    abstract fun onActivityDestroy()

    /**
     * 登录功能
     * @param type 登录类型: 自动登录优先,自有账号仅支持此方法; 仅自动登录; 仅手动登录;
     * @param account 登录账号类型: 小米账号; 自有账号
     * @param extra 自有账号填入您的账号系统的用户id,小米账号传null即可.
     */
    abstract suspend fun login(
        activity: Activity,
        type: XMLoginType = XMLoginType.AutoFirst,
        account: XMAccountType = XMAccountType.XM,
        extra: String? = null
    ): AuthResult

    /**
     * 支付 金额付费
     * @param orderId 订单号
     * @param amount 消费总金额，单位为分
     * @param userInfo 用于透传用户信息,当用户支付成功后我们会将此参数透传给开发者业务服务器(不能为null或“”)
     */
    abstract suspend fun payAmount(
        activity: Activity,
        orderId: String,
        amount: Int,
        userInfo: String
    ): AuthResult

    /**
     * 支付 计费代码付费(可消耗:可以重复购买；不可消耗:不可以重复购买)
     * @param orderId 订单号
     * @param productCode 商品 code 编码
     * @param quantity 非消耗类商品,取值=1 可消耗类商品,取值≥1
     */
    abstract suspend fun payCode(
        activity: Activity,
        orderId: String,
        productCode: String,
        quantity: Int
    ): AuthResult

    /**
     * 支付 签约支付
     * @param orderId 订单号
     * @param productCode 商品 code 编码
     * @param quantity 非消耗类商品,取值=1 可消耗类商品,取值≥1
     */
    abstract suspend fun payTreaty(
        activity: Activity,
        orderId: String,
        productCode: String,
        quantity: Int
    ): AuthResult
}

/**
 * 小米登录类型
 */
enum class XMLoginType {
    // 自动登录优先,自有账号仅支持此方法; 仅自动登录; 仅手动登录;
    AutoFirst, AutoOnly, ManualOnly
}

/**
 * 小米登录账号类型
 */
enum class XMAccountType {
    // 自有账号; 小米账号
    App, XM
}
