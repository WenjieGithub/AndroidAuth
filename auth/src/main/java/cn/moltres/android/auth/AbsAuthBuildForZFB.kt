package cn.moltres.android.auth

import android.app.Activity

abstract class AbsAuthBuildForZFB : AbsAuthBuild("ZFB") {
    /**
     * 支付
     * @param orderInfo 订单信息
     * @param activity 如果为空，则启动透明 activity 调用支付，建议传值
     */
    abstract suspend fun pay(orderInfo: String, activity: Activity? = null): AuthResult

    /**
     * 签约支付
     * @param data 签约的数据
     */
    abstract suspend fun payTreaty(data: String): AuthResult
}
