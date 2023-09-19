package cn.moltres.android.auth

import android.graphics.Bitmap

abstract class AbsAuthBuildForWB : AbsAuthBuild() {
    /** 检查应用安装 */
    abstract fun checkAppInstalled(): AuthResult

    /** 登录功能 */
    abstract suspend fun login(): AuthResult

    /**
     * 分享链接
     *
     * @param url 必填, 分享链接 Url
     * @param title 分享链接标题
     * @param des 分享链接描述
     * @param text 分享文本
     * @param thumb 分享链接缩略图
     */
    abstract suspend fun shareLink(
        url: String,
        title: String? = null,
        des: String? = null,
        text: String? = null,
        thumb: ByteArray? = null,
    ): AuthResult

    /**
     * 分享图片
     *
     * @param bitmap 必填，分享图片二进制文件, 限制内容大小不超过 10M
     * @param title 分享图片标题
     */
    abstract suspend fun shareImage(
        bitmap: Bitmap,
        title: String? = null
    ): AuthResult
}
