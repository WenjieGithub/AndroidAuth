package cn.moltres.android.auth

abstract class AbsAuthBuildForYL : AbsAuthBuild("YL") {
    /**
     * 支付
     * @param orderInfo 订单信息
     * @param test 是否为测试
     */
    abstract suspend fun pay(orderInfo: String, test: Boolean = false): AuthResult
}
