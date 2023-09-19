package cn.moltres.android.auth.yl

import cn.moltres.android.auth.AbsAuthBuildForYL
import com.unionpay.UPPayAssistEx
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class AuthBuildForYL : AbsAuthBuildForYL() {

    override suspend fun pay(orderInfo: String, test: Boolean) = suspendCancellableCoroutine { coroutine ->
        mCallback = { coroutine.resume(it) }
        AuthActivityForYL.authBuildForYL = this
        AuthActivityForYL.callbackActivity = { activity ->
            AuthActivityForYL.callbackActivityResult = { _, _, data ->
                if (data != null && data.extras != null) {
                    val s = data.extras?.getString("pay_result")
                    when {
                        "success".equals(s, true) -> resultSuccess("支付成功", s, activity)
                        "cancel".equals(s, true) -> resultCancel(activity)
                        else -> resultError("支付失败: ${data.extras.toString()}", activity)
                    }
                } else {
                    resultError("返回结果为空", activity)
                }
            }
            UPPayAssistEx.startPay(activity, null, null, orderInfo, if (test) "01" else "00")
        }
        startAuthActivity(AuthActivityForYL::class.java)
    }
}