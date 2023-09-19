package cn.moltres.android.auth.wx

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import cn.moltres.android.auth.AuthResult
import com.tencent.mm.opensdk.constants.ConstantsAPI
import com.tencent.mm.opensdk.modelbase.BaseReq
import com.tencent.mm.opensdk.modelbase.BaseResp
import com.tencent.mm.opensdk.modelbiz.WXLaunchMiniProgram
import com.tencent.mm.opensdk.modelmsg.GetMessageFromWX
import com.tencent.mm.opensdk.modelmsg.SendAuth
import com.tencent.mm.opensdk.modelmsg.ShowMessageFromWX
import com.tencent.mm.opensdk.modelmsg.WXAppExtendObject
import com.tencent.mm.opensdk.modelpay.PayResp
import com.tencent.mm.opensdk.openapi.IWXAPIEventHandler
import org.json.JSONObject

class AuthActivityForWX : Activity() {
    companion object {
        internal var callback: ((result: AuthResult) -> Unit)? = null
        internal var authBuildForWX: AuthBuildForWX? = null

        internal fun respParseToJson(resp: BaseResp): JSONObject {
            val jsonObject = JSONObject()
            jsonObject.put("code", resp.errCode)
                .put("msg", resp.errStr)
                .put("type", resp.type)
                .put("openId", resp.openId)
            (resp as? SendAuth.Resp)?.let {
                jsonObject.put("userCode", it.code)
                    .put("lang", it.lang)
                    .put("country", it.country)
            }
            (resp as? PayResp)?.let {
                jsonObject.put("extData", it.extData)
                    .put("prepayId", it.prepayId)
                    .put("returnKey", it.returnKey)
            }
            (resp as? WXLaunchMiniProgram.Resp)?.let {
                jsonObject.put("extMsg", it.extMsg)
            }
            return jsonObject
        }

        internal fun reqParseToJson(req: BaseReq): JSONObject {
            val jsonObject = JSONObject()
            jsonObject.put("type", req.type)
            (req as? ShowMessageFromWX.Req)?.let { itReq ->
                jsonObject.put("description", itReq.message?.description)
                    .put("title", itReq.message?.title)
                    .put("mediaTagName", itReq.message?.mediaTagName)
                    .put("lang", itReq.lang)
                    .put("country", itReq.country)
                (itReq.message?.mediaObject as? WXAppExtendObject)?.let {
                    jsonObject.put("extInfo", it.extInfo)
                        .put("filePath", it.filePath)
                }
            }
            (req as? GetMessageFromWX.Req)?.let {
                jsonObject.put("description", it.username)
                    .put("lang", it.lang)
                    .put("country", it.country)
            }
            return jsonObject
        }

        internal fun respParseSuccess(build: AuthBuildForWX, resp: BaseResp, activity: Activity?) {
            when (resp.type) {
                ConstantsAPI.COMMAND_SENDAUTH -> {                                              // 登录
                    if (resp is SendAuth.Resp) {// if (sResp?.state == mSign) state 可能为空
                        build.resultSuccess(respParseToJson(resp).toString(), resp.code, activity)
                    } else {
                        build.resultError("返回结果类型异常：${respParseToJson(resp)}", activity)
                    }
                }
                ConstantsAPI.COMMAND_SENDMESSAGE_TO_WX -> {                                     // 分享
                    if (build.signMatching(resp.transaction)) {
                        build.resultSuccess(respParseToJson(resp).toString(), activity = activity)
                    } else {
                        build.resultError("返回结果异常: ${respParseToJson(resp)}", activity)
                    }
                }
                ConstantsAPI.COMMAND_PAY_BY_WX -> {                                             // 支付
                    build.resultSuccess(respParseToJson(resp).toString(), activity = activity)
                }
                else -> build.resultSuccess("未确定返回类型: ${respParseToJson(resp)}", activity = activity)
            }
        }
    }

    private var wxHandler: IWXAPIEventHandler? = object : IWXAPIEventHandler {
        // 第三方应用发送到微信的请求处理后的响应结果，会回调到该方法
        override fun onResp(resp: BaseResp?) {
            if (resp != null) {
                when (resp.errCode) {
                    BaseResp.ErrCode.ERR_USER_CANCEL,
                    BaseResp.ErrCode.ERR_AUTH_DENIED -> {
                        authBuildForWX?.also { it.resultCancel(this@AuthActivityForWX) }
                            ?: onResultCancel() // "取消或拒绝", respParseToJson(resp)
                    }
                    BaseResp.ErrCode.ERR_OK -> {
                        authBuildForWX?.also { respParseSuccess(it, resp, this@AuthActivityForWX) }
                            ?: onResultSuccess("成功", respParseToJson(resp))
                    }
                    else -> {
                        authBuildForWX?.also { it.resultError("失败: ${respParseToJson(resp)}", this@AuthActivityForWX) }
                            ?: onResultError("失败", respParseToJson(resp))
                    }
                }
            } else {
                authBuildForWX?.also { it.resultError("发送到微信的请求处理后的响应结果为空") }
                    ?: onResultError("发送到微信的请求处理后的响应结果为空")
            }
        }

        // 微信发送请求到第三方应用时，会回调到该方法
        override fun onReq(req: BaseReq?) {
            if (authBuildForWX != null) {
                if (req != null) {
                    authBuildForWX?.resultSuccess("签约完成", reqParseToJson(req).toString(), this@AuthActivityForWX)
                } else {
                    authBuildForWX?.resultError("微信发送请求数据为空", this@AuthActivityForWX)
                }
            } else {
                if (req != null) {
                    when (req.type) {
                        ConstantsAPI.COMMAND_GETMESSAGE_FROM_WX -> onResultSuccess("GETMESSAGE_FROM_WX", reqParseToJson(req))
                        ConstantsAPI.COMMAND_SHOWMESSAGE_FROM_WX -> onResultSuccess("SHOWMESSAGE_FROM_WX", reqParseToJson(req))
                        ConstantsAPI.COMMAND_LAUNCH_BY_WX -> onResultSuccess("LAUNCH_BY_WX", reqParseToJson(req))
                        else -> onResultSuccess("其他类型", reqParseToJson(req))
                    }
                } else {
                    onResultError("微信发送请求数据为空")
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onHandleIntent()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        onHandleIntent()
    }

    override fun onDestroy() {
        wxHandler = null
        authBuildForWX = null
        super.onDestroy()
    }

    private fun onHandleIntent() {
        try {
            AuthBuildForWX.mAPI?.handleIntent(intent, wxHandler)
        } catch (e: Exception) {
            if (authBuildForWX != null) {
                authBuildForWX?.resultError("获取 Intent 异常", this, e)
            } else {
                onResultError("获取 Intent 异常, e: ${e.stackTraceToString()}")
            }
        }
    }

    /** 只有在 AuthBuild 没有启用的时候回调, 即：微信主动调用时启用 */
    private fun onResultError(msg: String, json: JSONObject? = null) {
        callback?.invoke(AuthResult.Error("$msg : $json"))
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private fun onResultCancel() {
        callback?.invoke(AuthResult.Cancel)
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private fun onResultSuccess(msg: String, json: JSONObject? = null) {
        callback?.invoke(AuthResult.Success(msg, json?.toString()))
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}
