package cn.moltres.android.auth

import android.app.Activity
import android.content.Intent

abstract class AbsAuthBuild(val with: String) {
    protected val mSign: String = System.currentTimeMillis().toString()         // 任务标记
    protected var mCallback: ((AuthResult) -> Unit)? = null                     // 事件回调
    protected var mAction: String? = null

    fun signMatching(sign: String): Boolean {
        return mSign == sign
    }

    protected open fun destroy(activity: Activity? = null) {
        mAction = null
        mCallback = null
        activity?.finish()
        activity?.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    protected fun startAuthActivity(clazz: Class<out Activity>) {
        val intent = Intent(Auth.application, clazz)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        Auth.application.startActivity(intent)
    }

    fun resultUninstalled(
        activity: Activity? = null
    ) = AuthResult.Uninstalled.apply {
        mCallback?.invoke(this)
        Auth.logCallback?.invoke("$with-$mAction: $this")
        destroy(activity)
    }
    fun resultSuccess(
        msg: String? = null,
        data: String? = null,
        activity: Activity? = null,
        any: Any? = null
    ) = AuthResult.Success(msg, data, any).apply {
        mCallback?.invoke(this)
        Auth.logCallback?.invoke("$with-$mAction: $this")
        destroy(activity)
    }
    fun resultCancel(
        activity: Activity? = null
    ) = AuthResult.Cancel.apply {
        mCallback?.invoke(this)
        Auth.logCallback?.invoke("$with-$mAction: $this")
        destroy(activity)
    }
    fun resultError(
        msg: String?,
        activity: Activity? = null,
        exception: Exception? = null,
        code: Int = -1
    ) = AuthResult.Error(msg, exception, code).apply {
        mCallback?.invoke(this)
        Auth.logCallback?.invoke("$with-$mAction: $this")
        destroy(activity)
    }
}