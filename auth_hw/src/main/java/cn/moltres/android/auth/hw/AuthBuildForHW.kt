package cn.moltres.android.auth.hw

import android.app.Activity
import android.content.Intent
import android.content.IntentSender.SendIntentException
import android.text.TextUtils
import android.util.Base64
import cn.moltres.android.auth.AbsAuthBuildForHW
import cn.moltres.android.auth.Auth
import cn.moltres.android.auth.HWPriceType
import com.huawei.agconnect.AGConnectInstance
import com.huawei.agconnect.AGConnectOptionsBuilder
import com.huawei.hms.common.ApiException
import com.huawei.hms.iap.Iap
import com.huawei.hms.iap.IapApiException
import com.huawei.hms.iap.entity.ConsumeOwnedPurchaseReq
import com.huawei.hms.iap.entity.InAppPurchaseData
import com.huawei.hms.iap.entity.IsSandboxActivatedReq
import com.huawei.hms.iap.entity.OrderStatusCode
import com.huawei.hms.iap.entity.OwnedPurchasesReq
import com.huawei.hms.iap.entity.ProductInfo
import com.huawei.hms.iap.entity.ProductInfoReq
import com.huawei.hms.iap.entity.PurchaseIntentReq
import com.huawei.hms.iap.entity.PurchaseIntentWithPriceReq
import com.huawei.hms.iap.entity.StartIapActivityReq
import com.huawei.hms.iap.util.IapClientHelper
import com.huawei.hms.jos.AppParams
import com.huawei.hms.jos.JosApps
import com.huawei.hms.support.account.AccountAuthManager
import com.huawei.hms.support.account.request.AccountAuthParams
import com.huawei.hms.support.account.request.AccountAuthParamsHelper
import com.huawei.updatesdk.service.appmgr.bean.ApkUpgradeInfo
import com.huawei.updatesdk.service.otaupdate.CheckUpdateCallBack
import com.huawei.updatesdk.service.otaupdate.UpdateKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class AuthBuildForHW: AbsAuthBuildForHW() {
    internal companion object {
        init {
//            Auth.getMetaData("HWServicesJson")?.replace("hw", "")?.let {
//                if (it.isNotEmpty()) { Auth.hwServicesJson = it }
//            }
//            if (Auth.hwPublicKey.isNullOrEmpty()) {
//                Auth.hwPublicKey = Auth.getMetaData("HWPublicKey")?.replace("hw", "")
//            }
            if (Auth.hwClientID.isNullOrEmpty()) {
                Auth.hwClientID = Auth.getMetaData("HWClientID")?.replace("hw", "")
            }
            if (Auth.hwClientSecret.isNullOrEmpty()) {
                Auth.hwClientSecret = Auth.getMetaData("HWClientSecret")?.replace("hw", "")
            }
            if (Auth.hwApiKey.isNullOrEmpty()) {
                Auth.hwApiKey = Auth.getMetaData("HWApiKey")?.replace("hw", "")
            }
            if (Auth.hwCpId.isNullOrEmpty()) {
                Auth.hwCpId = Auth.getMetaData("HWCpId")?.replace("hw", "")
            }
            if (Auth.hwProductId.isNullOrEmpty()) {
                Auth.hwProductId = Auth.getMetaData("HWProductId")?.replace("hw", "")
            }
            if (Auth.hwAppId.isNullOrEmpty()) {
                Auth.hwAppId = Auth.getMetaData("HWAppId")?.replace("hw", "")
            }

            try {
                val builder = AGConnectOptionsBuilder()
                builder.inputStream = Auth.application.assets.open(Auth.hwServicesJson)

                if (!Auth.hwClientID.isNullOrEmpty()) { builder.setClientId(Auth.hwClientID) }
                if (!Auth.hwClientSecret.isNullOrEmpty()) { builder.setClientSecret(Auth.hwClientSecret) }
                if (!Auth.hwApiKey.isNullOrEmpty()) { builder.setApiKey(Auth.hwApiKey) }
                if (!Auth.hwCpId.isNullOrEmpty()) { builder.setCPId(Auth.hwCpId) }
                if (!Auth.hwProductId.isNullOrEmpty()) { builder.setProductId(Auth.hwProductId) }
                if (!Auth.hwAppId.isNullOrEmpty()) { builder.setAppId(Auth.hwAppId) }

                AGConnectInstance.initialize(Auth.application, builder)
            } catch (e: IOException) {
                Auth.logCallback?.invoke("华为SDK初始化失败：${e.stackTraceToString()}")
            }
        }
    }

    override fun isSandboxActivated(activity: Activity) {
        val task = Iap.getIapClient(activity).isSandboxActivated(IsSandboxActivatedReq())
        task.addOnSuccessListener {
            Auth.logCallback?.invoke("isSandboxActivated success")
        }.addOnFailureListener {
            if (it is IapApiException) {
                Auth.logCallback?.invoke("isSandboxActivated fail ${it.statusCode}  ${it.message}")
            } else {
                Auth.logCallback?.invoke("isSandboxActivated fail ${it.stackTraceToString()}")
            }
        }
    }

    override fun onActivityCreate(activity: Activity, forceUpdate: Boolean) {
        val params = AccountAuthParams.DEFAULT_AUTH_REQUEST_PARAM
        val appsClient = JosApps.getJosAppsClient(activity)
        val initTask = appsClient.init(AppParams(params))
        initTask.addOnSuccessListener {
            Auth.logCallback?.invoke("$with-onActivityCreate 初始化成功")
        }.addOnFailureListener {
            Auth.logCallback?.invoke("$with-onActivityCreate 初始化失败")
        }

        try {
            val client = JosApps.getAppUpdateClient(activity)
            HWCheck.checkUp(client, activity, object : CheckUpdateCallBack {
                override fun onUpdateInfo(intent: Intent?) {
                    intent?.let {
                        // 获取更新状态码， Default_value为取不到status时默认的返回码，由应用自行决定
                        val status = intent.getIntExtra(UpdateKey.STATUS, 1001001)
                        // 错误码，建议打印
                        val rtnCode = intent.getIntExtra(UpdateKey.FAIL_CODE, 1001001)
                        // 失败信息，建议打印
                        val rtnMessage = intent.getStringExtra(UpdateKey.FAIL_REASON)
                        val info = intent.getSerializableExtra(UpdateKey.INFO)
                        // 可通过获取到的info是否属于ApkUpgradeInfo类型来判断应用是否有更新
                        if (info is ApkUpgradeInfo) {
                            // 这里调用showUpdateDialog接口拉起更新弹窗
                            client.showUpdateDialog(activity, info, forceUpdate)
                        }
                        Auth.logCallback?.invoke("$with-onActivityCreate onUpdateInfo status: $status, rtnCode: $rtnCode, rtnMessage: $rtnMessage")
                    }
                }
                override fun onMarketInstallInfo(intent: Intent?) {
                }
                override fun onMarketStoreError(i: Int) {
                }
                override fun onUpdateStoreError(i: Int) {
                }
            })
        } catch (e: Exception) {
            Auth.logCallback?.invoke("$with-onActivityCreate checkUp失败 ${e.stackTraceToString()}")
        }
    }

    override suspend fun jumpToManageSubsPage(activity: Activity) = suspendCancellableCoroutine { coroutine ->
        mAction = "jumpToManageSubsPage"
        mCallback = { coroutine.resume(it) }
        val req = StartIapActivityReq()
        req.type = StartIapActivityReq.TYPE_SUBSCRIBE_MANAGER_ACTIVITY
        val mClient = Iap.getIapClient(activity)
        val task = mClient.startIapActivity(req)
        task.addOnSuccessListener { result ->
            result?.startActivity(activity)     // 请求成功，需拉起IAP返回的页面
            resultSuccess()
        }.addOnFailureListener {
            resultError(it?.message, null, it)
        }.addOnCanceledListener {
            resultCancel()
        }
    }

    override suspend fun login() = suspendCancellableCoroutine { coroutine ->
        mAction = "login"
        mCallback = { coroutine.resume(it) }
        AuthActivityForHW.callbackActivity = { activity ->
            // 1、配置登录请求参数AccountAuthParams，包括请求用户的id(openid、unionid)、email、profile(昵称、头像)等;
            // 2、DEFAULT_AUTH_REQUEST_PARAM默认包含了id和profile（昵称、头像）的请求;
            // 3、如需要再获取用户邮箱，需要setEmail();
            // 4、通过setAuthorizationCode()来选择使用code模式，最终所有请求的用户信息都可以调服务器的接口获取；
            val authParam = AccountAuthParamsHelper(AccountAuthParams.DEFAULT_AUTH_REQUEST_PARAM)
                .setEmail()
                .setAuthorizationCode()
                .createParams()
            // 使用请求参数构造华为帐号登录授权服务AccountAuthService
            val authService = AccountAuthManager.getService(activity, authParam)
            // 使用静默登录进行华为帐号登录
            val task = authService.silentSignIn()
            task.addOnSuccessListener { authAccount ->  // 静默登录成功，处理返回的帐号对象AuthAccount，获取帐号信息并处理
                resultSuccess(authAccount.toString(), authAccount.authorizationCode, activity)
            }
            task.addOnFailureListener { ae ->            //静默登录失败，使用getSignInIntent()方法进行前台显式登录
                if (ae is ApiException) {
                    AuthActivityForHW.callbackActivityResult = { requestCode, _, data ->
                        if (requestCode == 8888) {
                            if (data == null) {
                                resultError("返回数据为空", activity)
                            } else {
                                val authAccountTask = AccountAuthManager.parseAuthResultFromIntent(data)
                                when {
                                    authAccountTask.isSuccessful -> resultSuccess(authAccountTask.result.toJson(), authAccountTask.result.authorizationCode, activity)
                                    authAccountTask.isCanceled -> resultCancel(activity)
                                    else -> {
                                        val e = authAccountTask.exception
                                        when {
                                            e is ApiException -> {
                                                if (e.statusCode == 2012) {
                                                    resultCancel(activity)
                                                } else {
                                                    resultError("code: ${e.statusCode}; msg: ${e.message}", activity, e)
                                                }
                                            }
                                            e != null -> resultError(e.message, activity, e)
                                            else -> resultError("登录失败", activity)
                                        }
                                    }
                                }
                            }
                        } else {
                            resultError("requestCode 异常：$requestCode", activity)
                        }
                    }
                    // 如果应用是全屏显示，即顶部无状态栏的应用，需要在Intent中添加如下参数：
                    // intent.putExtra(CommonConstant.RequestParams.IS_FULL_SCREEN, true)
                    activity.startActivityForResult(authService.signInIntent, 8888)
                } else {
                    resultError(ae?.message, activity, ae)
                }
            }
        }
        startAuthActivity(AuthActivityForHW::class.java)
    }

    private fun getActivity(activity: Activity?, callback: (Activity, Activity?) -> Unit) {
        if (activity == null) {
            AuthActivityForHW.callbackActivity = {
                callback(it, it)
            }
            startAuthActivity(AuthActivityForHW::class.java)
        } else {
            callback(activity, null)
        }
    }
    private suspend fun getActivitySus(activity: Activity?) = suspendCancellableCoroutine<Pair<Activity, Activity?>> { coroutine ->
        if (activity == null) {
            AuthActivityForHW.callbackActivity = {
                coroutine.resume(Pair(it, it))
            }
            startAuthActivity(AuthActivityForHW::class.java)
        } else {
            coroutine.resume(Pair(activity, null))
        }
    }

    override suspend fun cancelAuth(activity: Activity?) = suspendCancellableCoroutine { coroutine ->
        mAction = "cancelAuth"
        mCallback = { coroutine.resume(it) }
        val authParam = AccountAuthParamsHelper(AccountAuthParams.DEFAULT_AUTH_REQUEST_PARAM)
            .setEmail()
            .setAuthorizationCode()
            .createParams()
        getActivity(activity) { a, af ->
            // 使用请求参数构造华为帐号登录授权服务AccountAuthService
            val authService = AccountAuthManager.getService(a, authParam)
            val task = authService.cancelAuthorization()
            task.addOnSuccessListener {
                resultSuccess(activity = af)
            }
            task.addOnFailureListener { e ->
                resultError(e?.message, af, e)
            }
        }
    }

    override suspend fun purchaseHistoryQuery(
        priceType: HWPriceType,
        record: Boolean,
        activity: Activity?
    ) = suspendCancellableCoroutine { coroutine ->
        mAction = "purchaseHistoryQuery"
        mCallback = { coroutine.resume(it) }
        val req = OwnedPurchasesReq()
        req.priceType = priceType.code
        getActivity(activity) { a, af ->
            val task = if (record) {
                Iap.getIapClient(a).obtainOwnedPurchaseRecord(req)
            } else {
                Iap.getIapClient(a).obtainOwnedPurchases(req)
            }
            task.addOnSuccessListener { result ->
                val jsonArray = JSONArray()
                for (i in result.inAppPurchaseDataList.indices) {
                    val inAppSignature = result.inAppSignature[i]
                    val inAppPurchaseData = result.inAppPurchaseDataList[i]
                    try {
                        val inAppPurchaseDataBean = InAppPurchaseData(inAppPurchaseData)
                        val jo = JSONObject()
                        // 当 purchaseState 为 0 时表示此次交易是成功的
                        jo.put("purchaseState", inAppPurchaseDataBean.purchaseState)
                        jo.put("orderSn", inAppPurchaseDataBean.developerPayload)
                        jo.put("purchaseToken", inAppPurchaseDataBean.purchaseToken)
                        jo.put("inAppSignature", inAppSignature)
                        jo.put("inAppPurchaseData", inAppPurchaseData)
                        jo.put("isSubValid", inAppPurchaseDataBean.isSubValid)
                        jsonArray.put(jo)
                    } catch (e: Exception) {
                        Auth.logCallback?.invoke("华为 purchaseHistoryQuery 序列化 json 异常: ${e.stackTraceToString()}")
                    }
                }
                resultSuccess(null, null, af, jsonArray)
            }.addOnFailureListener { e ->
                if (e is IapApiException) {
                    resultError("code: ${e.statusCode}; msg: ${e.message}", af, e)
                } else {
                    resultError(e?.message, af, e)
                }
            }
        }
    }

    override suspend fun payCheck() = suspendCancellableCoroutine { coroutine ->
        mAction = "payCheck"
        mCallback = { coroutine.resume(it) }
        AuthActivityForHW.callbackActivity = { activity ->
            val task = Iap.getIapClient(activity).isEnvReady
            task.addOnSuccessListener { result ->
                if (result.returnCode == 0) {
                    resultSuccess(null, null, activity)
                } else {
                    resultError("code: ${result.returnCode}", activity)
                }
            }.addOnFailureListener { e ->
                if (e is IapApiException) {
                    if (e.status.statusCode == OrderStatusCode.ORDER_HWID_NOT_LOGIN) {  // 未登录
                        if (e.status.hasResolution()) {
                            try {
                                AuthActivityForHW.callbackActivityResult = { requestCode, _, data ->
                                    if (requestCode == 6666 && data != null && IapClientHelper.parseRespCodeFromIntent(data) == 0) {
                                        resultSuccess(null, null, activity)
                                    } else {
                                        resultError("支付登录失败", activity)
                                    }
                                }
                                e.status.startResolutionForResult(activity, 6666)
                            } catch (exp: SendIntentException) {
                                resultError("未登陆，调起登录界面失败: ${exp.message}", activity, e)
                            }
                        } else {
                            resultError("未登陆，调起登录界面失败: ${e.message}", activity, e)
                        }
                    } else if (e.status.statusCode == OrderStatusCode.ORDER_ACCOUNT_AREA_NOT_SUPPORTED) {
                        resultError("暂未支持地区 code: ${e.status.statusCode}; msg: ${e.status.statusMessage}", activity, e)
                    } else {
                        resultError("code: ${e.status.statusCode}; msg: ${e.status.errorString}", activity, e)
                    }
                } else {
                    resultError(e?.message, activity, e)
                }
            }
        }
        startAuthActivity(AuthActivityForHW::class.java)
    }

    override suspend fun payConsume(
        purchaseToken: String,
        activity: Activity?
    ) = suspendCancellableCoroutine { coroutine ->
        mAction = "payConsume"
        mCallback = { coroutine.resume(it) }
        val req = ConsumeOwnedPurchaseReq()
        req.purchaseToken = purchaseToken
        getActivity(activity) { a, af ->
            // 消耗型商品发货成功后，需调用consumeOwnedPurchase接口进行消耗
            val task = Iap.getIapClient(a).consumeOwnedPurchase(req)
            task.addOnSuccessListener { result ->
                resultSuccess("code: ${result.returnCode}; msg: ${result.errMsg}", null, af)
            }.addOnFailureListener { e ->
                if (e is IapApiException) {
                    resultError(e.status.toString(), af, e)
                } else {
                    resultError(e?.message, af, e)
                }
            }
        }
    }

    override suspend fun payProductQuery(
        productListSubs: List<String>?,
        productListConsumable: List<String>?,
        productListNonConsumable: List<String>?,
        activity: Activity?
    ) = withContext(Dispatchers.Default) {
        mAction = "payProductQuery"
        val productInfoList = mutableListOf<ProductInfo>()
        val errorMsg = StringBuilder()
        val pa = getActivitySus(activity)

        if (!productListConsumable.isNullOrEmpty()) {
            try {
                val result = async { payProductQueryQueryRun(pa.first, productListConsumable, 0) }.await()
                productInfoList.addAll(result)
            } catch (e: Exception) {
                val returnCode = if (e is IapApiException) { e.statusCode } else { -1 }     // 其他外部错误
                errorMsg.append("消耗型商品查询异常，code=$returnCode e=${e.stackTraceToString()}")
                errorMsg.append("\n\n       ")
            }
        }
        if (!productListNonConsumable.isNullOrEmpty()) {
            try {
                val result = async { payProductQueryQueryRun(pa.first, productListNonConsumable, 1) }.await()
                productInfoList.addAll(result)
            } catch (e: Exception) {
                val returnCode = if (e is IapApiException) { e.statusCode } else { -1 }     // 其他外部错误
                errorMsg.append("非消耗型商品查询异常，code=$returnCode e=${e.stackTraceToString()}")
                errorMsg.append("\n\n       ")
            }
        }
        if (!productListSubs.isNullOrEmpty()) {
            try {
                val result = async { payProductQueryQueryRun(pa.first, productListSubs, 2) }.await()
                productInfoList.addAll(result)
            } catch (e: Exception) {
                val returnCode = if (e is IapApiException) { e.statusCode } else { -1 }     // 其他外部错误
                errorMsg.append("订阅型商品查询异常，code=$returnCode e=${e.stackTraceToString()}")
                errorMsg.append("\n\n       ")
            }
        }

        when {
            productInfoList.isNotEmpty() -> {
                resultSuccess(msg = errorMsg.toString() ,activity = pa.second, any = productInfoList.map {
                    JSONObject().apply {
                        put("ProductId", it.productId)
                        put("PriceType", it.priceType)
                        put("Price", it.price)
                        put("MicrosPrice", it.microsPrice)
                        put("OriginalLocalPrice", it.originalLocalPrice)
                        put("OriginalMicroPrice", it.originalMicroPrice)
                        put("Currency", it.currency)
                        put("ProductName", it.productName)
                        put("ProductDesc", it.productDesc)
                        put("SubPeriod", it.subPeriod)
                        put("SubSpecialPrice", it.subSpecialPrice)
                        put("SubSpecialPriceMicros", it.subSpecialPriceMicros)
                        put("SubSpecialPeriod", it.subSpecialPeriod)
                        put("SubSpecialPeriodCycles", it.subSpecialPeriodCycles)
                        put("SubFreeTrialPeriod", it.subFreeTrialPeriod)
                        put("SubGroupId", it.subGroupId)
                        put("SubGroupTitle", it.subGroupTitle)
                        put("SubProductLevel", it.subProductLevel)
                        put("Status", it.status)
                    }
                })
            }
            errorMsg.isEmpty() -> resultSuccess("没有查询到数据", activity = pa.second)
            else -> resultError(errorMsg.toString(), activity = pa.second)
        }
    }
    private suspend fun payProductQueryQueryRun(activity: Activity, list: List<String>, type: Int) = suspendCoroutine<List<ProductInfo>> {
        val req = ProductInfoReq()
        req.priceType = type                                        // priceType: 0：消耗型商品; 1：非消耗型商品; 2：订阅型商品
        req.productIds = list
        val task = Iap.getIapClient(activity).obtainProductInfo(req)// 调用obtainProductInfo接口获取AppGallery Connect网站配置的商品的详情信息
        task.addOnSuccessListener { result ->
            it.resumeWith(runCatching {
                if (result.returnCode == 0) {
                    result.productInfoList                          // 获取接口请求成功时返回的商品详情信息
                } else {
                    throw Exception("code: ${result.returnCode}  msg: ${result.errMsg}")
                }
            })
        }.addOnFailureListener { e ->
            it.resumeWithException(e)
        }
    }

    override suspend fun payPMS(
        productId: String,
        priceType: HWPriceType,
        developerPayload: String?,
        publicKey: String?,
    ) = suspendCancellableCoroutine { coroutine ->
        mAction = "payPMS"
        mCallback = { coroutine.resume(it) }
        AuthActivityForHW.callbackActivity = { activity ->
            val req = PurchaseIntentReq()
            req.productId = productId
            req.priceType = priceType.code
            req.developerPayload = developerPayload
            val task = Iap.getIapClient(activity).createPurchaseIntent(req)
            task.addOnSuccessListener { result ->
                val status = result.status
                if (status.hasResolution()) {
                    try {
                        AuthActivityForHW.callbackActivityResult = { requestCode, _, data ->
                            if (requestCode == 4444) {
//                                val pk = publicKey ?: Auth.hwPublicKey ?: ""
                                payResult(activity, data, "pk", developerPayload)
                            } else {
                                resultError("requestCode 异常：$requestCode", activity)
                            }
                        }
                        status.startResolutionForResult(activity, 4444)
                    } catch (exp: Exception) {
                        resultError("支付失败: ${exp.message}", activity, exp)
                    }
                } else {
                    resultError(result.status.toString(), activity)
                }
            }.addOnFailureListener { e ->
                if (e is IapApiException) {
                    resultError(e.status.toString(), activity, e)
                } else {
                    resultError(e.stackTraceToString(), activity, e)
                }
            }
        }
        startAuthActivity(AuthActivityForHW::class.java)
    }
    override suspend fun payAmount(
        priceType: HWPriceType,
        productId: String,
        productName: String,
        amount: String,
        sdkChannel: String,
        country: String,
        currency: String,
        developerPayload: String?,
        serviceCatalog: String,
        publicKey: String?,
    ) = suspendCancellableCoroutine { coroutine ->
        mAction = "payAmount"
        mCallback = { coroutine.resume(it) }
        AuthActivityForHW.callbackActivity = { activity ->
            val req = PurchaseIntentWithPriceReq()
            req.priceType = priceType.code
            req.productId = productId
            req.productName = productName
            req.amount = amount
            req.sdkChannel = sdkChannel
            req.country = country
            req.currency = currency
            req.developerPayload = developerPayload
            req.serviceCatalog = serviceCatalog
//            val pk = publicKey ?: Auth.hwPublicKey ?: ""
            payAmount(activity, req, "pk", developerPayload)
        }
        startAuthActivity(AuthActivityForHW::class.java)
    }
    private fun payAmount(activity: Activity, req: PurchaseIntentWithPriceReq, publicKey: String, developerPayload: String?) {
        val task = Iap.getIapClient(activity).createPurchaseIntentWithPrice(req)
        task.addOnSuccessListener { result ->
            val paymentData = result.paymentData
            val paymentSignature = result.paymentSignature
//            if (doCheck(paymentData, paymentSignature, publicKey)) {
                val status = result.status
                if (status.hasResolution()) {
                    try {
                        AuthActivityForHW.callbackActivityResult = { requestCode, _, data ->
                            if (requestCode == 7777) {
                                payResult(activity, data, publicKey, developerPayload)
                            } else {
                                resultError("requestCode 异常：$requestCode", activity)
                            }
                        }
                        status.startResolutionForResult(activity, 7777)
                    } catch (exp: SendIntentException) {
                        resultError("打开结账页面失败", activity, exp)
                    }
                } else {
                    resultError("打开结账页面失败: ${result.errMsg}", activity)
                }
//            } else {
//                resultError("验签错误: paymentData=$paymentData paymentSignature=$paymentSignature result=$result", activity)
//            }
        }.addOnFailureListener { e ->
            if (e is IapApiException) {
                when (e.statusCode) {
                    OrderStatusCode.ORDER_HWID_NOT_LOGIN, OrderStatusCode.ORDER_NOT_ACCEPT_AGREEMENT -> {
                        if (e.status.hasResolution()) {
                            try {
                                AuthActivityForHW.callbackActivityResult = { requestCode, _, data ->
                                    if (requestCode == 5555) {
                                        if (data != null) {             // 非托管支付调起登录界面
                                            val returnCode = IapClientHelper.parseRespCodeFromIntent(data)
                                            if (returnCode == OrderStatusCode.ORDER_STATE_SUCCESS) {
                                                payAmount(activity, req, publicKey, developerPayload) // 成功再次发起支付
                                            } else {
                                                resultError("code: $returnCode; msg: 调起登录界面失败", activity)
                                            }
                                        } else {
                                            resultError("支付登录失败，数据返回空", activity)
                                        }
                                    } else {
                                        resultError("requestCode 异常：$requestCode", activity)
                                    }
                                }
                                e.status.startResolutionForResult(activity, 5555)
                            } catch (exp: SendIntentException) {
                                resultError("code: ${e.statusCode}; msg: ${e.message}; 调起授权界面失败", activity, exp)
                            }
                        } else {
                            resultError("code: ${e.statusCode}; msg: ${e.message}; 未登录或未同意支付协议", activity)
                        }
                    }
                    OrderStatusCode.ORDER_PRODUCT_OWNED -> resultError("code: ${e.statusCode}; msg: ${e.message}; 已拥有该商品", activity)
                    else -> resultError("code: ${e.statusCode}; msg: ${e.message}", activity)
                }
            } else {
                resultError("支付失败: $e", activity, e)
            }
        }
    }
    private fun payResult(activity: Activity, data: Intent?, publicKey: String, developerPayload: String?) {
        if (data == null) {
            resultError("结果信息为空", activity)
        } else {
            val purchaseResultInfo = Iap.getIapClient(activity).parsePurchaseResultInfoFromIntent(data)
            when (purchaseResultInfo.returnCode) {
                OrderStatusCode.ORDER_STATE_CANCEL -> resultCancel(activity)
                OrderStatusCode.ORDER_STATE_FAILED,
                OrderStatusCode.ORDER_PRODUCT_OWNED,
                    // todo 消耗型商品需要补单
                OrderStatusCode.ORDER_STATE_DEFAULT_CODE ->
                    resultError("如果是消耗型商品需要补单：code: ${purchaseResultInfo.returnCode}; msg: ${purchaseResultInfo.errMsg}", activity, null, 1001)
                OrderStatusCode.ORDER_STATE_SUCCESS -> {
                    val iApd = purchaseResultInfo.inAppPurchaseData
                    val iAds = purchaseResultInfo.inAppDataSignature
//                    if (doCheck(iApd, iAds, publicKey)) {
                        try {
                            val d = InAppPurchaseData(iApd)
                            if (d.purchaseState == 0 || d.purchaseState == -1) {
                                val jo = JSONObject()
                                jo.put("orderSn", developerPayload)
                                jo.put("purchaseToken", d.purchaseToken)
                                jo.put("inAppPurchaseData", iApd)
                                jo.put("inAppDataSignature", iAds)
                                jo.put("purchaseState", d.purchaseState)
                                resultSuccess("支付成功", jo.toString(), activity, jo)
                            } else {
                                resultError("支付状态异常: code=${d.purchaseState}", activity)
                            }
                        } catch (e: Exception) {
                            resultError("订单信息解析失败", activity, e)
                        }
//                    } else {
//                        resultError("验签失败: iApd=$iApd; iAds=$iAds; purchaseResultInfo=${purchaseResultInfo}", activity)
//                    }
                }
                else -> resultError("code: ${purchaseResultInfo.returnCode}; msg: ${purchaseResultInfo.errMsg}", activity)
            }
        }
    }

//    /**
//     * 校验签名信息
//     * @param content 结果字符串
//     * @param sign 签名字符串
//     * @param publicKey 支付公钥
//     * @return 是否校验通过
//     */
//    private fun doCheck(content: String, sign: String, publicKey: String): Boolean {
//        try {
//            if (!TextUtils.isEmpty(sign) && !TextUtils.isEmpty(publicKey)) {
//                val keyFactory = KeyFactory.getInstance("RSA")
//                val encodedKey = Base64.decode(publicKey, Base64.DEFAULT)
//                val pubKey = keyFactory.generatePublic(X509EncodedKeySpec(encodedKey))
//                val signature = Signature.getInstance("SHA256WithRSA")
//                signature.initVerify(pubKey)
//                signature.update(content.toByteArray(StandardCharsets.UTF_8))
//                val bSign = Base64.decode(sign, Base64.DEFAULT)
//                return signature.verify(bSign)
//            }
//        } catch (e: Exception) {
//            e.printStackTrace()
//        }
//        return false
//    }
}