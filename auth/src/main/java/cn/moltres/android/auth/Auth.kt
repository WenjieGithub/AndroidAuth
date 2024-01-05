package cn.moltres.android.auth

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import java.lang.reflect.InvocationTargetException

object Auth {
    lateinit var application: Application
    var logCallback: ((String) -> Unit)? = null
    val separatorLine = System.getProperty("line.separator") ?: "\n"        // 换行符

    var wxAppId: String? = null

    var wbAppKey: String? = null
    var wbUrl: String? = null
    var wbScope: String? = null

    var xmAppId: String? = null
    var xmAppKey: String? = null

    /** agconnect-services.json 不包含密钥时使用的参数, agconnect-services.json 文件名不能修改, 否则会导致初始化失败 */
    const val hwServicesJson: String = "agconnect-services.json"// 由于华为不同功能sdk会内部自动初始化, 所以不可配置文件名, 期待后续改进
    var hwPublicKey: String? = null
    var hwClientID: String? = null
    var hwClientSecret: String? = null
    var hwApiKey: String? = null
    var hwCpId: String? = null
    var hwProductId: String? = null
    var hwAppId: String? = null


    /**
     * 初始化
     */
    fun init(app: Application) {
        application = app
    }

    fun getMetaData(key: String): String? {
        return application.packageManager?.getApplicationInfo(
            application.packageName,
            PackageManager.GET_META_DATA
        )?.metaData?.getString(key)
    }

    //检测是否安装
    fun isInstalled(packageName: String, intent: Intent): Boolean {
        val resInfo = application.packageManager.queryIntentActivities(intent, 0)
        if (resInfo.isNotEmpty()) {
            for (info in resInfo) {
                val activityInfo = info.activityInfo
                if (activityInfo.packageName.contains(packageName)) {
                    return true
                }
            }
        }
        return false
    }

    fun withMore(): AuthBuildForMore {
        return AuthBuildForMore
    }

    fun withGoogle(): AbsAuthBuildForGoogle {
        return try {
            val constructor = Class.forName("cn.moltres.android.auth.google.AuthBuildForGoogle").getConstructor()
            constructor.newInstance() as AbsAuthBuildForGoogle
        } catch (e: Exception) {
            throw NullPointerException("添加谷歌依赖, 并配置: $e ${(e as? InvocationTargetException)?.targetException}")
        }
    }

    fun withQQ(): AbsAuthBuildForQQ {
        return try {
            val constructor = Class.forName("cn.moltres.android.auth.qq.AuthBuildForQQ").getConstructor()
            constructor.newInstance() as AbsAuthBuildForQQ
        } catch (e: Exception) {
            throw IllegalAccessException("添加QQ依赖, 并配置: $e ${(e as? InvocationTargetException)?.targetException}")
        }
    }

    fun withWB(): AbsAuthBuildForWB {
        return try {
            val constructor = Class.forName("cn.moltres.android.auth.wb.AuthBuildForWB").getConstructor()
            constructor.newInstance() as AbsAuthBuildForWB
        } catch (e: Exception) {
            throw IllegalAccessException("添加微博依赖, 并配置: $e ${(e as? InvocationTargetException)?.targetException}")
        }
    }

    fun withWX(): AbsAuthBuildForWX {
        return try {
            val constructor = Class.forName("cn.moltres.android.auth.wx.AuthBuildForWX").getConstructor()
            constructor.newInstance() as AbsAuthBuildForWX
        } catch (e: Exception) {
            throw IllegalAccessException("添加微信依赖, 并配置: $e ${(e as? InvocationTargetException)?.targetException}")
        }
    }

    fun withZFB(): AbsAuthBuildForZFB {
        return try {
            val constructor = Class.forName("cn.moltres.android.auth.zfb.AuthBuildForZFB").getConstructor()
            constructor.newInstance() as AbsAuthBuildForZFB
        } catch (e: Exception) {
            throw IllegalAccessException("添加支付宝依赖, 并配置: $e ${(e as? InvocationTargetException)?.targetException}")
        }
    }

    fun withHW(): AbsAuthBuildForHW {
        return try {
            val constructor = Class.forName("cn.moltres.android.auth.hw.AuthBuildForHW").getConstructor()
            constructor.newInstance() as AbsAuthBuildForHW
        } catch (e: Exception) {
            throw IllegalAccessException("添加华为依赖, 并配置: $e ${(e as? InvocationTargetException)?.targetException}")
        }
    }

    fun withXM(): AbsAuthBuildForXM {
        return try {
            val constructor = Class.forName("cn.moltres.android.auth.xm.AuthBuildForXM").getConstructor()
            constructor.newInstance() as AbsAuthBuildForXM
        } catch (e: Exception) {
            throw IllegalAccessException("添加小米依赖, 并配置: $e ${(e as? InvocationTargetException)?.targetException}")
        }
    }

    fun withRY(): AbsAuthBuildForRY {
        return try {
            val constructor = Class.forName("cn.moltres.android.auth.ry.AuthBuildForRY").getConstructor()
            constructor.newInstance() as AbsAuthBuildForRY
        } catch (e: Exception) {
            throw IllegalAccessException("添加荣耀依赖: $e ${(e as? InvocationTargetException)?.targetException}")
        }
    }

    fun withYL(): AbsAuthBuildForYL {
        return try {
            val constructor = Class.forName("cn.moltres.android.auth.yl.AuthBuildForYL").getConstructor()
            constructor.newInstance() as AbsAuthBuildForYL
        } catch (e: Exception) {
            throw IllegalAccessException("添加银联依赖: $e ${(e as? InvocationTargetException)?.targetException}")
        }
    }
}