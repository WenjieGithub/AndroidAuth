package cn.moltres.android.auth

/**
 * 所有 Auth 回调的结果类
 */
sealed class AuthResult(open val with: String, open val action: String?) {
    data class Uninstalled(
        override val with: String,
        override val action: String?
    ) : AuthResult(with, action)            // 未安装客户端

    data class Cancel(
        override val with: String,
        override val action: String?
    ) : AuthResult(with, action)            // 取消

    data class Success(
        override val with: String,
        override val action: String?,
        val msg: String? = null,
        val data: String? = null,
        val any: Any? = null
    ) : AuthResult(with, action)            // 成功

    data class Error(
        override val with: String,
        override val action: String?,
        val msg: String? = null,
        val exception: Throwable? = null,
        val code: Int = -1
    ) : AuthResult(with, action)            // 异常

    override fun toString(): String {
        return when (this) {
            is Cancel -> "$with-$action: Cancel"
            is Uninstalled -> "$with-$action: Uninstalled"
            is Error -> "$with-$action: Error -> $code  $msg  ${exception?.stackTraceToString()}"
            is Success -> "$with-$action: Success -> $msg  $data  $any"
        }
    }
}