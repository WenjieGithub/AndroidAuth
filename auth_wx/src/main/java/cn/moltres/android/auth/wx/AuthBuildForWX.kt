package cn.moltres.android.auth.wx

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.os.Build
import cn.moltres.android.auth.AbsAuthBuildForWX
import cn.moltres.android.auth.AuthResult
import cn.moltres.android.auth.Auth
import cn.moltres.android.auth.WXShareScene
import com.tencent.mm.opensdk.constants.ConstantsAPI
import com.tencent.mm.opensdk.modelbiz.OpenWebview
import com.tencent.mm.opensdk.modelbiz.WXLaunchMiniProgram
import com.tencent.mm.opensdk.modelbiz.WXOpenBusinessWebview
import com.tencent.mm.opensdk.modelbiz.WXOpenCustomerServiceChat
import com.tencent.mm.opensdk.modelmsg.*
import com.tencent.mm.opensdk.modelpay.PayReq
import com.tencent.mm.opensdk.openapi.IWXAPI
import com.tencent.mm.opensdk.openapi.WXAPIFactory
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class AuthBuildForWX : AbsAuthBuildForWX() {
    internal companion object {
        var mAPI: IWXAPI? = null

        init {
            if (mAPI == null) {
                if (Auth.wxAppId.isNullOrEmpty()) {
                    Auth.wxAppId = Auth.getMetaData("WXAppId")
                }
                require(!Auth.wxAppId.isNullOrEmpty())  { "请配置 WXAppId" }

                mAPI = WXAPIFactory.createWXAPI(Auth.application, Auth.wxAppId, true)
                mAPI?.registerApp(Auth.wxAppId)

                // 动态监听微信启动广播进行注册到微信
                val intent = IntentFilter(ConstantsAPI.ACTION_REFRESH_WXAPP)
                val broad = object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        mAPI?.registerApp(Auth.wxAppId)
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Auth.application.registerReceiver(broad, intent, Context.RECEIVER_EXPORTED)
                } else {
                    Auth.application.registerReceiver(broad, intent)
                }
            }
        }
    }

    override fun registerCallback(callback: (result: AuthResult, activity: Activity) -> Unit) {
        AuthActivityForWX.callback = callback
    }

    override fun checkAppInstalled(): AuthResult {
        mAction = "checkAppInstalled"
        return try {
            if (mAPI == null) {
                resultError("初始化失败")
            } else if (mAPI?.isWXAppInstalled == true) {
                resultSuccess()
            } else {
                resultUninstalled()
            }
        } catch (e: Exception) {
            resultError(e.message, null, e)
        }
    }

    override fun launchMiniProgram(id: String, path: String, type: Int): AuthResult {
        mAction = "launchMiniProgram"
        return if (mAPI == null) {
            resultError("初始化失败")
        } else if (mAPI?.isWXAppInstalled != true) {
            resultUninstalled()
        } else {
            val req = WXLaunchMiniProgram.Req()
            req.userName = id   // 填小程序原始id
            req.path = path     // 拉起小程序页面的可带参路径，不填默认拉起小程序首页，对于小游戏，可以只传入 query 部分，来实现传参效果，如：传入 "?foo=bar"。
            req.miniprogramType = when (type) {
                1 -> WXLaunchMiniProgram.Req.MINIPROGRAM_TYPE_TEST
                2 -> WXLaunchMiniProgram.Req.MINIPROGRAM_TYPE_PREVIEW
                else -> WXLaunchMiniProgram.Req.MINIPTOGRAM_TYPE_RELEASE
            }
            mAPI?.sendReq(req)
            resultSuccess()
        }
    }

    override fun launchCustomerService(id: String, url: String): AuthResult {
        mAction = "launchMiniProgram"
        return if (mAPI == null) {
            resultError("初始化失败")
        } else if (mAPI?.isWXAppInstalled != true) {
            resultUninstalled()
        } else if (mAPI!!.wxAppSupportAPI < com.tencent.mm.opensdk.constants.Build.SUPPORT_OPEN_CUSTOMER_SERVICE_CHAT) {
            resultError("不支持的版本")
        } else {
            val req = WXOpenCustomerServiceChat.Req()
            req.corpId = id
            req.url = url
            mAPI?.sendReq(req)
            resultSuccess()
        }
    }

    override suspend fun login() = suspendCancellableCoroutine { coroutine ->
        mAction = "login"
        mCallback = { coroutine.resume(it) }
        if (mAPI == null) {
            resultError("初始化失败")
        } else if (mAPI?.isWXAppInstalled != true) {
            resultUninstalled()
        } else {
            AuthActivityForWX.authBuildForWX = this
            mAPI?.sendReq(SendAuth.Req().apply {
                scope = "snsapi_userinfo"
                state = mSign
            })
        }
    }

    override suspend fun shareLink(
        url: String,
        title: String?,
        des: String?,
        thumb: ByteArray?,
        shareScene: WXShareScene
    ) = suspendCancellableCoroutine { coroutine ->
        mAction = "shareLink"
        mCallback = { coroutine.resume(it) }
        if (mAPI == null) {
            resultError("初始化失败")
        } else if (mAPI?.isWXAppInstalled != true) {
            resultUninstalled()
        } else {
            AuthActivityForWX.authBuildForWX = this
            mAPI?.sendReq(SendMessageToWX.Req().apply {
                transaction = mSign
                message = WXMediaMessage(WXWebpageObject(url)).apply {
                    this.title = title
                    description = des
                    thumbData = thumb
                }
                scene = when (shareScene) {
                    WXShareScene.Session -> SendMessageToWX.Req.WXSceneSession
                    WXShareScene.Timeline -> SendMessageToWX.Req.WXSceneTimeline
                    WXShareScene.Favorite -> SendMessageToWX.Req.WXSceneFavorite
                }
            })
        }
    }

    override suspend fun shareImage(
        bitmap: Bitmap,
        title: String?,
        des: String?,
        thumb: ByteArray?,
        shareScene: WXShareScene
    ) = suspendCancellableCoroutine { coroutine ->
        mAction = "shareImage"
        mCallback = { coroutine.resume(it) }
        if (mAPI == null) {
            resultError("初始化失败")
        } else if (mAPI?.isWXAppInstalled != true) {
            resultUninstalled()
        } else {
            AuthActivityForWX.authBuildForWX = this
            mAPI?.sendReq(SendMessageToWX.Req().apply {
                transaction = mSign
                message = WXMediaMessage(WXImageObject(bitmap)).apply {
                    this.title = title
                    description = des
                    thumbData = thumb
                }
                scene = when (shareScene) {
                    WXShareScene.Session -> SendMessageToWX.Req.WXSceneSession
                    WXShareScene.Timeline -> SendMessageToWX.Req.WXSceneTimeline
                    WXShareScene.Favorite -> SendMessageToWX.Req.WXSceneFavorite
                }
            })
        }
    }

    override suspend fun pay(
        partnerId: String,
        prepayId: String,
        nonceStr: String,
        timeStamp: String,
        sign: String,
        packageValue: String
    ) = suspendCancellableCoroutine { coroutine ->
        mAction = "pay"
        mCallback = { coroutine.resume(it) }
        if (mAPI == null) {
            resultError("初始化失败")
        } else if (mAPI?.isWXAppInstalled != true) {
            resultUninstalled()
        } else {
            AuthActivityForWX.authBuildForWX = this
            val req = PayReq()
            req.appId = Auth.wxAppId
            req.partnerId = partnerId
            req.prepayId = prepayId
            req.packageValue = packageValue
            req.nonceStr = nonceStr
            req.timeStamp = timeStamp
            req.sign = sign
            req.transaction = mSign     // 回调时这个标记为 null, 只有 prePayId 可用, 所以使用 prePayId 作为标记
            mAPI?.sendReq(req)
        }
    }

    override suspend fun payTreaty(
        data: String,
        useOld: Boolean
    ) = suspendCancellableCoroutine { coroutine ->
        mAction = "payTreaty"
        mCallback = { coroutine.resume(it) }
        if (mAPI == null) {
            resultError("初始化失败")
        } else if (mAPI?.isWXAppInstalled != true) {
            resultUninstalled()
        } else if (useOld) {
            AuthActivityForWX.authBuildForWX = this
            val req = OpenWebview.Req()
            req.transaction = mSign             // 回调时这个标记和设置的不一样, 无法作为判断依据
            req.url = data
            mAPI?.sendReq(req)
        } else {
            AuthActivityForWX.authBuildForWX = this
            val req = WXOpenBusinessWebview.Req()
            req.transaction = mSign
            req.businessType = 12               // 固定值
            req.queryInfo = hashMapOf(Pair("pre_entrustweb_id", data))
            mAPI?.sendReq(req)
        }
    }
}