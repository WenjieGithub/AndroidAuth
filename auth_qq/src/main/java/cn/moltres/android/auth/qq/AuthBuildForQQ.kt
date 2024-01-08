package cn.moltres.android.auth.qq

import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import cn.moltres.android.auth.AbsAuthBuildForQQ
import cn.moltres.android.auth.AuthResult
import cn.moltres.android.auth.Auth
import com.tencent.connect.share.QQShare
import com.tencent.tauth.IUiListener
import com.tencent.tauth.Tencent
import com.tencent.tauth.UiError
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import kotlin.coroutines.resume

class AuthBuildForQQ : AbsAuthBuildForQQ() {
    internal companion object {
        internal var mAPI: Tencent? = null

        init {
            if (mAPI == null) {
                val id = Auth.getMetaData("QQAppId")?.replace("tencent", "")
                val authorities = Auth.getMetaData("QQAuthorities")
                require(!id.isNullOrEmpty()) { "请配置 QQAppId、QQAuthorities" }

                mAPI = Tencent.createInstance(id, Auth.application, authorities)

                // 使用前初始化, 应用在确认用户已授权应用获取设备信息后，调用下面代码通知
                Tencent.setIsPermissionGranted(true, Build.MODEL)
            }
        }
    }

    override fun checkAppInstalled(): AuthResult {
        mAction = "checkAppInstalled"
        return try {
            if (mAPI == null) {
                resultError("QQ API 初始化失败")
            } else if (mAPI?.isQQInstalled(Auth.application) == true) {
                resultSuccess()
            } else {
                resultUninstalled()
            }
        } catch (e: Exception) {
            resultError(e.message, null, e)
        }
    }

    override suspend fun login() = suspendCancellableCoroutine { coroutine ->
        mAction = "login"
        mCallback = { coroutine.resume(it) }
        if (mAPI == null) {
            resultError("QQ API 初始化失败")
        } else if (mAPI?.isQQInstalled(Auth.application) == true) {
            AuthActivityForQQ.callbackActivity = { activity ->
                val listener = object : IUiListener {
                    override fun onComplete(any: Any?) {
                        try {
                            (any as? JSONObject)?.let {
                                if (it.optInt("ret") == 0 && !TextUtils.isEmpty(it.optString("access_token"))) {
                                    resultSuccess(it.toString(), it.optString("access_token"), activity)
                                    return
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        resultError("返回结果异常: $any", activity)
                    }
                    override fun onCancel() {
                        resultCancel(activity)
                    }
                    override fun onError(e: UiError?) {
                        resultError(e?.toString(), activity)
                    }
                    override fun onWarning(p0: Int) {
                    }
                }
                AuthActivityForQQ.callbackActivityResult = { requestCode, resultCode, data ->
                    Tencent.onActivityResultData(requestCode, resultCode, data, listener)
                }
                mAPI?.login(activity, "all", listener, false)
            }
            startAuthActivity(AuthActivityForQQ::class.java)
        } else {
            resultUninstalled()
        }
    }

    override suspend fun shareLink(
        targetUrl: String,
        title: String,
        summary: String?,
        imageUrl: String?
    ) = suspendCancellableCoroutine { coroutine ->
        mAction = "shareLink"
        mCallback = { coroutine.resume(it) }
        if (mAPI == null) {
            resultError("初始化失败")
        } else if (mAPI?.isQQInstalled(Auth.application) == true) {
            AuthActivityForQQ.callbackActivity = { activity ->
                val listener = object : IUiListener {
                    override fun onComplete(any: Any?) {
                        try {
                            (any as? JSONObject)?.let {
                                if (it.optInt("ret") == 0) {
                                    resultSuccess(it.toString(), activity = activity)
                                    return
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        resultError("信息异常: $any", activity)
                    }
                    override fun onCancel() {
                        resultCancel(activity)
                    }
                    override fun onError(e: UiError?) {
                        resultError(e?.toString(), activity)
                    }
                    override fun onWarning(p0: Int) {
                    }
                }
                AuthActivityForQQ.callbackActivityResult = { requestCode, resultCode, data ->
                    Tencent.onActivityResultData(requestCode, resultCode, data, listener)
                }
                val shareParams = Bundle()                     // 分享参数
                shareParams.putString(QQShare.SHARE_TO_QQ_TARGET_URL, targetUrl)
                shareParams.putString(QQShare.SHARE_TO_QQ_TITLE, title)
                summary?.let { shareParams.putString(QQShare.SHARE_TO_QQ_SUMMARY, it) }
                imageUrl?.let { shareParams.putString(QQShare.SHARE_TO_QQ_IMAGE_URL, it) }
                shareParams.putInt(QQShare.SHARE_TO_QQ_KEY_TYPE, QQShare.SHARE_TO_QQ_TYPE_DEFAULT)
                mAPI?.shareToQQ(activity, shareParams, listener)
            }
            startAuthActivity(AuthActivityForQQ::class.java)
        } else {
            resultUninstalled()
        }
    }
}