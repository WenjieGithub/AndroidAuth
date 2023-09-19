package cn.moltres.android.auth.wx

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

    override fun registerCallback(callback: (result: AuthResult) -> Unit) {
        AuthActivityForWX.callback = callback
    }

    override fun checkAppInstalled(): AuthResult {
        return try {
            if (mAPI == null) {
                AuthResult.Error("微信 初始化失败")
            } else if (mAPI?.isWXAppInstalled == true) {
                AuthResult.Success("已经安装了微信客户端")
            } else {
                AuthResult.Uninstalled
            }
        } catch (e: Exception) {
            AuthResult.Error(e.stackTraceToString(), e)
        }
    }

    override fun launchMiniProgram(id: String, path: String, type: Int): AuthResult {
        return if (mAPI == null) {
            AuthResult.Error("微信 初始化失败")
        } else if (mAPI?.isWXAppInstalled != true) {
            AuthResult.Uninstalled
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
            AuthResult.Success()
        }
    }

    override suspend fun login() = suspendCancellableCoroutine { coroutine ->
        mCallback = { coroutine.resume(it) }
        if (mAPI == null) {
            resultError("微信 初始化失败")
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
        mCallback = { coroutine.resume(it) }
        if (mAPI == null) {
            resultError("微信 初始化失败")
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
        mCallback = { coroutine.resume(it) }
        if (mAPI == null) {
            resultError("微信 初始化失败")
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
        mCallback = { coroutine.resume(it) }
        if (mAPI == null) {
            resultError("微信 初始化失败")
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
        mCallback = { coroutine.resume(it) }
        if (mAPI == null) {
            resultError("微信 初始化失败")
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