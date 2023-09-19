package cn.moltres.android.auth.wb

import android.graphics.Bitmap
import cn.moltres.android.auth.AbsAuthBuildForWB
import cn.moltres.android.auth.AuthResult
import cn.moltres.android.auth.Auth
import com.sina.weibo.sdk.api.ImageObject
import com.sina.weibo.sdk.api.TextObject
import com.sina.weibo.sdk.api.WebpageObject
import com.sina.weibo.sdk.api.WeiboMultiMessage
import com.sina.weibo.sdk.auth.AuthInfo
import com.sina.weibo.sdk.auth.Oauth2AccessToken
import com.sina.weibo.sdk.auth.WbAuthListener
import com.sina.weibo.sdk.common.UiError
import com.sina.weibo.sdk.openapi.IWBAPI
import com.sina.weibo.sdk.openapi.SdkListener
import com.sina.weibo.sdk.openapi.WBAPIFactory
import com.sina.weibo.sdk.share.WbShareCallback
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.*
import kotlin.coroutines.resume

class AuthBuildForWB : AbsAuthBuildForWB() {
    internal companion object {
        var mAPI: IWBAPI? = null

        init {
            if (mAPI == null) {
                if (Auth.wbAppKey.isNullOrEmpty() || Auth.wbUrl.isNullOrEmpty() || Auth.wbScope.isNullOrEmpty()) {
                    Auth.wbAppKey = Auth.getMetaData("WBAppKey")?.replace("wb", "")
                    Auth.wbUrl = Auth.getMetaData("WBRedirectUrl")
                    Auth.wbScope = Auth.getMetaData("WBScope")
                }
                require(!Auth.wbAppKey.isNullOrEmpty()) { "请配置 WBAppKey" }
                require(!Auth.wbUrl.isNullOrEmpty()) { "请配置 WBRedirectUrl" }
                require(!Auth.wbScope.isNullOrEmpty()) { "请配置 WBScope" }

                mAPI = WBAPIFactory.createWBAPI(Auth.application)
                mAPI?.registerApp(
                    Auth.application,
                    AuthInfo(Auth.application, Auth.wbAppKey, Auth.wbUrl, Auth.wbScope),
                    object : SdkListener {
                        override fun onInitSuccess() { }    // 初始化成功回调
                        override fun onInitFailure(p0: java.lang.Exception?) { mAPI = null }    // 初始化失败回调
                    }
                )
            }
        }
    }

    override fun checkAppInstalled(): AuthResult {
        return try {
            if (mAPI == null) {
                AuthResult.Error("微博 初始化失败")
            } else if (mAPI?.isWBAppInstalled == true) {
                AuthResult.Success("已经安装了微博客户端")
            } else {
                AuthResult.Uninstalled
            }
        } catch (e: Exception) {
            AuthResult.Error(e.stackTraceToString(), e)
        }
    }

    override suspend fun login() = suspendCancellableCoroutine { coroutine ->
        mCallback = { coroutine.resume(it) }
        if (mAPI == null) {
            resultError("微博 初始化失败")
        } else {
            AuthActivityForWB.authBuildForWB = this
            AuthActivityForWB.callbackActivity = { activity ->
                AuthActivityForWB.callbackActivityResult = { requestCode, resultCode, data ->
                    mAPI?.authorizeCallback(activity, requestCode, resultCode, data)
                }
                mAPI?.authorize(activity, object : WbAuthListener {
                    override fun onComplete(token: Oauth2AccessToken) {
                        resultSuccess(token.toString(), token.accessToken, activity)
                    }
                    override fun onError(error: UiError?) {
                        resultError("code: ${error?.errorCode}; msg: ${error?.errorMessage}; detail: ${error?.errorDetail}", activity)
                    }
                    override fun onCancel() {
                        resultCancel(activity)
                    }
                })
            }
            startAuthActivity(AuthActivityForWB::class.java)
        }
    }

    override suspend fun shareLink(
        url: String,
        title: String?,
        des: String?,
        text: String?,
        thumb: ByteArray?,
    ) = suspendCancellableCoroutine { coroutine ->
        mCallback = { coroutine.resume(it) }
        if (mAPI == null) {
            resultError("微博 初始化失败")
        } else if (mAPI?.isWBAppInstalled == true) {
            AuthActivityForWB.authBuildForWB = this
            AuthActivityForWB.callbackActivity = { activity ->
                AuthActivityForWB.callbackActivityResult = { _, _, data ->
                    mAPI?.doResultIntent(data, object : WbShareCallback {
                        override fun onComplete() {
                            resultSuccess(activity = activity)
                        }
                        override fun onCancel() {
                            resultCancel(activity)
                        }
                        override fun onError(error: UiError?) {
                            resultError("code: ${error?.errorCode}; msg: ${error?.errorMessage}; detail: ${error?.errorDetail}", activity)
                        }
                    })
                }
                val message = WeiboMultiMessage()
                val webObject = WebpageObject()
                webObject.identify = UUID.randomUUID().toString()
                webObject.actionUrl = url
                webObject.title = title
                webObject.description = des
                webObject.defaultText = text
                webObject.thumbData = thumb
                message.mediaObject = webObject
                mAPI?.shareMessage(activity, message, false)
            }
            startAuthActivity(AuthActivityForWB::class.java)
        } else {
            resultUninstalled()
        }
    }

    override suspend fun shareImage(
        bitmap: Bitmap,
        title: String?,
    ) = suspendCancellableCoroutine { coroutine ->
        mCallback = { coroutine.resume(it) }
        if (mAPI == null) {
            resultError("微博 初始化失败")
        } else if (mAPI?.isWBAppInstalled == true) {
            AuthActivityForWB.authBuildForWB = this
            AuthActivityForWB.callbackActivity = { activity ->
                AuthActivityForWB.callbackActivityResult = { _, _, data ->
                    mAPI?.doResultIntent(data, object : WbShareCallback {
                        override fun onComplete() {
                            resultSuccess(activity = activity)
                        }
                        override fun onCancel() {
                            resultCancel(activity)
                        }
                        override fun onError(error: UiError?) {
                            resultError("code: ${error?.errorCode}; msg: ${error?.errorMessage}; detail: ${error?.errorDetail}", activity)
                        }
                    })
                }
                val message = WeiboMultiMessage()
                title?.let {
                    val textObject = TextObject()
                    textObject.text = it
                    message.textObject = textObject
                }
                val imageObject = ImageObject()
                imageObject.setImageData(bitmap)
                message.imageObject = imageObject
                mAPI?.shareMessage(activity, message, false)      // 是否只通过客户端分享 false
            }
            startAuthActivity(AuthActivityForWB::class.java)
        } else {
            resultUninstalled()
        }
    }
}