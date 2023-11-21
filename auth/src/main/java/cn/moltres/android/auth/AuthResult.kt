package cn.moltres.android.auth

/**
 * 所有 Auth 回调的结果类
 */
sealed class AuthResult {
    data class Uninstalled(
        val with: String,
        val action: String?
    ) : AuthResult()            // 未安装客户端

    data class Cancel(
        val with: String,
        val action: String?
    ) : AuthResult()            // 取消

    data class Success(
        val with: String,
        val action: String?,
        val msg: String? = null,
        val data: String? = null,
        val any: Any? = null
    ) : AuthResult()            // 成功

    data class Error(
        val with: String,
        val action: String?,
        val msg: String? = null,
        val exception: Throwable? = null,
        val code: Int = -1
    ) : AuthResult()            // 异常

    override fun toString(): String {
        return when (this) {
            is Cancel -> "$with-$action: Cancel"
            is Uninstalled -> "$with-$action: Uninstalled"
            is Error -> "$with-$action: Error -> $code  $msg  ${exception?.stackTraceToString()}"
            is Success -> "$with-$action: Success -> $msg  $data  $any"
        }
    }
}