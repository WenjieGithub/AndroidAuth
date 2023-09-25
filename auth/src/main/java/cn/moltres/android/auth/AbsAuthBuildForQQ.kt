package cn.moltres.android.auth

abstract class AbsAuthBuildForQQ : AbsAuthBuild("QQ") {
    /** 检查应用安装 */
    abstract fun checkAppInstalled(): AuthResult

    /** 登录功能 */
    abstract suspend fun login(): AuthResult

    /**
     * 分享链接
     *
     * @param targetUrl 必填，点击后跳转 url
     * @param title 必填，标题最长 30 个字符
     * @param summary 选填, 摘要
     * @param imageUrl 选填, 图片 url 或本地路径
     */
    abstract suspend fun shareLink(
        targetUrl: String,
        title: String,
        summary: String? = null,
        imageUrl: String? = null
    ): AuthResult
}
