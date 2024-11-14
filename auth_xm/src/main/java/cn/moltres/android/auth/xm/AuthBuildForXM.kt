package cn.moltres.android.auth.xm

import android.app.Activity
import android.text.TextUtils
import cn.moltres.android.auth.AbsAuthBuildForXM
import cn.moltres.android.auth.Auth
import cn.moltres.android.auth.XMAccountType
import cn.moltres.android.auth.XMLoginType
import com.xiaomi.gamecenter.appjoint.MiAccountType
import com.xiaomi.gamecenter.appjoint.MiCode
import com.xiaomi.gamecenter.appjoint.MiCommplatform
import com.xiaomi.gamecenter.appjoint.MiLoginType
import com.xiaomi.gamecenter.appjoint.entry.MiAppInfo
import com.xiaomi.gamecenter.appjoint.entry.MiBuyInfo
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import kotlin.coroutines.resume

class AuthBuildForXM : AbsAuthBuildForXM() {
    internal companion object {
        init {
            MiCommplatform.setApplication(Auth.application)

            if (Auth.xmAppId.isNullOrEmpty() || Auth.xmAppKey.isNullOrEmpty()) {
                Auth.xmAppId = Auth.getMetaData("XMAppId")?.replace("xm", "")
                Auth.xmAppKey = Auth.getMetaData("XMAppKey")?.replace("xm", "")
            }
            require(!Auth.xmAppId.isNullOrEmpty())  { "请配置 XMAppId" }
            require(!Auth.xmAppKey.isNullOrEmpty())  { "请配置 XMAppKey" }
        }
    }

    override fun onActivityCreate(activity: Activity, isDialog: Boolean, isToast: Boolean) {
        val appInfo = MiAppInfo()
        appInfo.appId = Auth.xmAppId
        appInfo.appKey = Auth.xmAppKey
        MiCommplatform.Init(activity, appInfo) { code: Int, msg: String? ->
            when (code) {
                MiCode.MI_INIT_SUCCESS -> {               // 初始化小米 SDK 成功
                    MiCommplatform.getInstance().isAlertDialogDisplay = isDialog
                    MiCommplatform.getInstance().isToastDisplay = isToast
                }
                else -> { Auth.logCallback?.invoke("小米SDK初始化失败: $msg") }
            }
        }
    }

    override fun onActivityDestroy() {
        MiCommplatform.getInstance().removeAllListener()
    }

    private fun getActivity(activity: Activity?, callback: (Activity, Activity?) -> Unit) {
        if (activity == null) {
            AuthActivityForXM.callbackActivity = {
                callback(it, it)
            }
            startAuthActivity(AuthActivityForXM::class.java)
        } else {
            callback(activity, null)
        }
    }

    override suspend fun login(
        type: XMLoginType,
        account: XMAccountType,
        extra: String?,
        activity: Activity?
    ) = suspendCancellableCoroutine { coroutine ->
        mAction = "login"
        mCallback = { coroutine.resume(it) }
        val lt = when (type) {
            XMLoginType.AutoFirst -> MiLoginType.AUTO_FIRST
            XMLoginType.AutoOnly -> MiLoginType.AUTO_ONLY
            XMLoginType.ManualOnly -> MiLoginType.MANUAL_ONLY
        }
        val at = when (account) {
            XMAccountType.App -> MiAccountType.APP
            XMAccountType.XM -> MiAccountType.MI_SDK
        }
        getActivity(activity) { a, af ->
            MiCommplatform.getInstance().miLogin(a, { code, account ->
                when (code) {
                    MiCode.MI_LOGIN_SUCCESS -> {            // 登录成功
                        val uid = account?.uid              // 获取用户的登录后的UID（即用户唯一标识）
                        val session = account?.sessionId    // 获取用户的登陆的Session（请参考5.3.3流程校验Session有效性），可选,12小时过期
                        val unionId = account?.unionId      // 用于验证在不同应用中 是否为同一用户, 如果为空 则代表没有开启unionID权限
                        // 请开发者完成将uid和session提交给开发者自己服务器进行session验证
                        if (!TextUtils.isEmpty(uid) && !TextUtils.isEmpty(session)) {
                            val json = JSONObject().put("uid", uid).put("session", session).put("unionId", unionId).toString()
                            resultSuccess("code: $code", json, af)
                        } else {
                            resultError("code = $code", af)
                        }
                    }
                    MiCode.MI_ERROR_LOGIN_CANCEL -> resultCancel(af)
                    else -> resultError("errorCode = $code", af)      // 登录失败,详细错误码见5.4 返回码
                }
            }, lt, at, extra)
        }
    }

    override suspend fun payAmount(
        orderId: String,
        amount: Int,
        userInfo: String,
        activity: Activity?,
    ) = suspendCancellableCoroutine { coroutine ->
        mAction = "payAmount"
        mCallback = { coroutine.resume(it) }
        val miBuyInfo = MiBuyInfo()
        miBuyInfo.cpOrderId = orderId      // 订单号唯一（不为空）
        miBuyInfo.cpUserInfo = userInfo    // 此参数在用户支付成功后会透传给CP的服务器
        miBuyInfo.feeValue = amount        // 必须是大于0的整数，100代表1元人民币（不为空）
        pay(miBuyInfo, activity)
    }
    override suspend fun payCode(
        orderId: String,
        productCode: String,
        quantity: Int,
        userInfo: String,
        activity: Activity?
    ) = suspendCancellableCoroutine { coroutine ->
        mAction = "payCode"
        mCallback = { coroutine.resume(it) }
        val miBuyInfo = MiBuyInfo()
        miBuyInfo.cpOrderId = orderId          // 订单号唯一（不为空）
        miBuyInfo.productCode = productCode    // 商品代码，开发者申请获得（不为空）
        miBuyInfo.cpUserInfo = userInfo    // 此参数在用户支付成功后会透传给CP的服务器
        miBuyInfo.quantity = quantity          // 购买数量(商品数量最大9999，最小1)（不为空）
        pay(miBuyInfo, activity)
    }
    private fun pay(miBuyInfo: MiBuyInfo, activity: Activity?) {
        getActivity(activity) { a, af ->
            MiCommplatform.getInstance().miUniPay(a, miBuyInfo) { code: Int, msg: String? ->
                when (code) {
                    MiCode.MI_PAY_SUCCESS -> resultSuccess(msg, activity = af)  // 购买成功，建议先通过自家服务器校验后再处理发货
                    MiCode.MI_ERROR_PAY_CANCEL -> resultCancel(af)              // 购买取消
                    else -> resultError("code=$code; msg=$msg", af)         // 购买失败,详细错误码见5.4 返回码
                }
            }
        }
    }

    override suspend fun payTreaty(
        orderId: String,
        productCode: String,
        quantity: Int,
        userInfo: String,
        activity: Activity?,
    ) = suspendCancellableCoroutine { coroutine ->
        mAction = "payTreaty"
        mCallback = { coroutine.resume(it) }
        val miBuyInfo = MiBuyInfo()
        miBuyInfo.cpOrderId = orderId          // 订单号唯一（不为空）
        miBuyInfo.productCode = productCode    // 商品代码，开发者申请获得（不为空）
        miBuyInfo.cpUserInfo = userInfo    // 此参数在用户支付成功后会透传给CP的服务器
        miBuyInfo.quantity = quantity          // 购买数量(商品数量最大9999，最小1)（不为空）
        getActivity(activity) { a, af ->
            MiCommplatform.getInstance().miSubscribe(a, miBuyInfo) { code: Int, msg: String? ->
                when (code) {
                    MiCode.MI_SUB_SUCCESS -> resultSuccess(msg, null, af)   // 订阅成功
                    MiCode.MI_ERROR_PAY_CANCEL -> resultCancel(af)              // 购买取消
                    else -> resultError("code=$code; msg=$msg", af)         // 购买失败,详细错误码见5.4 返回码
                }
            }
        }
    }
}