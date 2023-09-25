package cn.moltres.android.auth

/**
 * 所有 Auth 回调的结果类
 */
sealed class AuthResult {
    object Uninstalled : AuthResult()       // 未安装客户端

    object Cancel : AuthResult()            // 取消

    data class Success(
        val msg: String? = null,
        val data: String? = null,
        val any: Any? = null
    ) : AuthResult()                        // 成功

    data class Error(
        val msg: String? = null,
        val exception: Exception? = null,
        val code: Int = -1
    ) : AuthResult()                        // 异常

    override fun toString(): String {
        return when (this) {
            Cancel -> "Cancel"
            Uninstalled -> "Uninstalled"
            is Error -> "Error -> $code  $msg  ${exception?.stackTraceToString()}"
            is Success -> "Success -> $msg  $data  $any"
        }
    }
}