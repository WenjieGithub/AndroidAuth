package cn.moltres.android.auth.ry

import android.app.Activity
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import cn.moltres.android.auth.AbsAuthBuildForRY
import cn.moltres.android.auth.Auth
import cn.moltres.android.auth.RYPriceType
import com.hihonor.appmarketjointsdk.bean.init.AppParams
import com.hihonor.appmarketjointsdk.callback.APICallback
import com.hihonor.appmarketjointsdk.sdk.AMJointSdk
import com.hihonor.iap.framework.utils.JsonUtil
import com.hihonor.iap.sdk.Iap
import com.hihonor.iap.sdk.IapClient
import com.hihonor.iap.sdk.bean.ConsumeReq
import com.hihonor.iap.sdk.bean.OwnedPurchasesReq
import com.hihonor.iap.sdk.bean.ProductInfoReq
import com.hihonor.iap.sdk.bean.ProductInfoResult
import com.hihonor.iap.sdk.bean.ProductOrderIntentReq
import com.hihonor.iap.sdk.bean.ProductOrderIntentResult
import com.hihonor.iap.sdk.bean.ProductOrderIntentWithPriceReq
import com.hihonor.iap.sdk.bean.PurchaseProductInfo
import com.hihonor.iap.sdk.tasks.Task
import com.hihonor.iap.sdk.utils.IapUtil
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume

class AuthBuildForRY: AbsAuthBuildForRY() {
    internal companion object {
        private var mAppId: String? = null
        private var mCpId: String? = null
        init {
            mAppId = Auth.getMetaData("com.hihonor.iap.sdk.appid")?.trim()
            mCpId = Auth.getMetaData("com.hihonor.iap.sdk.cpid")?.trim()
            require(!mAppId.isNullOrEmpty()) { "请配置 RYAppId" }
            require(!mCpId.isNullOrEmpty()) { "请配置 RYCpId" }
        }
    }

    private val iapClient: IapClient = Iap.getIapClient(Auth.application, mAppId!!, mCpId!!)

    override suspend fun jumpToManageSubsPage(activity: Activity) = suspendCancellableCoroutine { coroutine ->
        mAction = "jumpToManageSubsPage"
        mCallback = { coroutine.resume(it) }
//        val req = StartIapActivityReq()
//        req.type = StartIapActivityReq.TYPE_SUBSCRIBE_MANAGER_ACTIVITY
//        val mClient = Iap.getIapClient(activity)
//        val task = mClient.startIapActivity(req)
//        task.addOnSuccessListener { result ->
//            result?.startActivity(activity)     // 请求成功，需拉起IAP返回的页面
//            resultSuccess()
//        }.addOnFailureListener {
//            resultError(it?.message, null, it)
//        }.addOnCanceledListener {
//            resultCancel()
//        }
    }

    override fun onActivityCreate(activity: AppCompatActivity) {
        activity.lifecycleScope.launch(Main) {
            val appParams = AppParams.Builder()
                .appId(mAppId) //从荣耀开发者平台获取的appid
                .setUserPrivacyState(true) //设置用户协议状态
                .build()
            AMJointSdk.init(appParams, object : APICallback {
                override fun onSuccess(result: String) {
                    // 初始化成功，执行进入应用逻辑
                    Auth.logCallback?.invoke("荣耀初始化成功 $result")
                }
                override fun onFailure(errorCode: Int, message: String) {
                    // 错误逻辑
                    Auth.logCallback?.invoke("荣耀初始化失败 code: $errorCode   meg: $message")
                }
            })
        }
    }

    override suspend fun login() = suspendCancellableCoroutine { coroutine ->
        mAction = "login"
        mCallback = { coroutine.resume(it) }

        AMJointSdk.doLogin(object : APICallback {
            override fun onSuccess(resultMessage: String?) {
                //调用登录成功，返回用户信息！进行json解析
                if (resultMessage.isNullOrEmpty()) {
                    resultError("调用登录成功,但信息为空")
                } else {
                    val jsonObject = JSONObject(resultMessage)
                    val openId = jsonObject.optString("openId") //荣耀用户标识，同一用户在不同appid下openID不同
                    val unionId = jsonObject.optString("unionId") //荣耀用户标识，同一用户在同一个开发者下unionId相同
                    val unionToken = jsonObject.optString("unionToken") //联运SDK授权用户身份信息
                    val displayName = jsonObject.optString("displayName") //用户名称
                    val headPictureURL = jsonObject.optString("headPictureURL") //用户头像地址，没有头像时为""
                    resultSuccess("调用登录成功", unionToken, null, jsonObject)
                }
            }
            override fun onFailure(resultCode: Int, resultMessage: String) {
                //失败的话，查看code和msg信息，也可以重新调用登录接口，再次尝试
                if (resultCode == 3002) {
                    resultCancel()
                } else {
                    resultError(resultMessage, null, null, resultCode)
                }
            }
        })
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

    override suspend fun payAmount(
        priceType: RYPriceType,
        productId: String,
        price: Long,
        promotionPrice: Long,
        productName: String,
        bizOrderNo: String,
        developerPayload: String?,

        currency: String,
        needSandboxTest: Int,
//        subPeriod: Int,
//        periodUnit: String,
//        secondChargeTime: String,
    ) = suspendCancellableCoroutine { coroutine ->
        mAction = "payAmount"
        mCallback = { coroutine.resume(it) }

        val req = ProductOrderIntentWithPriceReq()
        req.productType = priceType.code            // 商品类型，目前仅支持：0是消耗型商品，1为非消耗型商品
        req.productId = productId                   // 商品ID
        req.price = price                           // 价格，商品价格为1元时，此处传参100
        req.promotionPrice = promotionPrice         // 优惠价格
        req.productName = productName               // 商品名称
        req.bizOrderNo = bizOrderNo                 // 业务订单号,可以理解为游戏或app自定义订单号
        req.developerPayload = developerPayload     // 商户侧保留信息，支付结果会按传入内容返回
        req.currency = currency                     // 币种，中国：CNY
        req.needSandboxTest = needSandboxTest       // 传1为沙盒测试，0为正式支付
//        req.subPeriod = subPeriod                   // 订购周期，productType为2时必传。
//        req.periodUnit = periodUnit                 // 订购周期单位（W：周，M：月，Y：年。订阅商品有效）
//        req.secondChargeTime = secondChargeTime     // 第二次扣费时间（订阅型商品时传入）,格式yyyy-MM-dd

        // 以上具体参数介绍参考《荣耀应用内支付SDK接口文档》
        // 防止掉单 创建订单前，需要调用obtainOwnedPurchases 查询已购买，未消耗的商品，进行消耗
        val productOrderIntent: Task<ProductOrderIntentResult> = iapClient.createProductOrderIntentWithPrice(req)
        productOrderIntent.addOnSuccessListener { createProductOrderResp ->
            val intent = createProductOrderResp.intent
            if (intent != null) {
                AuthActivityForRY.callbackActivity = { activity ->
                    AuthActivityForRY.callbackActivityResult = { requestCode, resultCode, data ->
                        // 客户端并不能100%确保支付结果回调
                        if (requestCode == 5555) {
                            if (resultCode == Activity.RESULT_OK) {
                                payResult(activity, data)
                            } else {
                                resultCancel(activity)  // 取消支付
                            }
                        } else {
                            resultError("requestCode 异常：$requestCode", activity)
                        }
                    }
                    activity.startActivityForResult(intent, 5555)
                }
                startAuthActivity(AuthActivityForRY::class.java)
            } else {
                resultError("创建订单失败", null)
            }
        }.addOnFailureListener { e: com.hihonor.iap.framework.data.ApiException ->
            //e.errorCode 对应 OrderStatusCode的值
            resultError(e.message, null, null, e.errorCode)
        }
    }
    override suspend fun payPMS(
        productId: String,
        priceType: RYPriceType,
        developerPayload: String?,
        needSandboxTest: Int,
    ) = suspendCancellableCoroutine { coroutine ->
        mAction = "payPMS"
        mCallback = { coroutine.resume(it) }

        val productOrderIntentReq = ProductOrderIntentReq()
        productOrderIntentReq.productId = productId
        productOrderIntentReq.productType = priceType.code
        productOrderIntentReq.developerPayload = developerPayload
        productOrderIntentReq.needSandboxTest = needSandboxTest //传1为沙盒测试
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
                            jo.put("sigAlgorithm", purchaseResultInfo.sigAlgorithm)
                            jo.put("purchaseProductInfo", purchaseResultInfo.purchaseProductInfo)
                            jo.put("purchaseProductInfoSig", purchaseResultInfo.purchaseProductInfoSig)
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