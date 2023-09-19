package cn.moltres.android.auth.zfb

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import cn.moltres.android.auth.AbsAuthBuildForZFB
import cn.moltres.android.auth.Auth
import com.alipay.sdk.app.PayTask
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.UnsupportedEncodingException
import java.net.URLDecoder
import kotlin.coroutines.resume

class AuthBuildForZFB : AbsAuthBuildForZFB() {
    override suspend fun pay(
        orderInfo: String,
        activity: Activity?
    ) = suspendCancellableCoroutine { coroutine ->
        mCallback = { coroutine.resume(it) }
        if (activity == null) {
            AuthActivityForZFB.callbackActivity = {
                pay(orderInfo, it, true)
            }
            startAuthActivity(AuthActivityForZFB::class.java)
        } else {
            pay(orderInfo, activity, false)
        }
    }

    private fun pay(orderInfo: String, activity: Activity, isFinish: Boolean) {
        val payRunnable = Runnable {
            val aliPay = PayTask(activity)
            val result = aliPay.payV2(orderInfo, true)     // true: 调用 pay 接口的时候唤起一个 loading
            val af = if (isFinish) activity else null
            when (result["resultStatus"]) {
                "9000" -> resultSuccess("支付成功", result.toString(), af)
                "6004", "8000" -> resultSuccess("支付完成，结果未知", result.toString(), af)
                "6001" -> resultCancel(af)
                "6002" -> resultError("网络连接出错: $result", af)
                "5000" -> resultError("重复请求: $result", af)
                "4000" -> resultError("支付失败: $result", af)
                else -> resultError("未知异常: $result", af)
            }
        }
        Thread(payRunnable).start()
    }

    override suspend fun payTreaty(data: String) = suspendCancellableCoroutine { coroutine ->
        mCallback = { coroutine.resume(it) }
        try {
            AuthActivityForZFB.authBuildForZFB = this
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(data))
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            Auth.application.startActivity(intent)
        } catch (e: Exception) {
            if (e is ActivityNotFoundException) {
                resultUninstalled()
            } else {
                resultError("签约失败", exception = e)
            }
        }
    }

    internal fun onTreatyPayResult(intent: Intent) {
        try {
            val data = intent.dataString
            val tradeStatus: String = decodeURL(data, "trade_status=")  // 根据支付宝返回的字符串中的trade_status字段来判断支付结果
            val statusAgreement: String = decodeURL(data, "status=")    // 如果只是签订续订协议，则根据status字段来判断协议的状态

            if ("TRADE_FINISHED" == tradeStatus || "TRADE_SUCCESS" == tradeStatus || "NORMAL" == statusAgreement) { // 支付成功或签约成功
                resultSuccess("签约支付成功", data)
            } else if ("TRADE_PENDING" == tradeStatus) {                        // 等待卖家收款
                resultSuccess("正在确认签约支付结果", data)
            } else {                                                            // 其他都认为支付失败
                resultError("签约支付失败: $data")
            }
        } catch (e: Exception) {
            resultError("签约支付失败： ", exception = e)
        }
    }

    private fun decodeURL(url: String?, key: String): String {
        try {
            val decode = URLDecoder.decode(url, "utf-8")
            if (!decode.contains(key)) {
                return ""
            }
            val str = decode.substring(decode.indexOf(key) + key.length)
            val strings = str.split("[&]|[?]".toRegex()).toTypedArray()
            return strings[0]
        } catch (e: UnsupportedEncodingException) {
            e.printStackTrace()
        }
        return ""
    }
}