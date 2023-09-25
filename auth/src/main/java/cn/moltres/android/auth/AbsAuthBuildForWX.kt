package cn.moltres.android.auth

import android.graphics.Bitmap

abstract class AbsAuthBuildForWX : AbsAuthBuild("WX") {
    /** 注册微信发送数据到应用时的回调 */
    abstract fun registerCallback(callback: (result: AuthResult) -> Unit)

    /** 检查应用安装 */
    abstract fun checkAppInstalled(): AuthResult

    /**
     * 调启小程序
     * @param id 小程序原始id
     * @param path 拉起小程序页面的可带参路径，不填默认拉起小程序首页，对于小游戏，可以只传入 query 部分，来实现传参效果，如：传入 "?foo=bar"。
     * @param type 可选, 0 正式版 1 测试版 2 体验版
     */
    abstract fun launchMiniProgram(id: String, path: String, type: Int = 0): AuthResult

    /** 登录功能 */
    abstract suspend fun login(): AuthResult

    /**
     * 分享链接, 取消操作也会走成功回调
     *
     * @param url 必填, 分享链接 Url, 限制长度不超过 10KB
     * @param title 分享链接标题, 限制长度不超过 512Bytes
     * @param des 分享链接描述, 限制长度不超过 1KB
     * @param thumb 分享链接缩略图, 限制内容大小不超过 32KB
     * @param shareScene 分享到对话\朋友圈\收藏
     */
    abstract suspend fun shareLink(
        url: String,
        title: String? = null,
        des: String? = null,
        thumb: ByteArray? = null,
        shareScene: WXShareScene = WXShareScene.Session
    ): AuthResult

    /**
     * 分享图片, 取消操作也会走成功回调
     *
     * @param bitmap 必填，分享图片二进制文件, 限制内容大小不超过 10M
     * @param title 分享图片标题
     * @param des 分享图片描述, 限制长度不超过 1KB
     * @param thumb 分享图片缩略图, 限制内容大小不超过 32KB
     * @param shareScene 分享到对话\朋友圈\收藏
     */
    abstract suspend fun shareImage(
        bitmap: Bitmap,
        title: String? = null,
        des: String? = null,
        thumb: ByteArray? = null,
        shareScene: WXShareScene = WXShareScene.Session
    ): AuthResult

    /**
     * 支付
     * @param partnerId 必填，商户号
     * @param prepayId 必填，预支付交易会话ID
     * @param nonceStr 必填，随机字符串
     * @param timeStamp 必填，时间戳
     * @param sign 必填，签名
     * @param packageValue 可选，扩展字段 默认微信文档暂填写固定值Sign=WXPay
     */
    abstract suspend fun pay(
        partnerId: String,
        prepayId: String,
        nonceStr: String,
        timeStamp: String,
        sign: String,
        packageValue: String = "Sign=WXPay"
    ): AuthResult

    /**
     * 签约支付，可能没有回调
     * @param data 签约支付所需数据
     * @param useOld 签约支付是否使用老版本
     */
    abstract suspend fun payTreaty(
        data: String,
        useOld: Boolean = false
    ): AuthResult
}

/**
 * 微信分享场景
 */
enum class WXShareScene {
    Session, Timeline, Favorite
}
