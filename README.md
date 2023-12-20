# Auth
- Auth 是一款第三方登陆、分享、支付的快速集成库。
- 支持平台包括微信、QQ、微博、支付宝、华为、荣耀、小米、银联、GooglePay。
- 支持 Intent 方式调用 twitter、facebook 等分享。
- 根据项目需求按需添加对应平台依赖。

## 配置项目

### 配置maven仓库
```groovy
maven { url 'https://jitpack.io' }                              // jitpack仓库
maven { url 'https://developer.huawei.com/repo/' }              // 华为仓库
maven { url 'https://developer.hihonor.com/repo' }              // 荣耀仓库
maven { url 'https://maven.aliyun.com/repository/google' }      // 小米有些库需要jcenter
maven { url 'https://maven.aliyun.com/repository/public' }      // 小米有些库需要jcenter
maven {                                                         // 小米仓库
    credentials {
        username '5f45c9022d5925c55bc00c6f'
        password 'NQwPJAa42nlV'
    }
    url 'https://packages.aliyun.com/maven/repository/2028284-release-awMPKn/'
}
```

### 配置微博支持的 SO 架构
```groovy
ndk { abiFilters 'armeabi', 'armeabi-v7a', 'arm64-v8a' }
```

### 添加依赖
```groovy
implementation 'androidx.appcompat:appcompat:1.x.x'

def auth_version = "0.8.0"
implementation "cn.moltres.android:auth:$auth_version"
implementation "cn.moltres.android:auth_qq:$auth_version"
implementation "cn.moltres.android:auth_wb:$auth_version"
implementation "cn.moltres.android:auth_wx:$auth_version"
implementation "cn.moltres.android:auth_zfb:$auth_version"
implementation "cn.moltres.android:auth_yl:$auth_version"
implementation "cn.moltres.android:auth_hw:$auth_version"
implementation "cn.moltres.android:auth_xm:$auth_version"
implementation "cn.moltres.android:auth_ry:$auth_version"
implementation "cn.moltres.android:auth_google:$auth_version"
```

### app build.gradle 中配置相应平台参数(必填, 可为空字符串用代码替换)，未依赖平台可忽略, 也可在代码中配置(会替换 Manifest 中的值)
```groovy
manifestPlaceholders = [
        // 微博 (可代码配置)
        WBAppKey:"xxx",
        WBScope:"xxx",
        WBRedirectUrl:"xxx",
        // 微信 (可代码配置)
        WXAppId:"",
        // QQ Authorities 为 Manifest 文件中注册 FileProvider 时设置的 authorities 属性值
        QQAppId:"xxx",
        QQAuthorities:"xxx",
        // 支付宝
        ZFBScheme:"xxx",
        
        // 小米 (可代码配置)
        XMAppId:"xxx",
        XMAppKey:"xxx",
        // 华为 (可代码配置, agconnect-services.json 不包含密钥时使用的参数, 包含无需配置)
        HWServicesJson:"xxx",   // 配置文件名称, 默认 agconnect-services.json 
        HWPublicKey:"xxx",      // 验签用公钥, 支付时使用, 也可通过方法参数的方式使用
        HWCpId:"xxx",
        HWAppId:"xxx",
        HWApiKey:"xxx",
        HWClientID:"xxx",
        HWProductId:"xxx",
        HWClientSecret:"xxx",
        // 荣耀
        RYAppId:"xxx",
        RYCpId:"xxx"
]
```

### 集成华为 SDK 时需要配置 json 文件，json 文件来自华为
- assets 中添加 agconnect-services.json 文件


## 使用

### 初始化
```kotlin
Auth.init(application)
Auth.logCallback = { str ->
    // 输出日志
}
// 微信 manifest 中配置过 WXAppId, 不用再此配置
Auth.wxAppId = "AppId"
// 微博 manifest 中配置过, 不用再此配置
Auth.wbAppKey = "AppKey"
Auth.wbUrl = "Url"
Auth.wbScope = "Scope"
// 小米 manifest 中配置过, 不用再此配置
Auth.xmAppId = "AppId"
Auth.xmAppKey = "AppKey"
// 华为 manifest 中配置过, 不用再此配置. agconnect-services.json 不包含密钥时使用的参数, 包含无需配置
Auth.hwServicesJson = "agconnect-services-test.json"
Auth.hwPublicKey = "key"
Auth.hwCpId = "CpId"
Auth.hwAppId = "AppId"
Auth.hwApiKey = "ApiKey"
Auth.hwClientID = "ClientID"
Auth.hwProductId = "ProductId"
Auth.hwClientSecret = "ClientSecret"
```

### 同意隐私协议后初始化
```kotlin
// 微信
Auth.withWX().registerCallback { }  // 注册回调 微信请求数据会在此回调内，按需解析数据
// 微博(可选)
Auth.withWB()                       // 微博是异步初始化, 避免调用时初始化出现未初始化完成的问题, 所以提前调用一下, 初始化SDK (库内使用方式是延迟初始化, 第一次调用才做初始化)
// 华为
Auth.withHW()
// 小米
Auth.withXM()
```

### 应用打开主页时调用，华为、小米需要（根据联运要求）
```kotlin
Auth.withHW().onActivityCreate(activity)
Auth.withXM().onActivityCreate(activity)
Auth.withXM().onActivityDestroy()
```

### 登陆、分享功能，注意调用的如果是 suspend 函数需要在携程内调用
```kotlin
lifecycleScope.launch {
    val loginResult = Auth.withWX().login()
    val shareLinkResult = Auth.withWX().shareLink("http://www.baidu.com")
    // 根据 result 判断是否成功
}
```

### 华为支付流程
1. 支付前检查是否支持
2. PMS商品，需要先查询商品列表
3. 调用支付
4. 消耗型商品和服务器核对后消耗（服务端也可消耗）

#### 消耗型商品的补单
1. 购买消耗型商品支付后返回 AuthResult 为 Error，且 code=1001 时
2. 应用启动时
3. 调用以下代码查询，根据返回数据进行补单操作，一般数据上传给服务器进行后续操作
4. 订阅和非消耗型商品也可通过此接口查询记录
```kotlin
Auth.withHW.purchaseHistoryQuery(activity, 0, false)
```

### google 支付
- 结果返回 Error 时，如果 code=1001、1002 请再次重新尝试购买（连接Google结算库失败，重试次数自定义）

### 小米支付
- 小米依赖支付宝 sdk，集成时需要添加支付宝集成；
- 支付前判断是否登录状态，未登录先调用登录

### 使用系统分享：更多分享、根据包名调用应用分享
1. 调用 Auth.withMore()
2. 需要传参目标应用包名，在清单文件中添加<queries>标签，并将目标应用包名加入
3. 库中已经添加<queries>标签，无需再添加的标签：
```xml
    <queries>
        <package android:name="com.twitter.android" />
        <package android:name="com.whatsapp" />
        <package android:name="com.linkedin.android" />
        <package android:name="com.instagram.android" />
        <package android:name="com.facebook.katana" />
    </queries>
```

## 第三方库版本及对应链接
- [微信 : 6.8.0](https://developers.weixin.qq.com/doc/oplatform/Mobile_App/Access_Guide/Android.html)
- [QQ : 3.5.14](https://wiki.connect.qq.com/qq%e7%99%bb%e5%bd%95)
- [微博 : 13.6.1](https://github.com/sinaweibosdk/weibo_android_sdk)
- [支付宝: 15.8.16](https://docs.open.alipay.com/204/105296/)

- [Google Pay billing-ktx:5.0.0](https://developer.android.com/google/play/billing/integrate#fetch)
- [华为联运: 6.4.0](https://developer.huawei.com/consumer/cn/doc/development/HMS-Guides/iap-development-guide-v4)
- [小米联运: 3.5.3](https://dev.mi.com/distribute/doc/details?pId=1150#6)
- [银联: 3.5.9](https://open.unionpay.com/tjweb/doc/mchnt/list?productId=3)
- [荣耀: 6.0.3.005](https://developer.hihonor.com/cn/kitdoc?category=%E5%9F%BA%E7%A1%80%E6%9C%8D%E5%8A%A1&kitId=11001&navigation=guides&docId=android-intergrate-sdk.md)
