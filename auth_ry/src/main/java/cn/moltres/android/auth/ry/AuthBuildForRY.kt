package cn.moltres.android.auth.ry

import android.app.Activity
import android.content.Intent
import cn.moltres.android.auth.AbsAuthBuildForRY
import cn.moltres.android.auth.Auth
import cn.moltres.android.auth.RYPriceType
import com.hihonor.cloudservice.common.ApiException
import com.hihonor.cloudservice.support.account.HonorIdSignInManager
import com.hihonor.cloudservice.support.account.request.SignInOptionBuilder
import com.hihonor.cloudservice.support.account.request.SignInOptions
import com.hihonor.honorid.core.helper.handler.ErrorStatus
import com.hihonor.iap.framework.utils.JsonUtil
import com.hihonor.iap.sdk.Iap
import com.hihonor.iap.sdk.IapClient
import com.hihonor.iap.sdk.bean.ConsumeReq
import com.hihonor.iap.sdk.bean.OwnedPurchasesReq
import com.hihonor.iap.sdk.bean.ProductInfoReq
import com.hihonor.iap.sdk.bean.ProductInfoResult
import com.hihonor.iap.sdk.bean.ProductOrderIntentReq
import com.hihonor.iap.sdk.bean.ProductOrderIntentResult
import com.hihonor.iap.sdk.bean.PurchaseProductInfo
import com.hihonor.iap.sdk.tasks.Task
import com.hihonor.iap.sdk.utils.IapUtil
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume


class AuthBuildForRY: AbsAuthBuildForRY() {
    internal companion object {
        private var mAppId: String? = null
        init {
            mAppId = Auth.getMetaData("com.hihonor.iap.sdk.appid")?.trim()
            require(!mAppId.isNullOrEmpty()) { "请配置 RYAppId" }
        }
    }

    private val iapClient: IapClient = Iap.getIapClient(Auth.application)

    override suspend fun login() = suspendCancellableCoroutine { coroutine ->
        mAction = "login"
        mCallback = { coroutine.resume(it) }

        AuthActivityForRY.callbackActivity = { activity ->
            val signInOptions = SignInOptionBuilder(SignInOptions.DEFAULT_AUTH_REQUEST_PARAM)
                .setClientId(mAppId)
                .createParams()
            HonorIdSignInManager.getService(activity, signInOptions)
                .silentSignIn()
                .addOnSuccessListener { signInAccountInfo ->
                    resultSuccess(signInAccountInfo.toString(), signInAccountInfo.authorizationCode, activity, signInAccountInfo.toJsonObject())
                }
                .addOnFailureListener { e ->
                    val exception = e as ApiException
                    when (exception.statusCode) {
                        // 如果 silentSignIn 接口返回“55：范围未授权” 或“31：账号尚未登录”，然后跳转到授权页面;
                        ErrorStatus.ERROR_SCOPES_NOT_AUTHORIZE, ErrorStatus.ACCOUNT_NON_LOGIN -> {
                            AuthActivityForRY.callbackActivityResult = { requestCode, resultCode, data ->
                                if (requestCode == 1001) {
                                    // 授权页面回调
                                    val accountTask = HonorIdSignInManager.parseAuthResultFromIntent(resultCode, data)
                                    if (accountTask.isSuccessful) {
                                        // 登录成功，获取到账号信息对象signInAccountInfo
                                        val signInAccountInfo = accountTask.result
                                        resultSuccess(signInAccountInfo.toString(), signInAccountInfo.authorizationCode, activity, signInAccountInfo.toJsonObject())
                                    } else {
                                        val ae = accountTask.exception
                                        // 登录失败，详情请参考 API 参考中的状态码
                                        if (ae is ApiException) {
                                            resultError(ae.message, activity, ae, ae.statusCode)
                                        } else {
                                            resultError(ae.message, activity, ae)
                                        }
                                    }
                                } else {
                                    resultError("requestCode 异常：$requestCode", activity)
                                }
                            }
                            val si = SignInOptionBuilder(SignInOptions.DEFAULT_AUTH_REQUEST_PARAM)
                                .setClientId(mAppId)
                                .createParams()
                            val signInIntent = HonorIdSignInManager.getService(activity, si).signInIntent
                            if (signInIntent == null) {
                                resultError("${exception.message}; Honor version too low", activity, e, exception.statusCode)
                            } else {
                                activity.startActivityForResult(signInIntent, 1001)
                            }
                        }
                        ErrorStatus.ERROR_OPER_CANCEL, ErrorStatus.ERROR_CANCEL_AUTH -> resultCancel(activity)
                        else -> resultError(exception.message, activity, e, exception.statusCode)
                    }
                }
        }
        startAuthActivity(AuthActivityForRY::class.java)
    }

    override suspend fun payCheck() = suspendCancellableCoroutine { coroutine ->
        mAction = "payCheck"
        mCallback = { coroutine.resume(it) }

        iapClient.checkEnvReady().addOnSuccessListener {
            resultSuccess("支付可用")
        }.addOnFailureListener {
            resultError(it.message, code = it.errorCode)
        }
    }

    override suspend fun payProductQuery(
        productList: List<String>,
        priceType: RYPriceType,
    ) = suspendCancellableCoroutine { coroutine ->
        mAction = "payProductQuery"
        mCallback = { coroutine.resume(it) }

        val productInfoReq = ProductInfoReq()
        productInfoReq.productType = priceType.code
        productInfoReq.productIds = productList
        val productInfo: Task<ProductInfoResult> = iapClient.getProductInfo(productInfoReq)
        productInfo.addOnSuccessListener { productInfoResult ->
            resultSuccess("查询成功", null, null, productInfoResult.productInfos.map {
                JSONObject().apply {
                    put("ProductType", it.productType)
                    put("ProductId", it.productId)
                    put("ProductName", it.productName)
                    put("ProductDesc", it.productDesc)
                    put("Country", it.country)
                    put("Currency", it.currency)
                    put("MicrosPrice", it.microsPrice)
                    put("OriginalLocalPrice", it.originalLocalPrice)
                    put("OriginalMicroPrice", it.originalMicroPrice)
                    put("Price", it.price)
                    put("Status", it.status)
                }
            })
        }.addOnFailureListener { e ->
            resultError(e.message, code = e.errorCode)
        }
    }

    override suspend fun payPMS(
        productId: String,
        priceType: RYPriceType,
        developerPayload: String?
    ) = suspendCancellableCoroutine { coroutine ->
        mAction = "payPMS"
        mCallback = { coroutine.resume(it) }

        val productOrderIntentReq = ProductOrderIntentReq()
        productOrderIntentReq.productId = productId
        productOrderIntentReq.productType = priceType.code
        productOrderIntentReq.developerPayload = developerPayload
        // productOrderIntentReq.setNeedSandboxTest(1);//传1为沙盒测试
        // 防止掉单 创建订单前，需要调用obtainOwnedPurchases 查询已购买，未消耗的商品，进行消耗
        val productOrderIntent: Task<ProductOrderIntentResult> = iapClient.createProductOrderIntent(productOrderIntentReq)
        productOrderIntent.addOnSuccessListener { createProductOrderResp ->
            val intent = createProductOrderResp.intent
            if (intent != null) {
                AuthActivityForRY.callbackActivity = { activity ->
                    AuthActivityForRY.callbackActivityResult = { requestCode, resultCode, data ->
                        // 客户端并不能100%确保支付结果回调
                        if (requestCode == 4444) {
                            if (resultCode == Activity.RESULT_OK) {
                                payResult(activity, data)
                            } else {
                                resultCancel(activity)  // 取消支付
                            }
                        } else {
                            resultError("requestCode 异常：$requestCode", activity)
                        }
                    }
                    activity.startActivityForResult(intent, 4444)
                }
                startAuthActivity(AuthActivityForRY::class.java)
            } else {
                resultError("创建订单失败", null)
            }
        }.addOnFailureListener { e ->
            // e.errorCode 对应 OrderStatusCode的值
            resultError(e.message, null, null, e.errorCode)
        }
    }
    private fun payResult(activity: Activity, data: Intent?) {
        if (data == null) {
            resultError("结果信息为空", activity)
        } else {
            val purchaseResultInfo = IapUtil.parsePurchaseResultInfoFromIntent(data)
            // PurchaseProductInfo 这里的 PurchaseResultInfo 对象类中不包含业务订单号 bizOrderNo
            if (purchaseResultInfo == null) {
                resultCancel(activity)  // 取消支付
            } else {
                try {
                    val purchaseProductInfo = JsonUtil.parse(purchaseResultInfo.purchaseProductInfo, PurchaseProductInfo::class.java)
                    when (purchaseProductInfo!!.purchaseState) {
                        PurchaseProductInfo.PurchaseState.PAID -> {
                            // 支付成功
                            val jo = JSONObject()
                            jo.put("appId", purchaseProductInfo.appId)
                            jo.put("orderId", purchaseProductInfo.orderId)
                            jo.put("productType", purchaseProductInfo.productType)
                            jo.put("productId", purchaseProductInfo.productId)
                            jo.put("productName", purchaseProductInfo.productName)
                            jo.put("purchaseTime", purchaseProductInfo.purchaseTime)
                            jo.put("consumptionState", purchaseProductInfo.consumptionState)
                            jo.put("purchaseToken", purchaseProductInfo.purchaseToken)
                            jo.put("currency", purchaseProductInfo.currency)
                            jo.put("developerPayload", purchaseProductInfo.developerPayload)
                            jo.put("price", purchaseProductInfo.price)
                            jo.put("displayPrice", purchaseProductInfo.displayPrice)
                            jo.put("purchaseState", purchaseProductInfo.purchaseState)
                            resultSuccess("支付成功", jo.toString(), activity, jo)
                        }
                        PurchaseProductInfo.PurchaseState.UNPAID, PurchaseProductInfo.PurchaseState.PAID_FAILED -> {
                            resultError("付款失败", activity)
                        }
                        else -> {
                            resultError("支付失败", activity)
                        }
                    }
                } catch (e: Throwable) {
                    resultError("支付失败", activity, e)
                }
            }
        }
    }

    override suspend fun payConsume(
        purchaseToken: String
    ) = suspendCancellableCoroutine { coroutine ->
        mAction = "payConsume"
        mCallback = { coroutine.resume(it) }

        // 这里由于网络原因可能调用失败，可以添加重试机制，调用 iapClient.obtainOwnedPurchases ，查询已付款未消耗的商品进行消耗
        val consumeReq = ConsumeReq()
        consumeReq.purchaseToken = purchaseToken
        val consumeRespTask = iapClient.consumeProduct(consumeReq)
        consumeRespTask.addOnSuccessListener { consumeResp ->
            resultSuccess("消耗成功", consumeResp.consumeData)
        }.addOnFailureListener { e ->  // 消耗失败
            resultError(e.message, null, null, e.errorCode)
        }
    }

    override suspend fun purchaseHistoryQuery(
        priceType: RYPriceType,
        continueToken: String?,
        record: Boolean
    ) = suspendCancellableCoroutine { coroutine ->
        mAction = "purchaseHistoryQuery"
        mCallback = { coroutine.resume(it) }

        val ownedPurchasesReq = OwnedPurchasesReq()
        ownedPurchasesReq.productType = priceType.code
        // 传入上一次查询得到的 continueToken，获取新的数据，第一次传空
        ownedPurchasesReq.continuationToken = continueToken
        if (record) {
            iapClient.obtainOwnedPurchaseRecord(ownedPurchasesReq)
        } else {
            iapClient.obtainOwnedPurchases(ownedPurchasesReq)
        }.addOnSuccessListener { ownedPurchasesResult ->
            val jo = JSONObject()
            // ContinueToken 用于获取下一个列表的数据，第一次为空，如果有更多数据 ContinueToken 有值，为空则没有更多数据
            jo.put("continueToken", ownedPurchasesResult.continueToken)
            // 签名算法
            jo.put("sigAlgorithm", ownedPurchasesResult.sigAlgorithm)
            val signJA = JSONArray()
            val purchaseJA = JSONArray()
            // purchaseList 和 sigList 一一对应
            ownedPurchasesResult.sigList?.forEach { signJA.put(it) }
            ownedPurchasesResult.purchaseList?.forEach { purchaseJA.put(it) }
            jo.put("sigList", signJA)
            jo.put("purchaseList", purchaseJA)
            resultSuccess(null, null, null, jo)
        }.addOnFailureListener { e ->
            //   e.errorCode 对应 OrderStatusCode的值
            resultError(e?.message, null, null, e.errorCode)
        }
    }
}