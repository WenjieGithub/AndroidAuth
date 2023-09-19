package cn.moltres.android.auth

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build

object AuthBuildForMore : AbsAuthBuild() {
    /**
     * 分享到更多
     * @param text 分享文本
     */
    fun shareToMore(text: String) {
        val shareIntent = Intent()
        shareIntent.action = Intent.ACTION_SEND
        shareIntent.putExtra(Intent.EXTRA_TEXT, text)
        shareIntent.type = "text/plain"
        val intent = Intent.createChooser(shareIntent, "Share To")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) //Intent.createChooser()方法会丢掉flags,这里加上
        Auth.application.startActivity(intent)
    }

    /**
     * 分享链接
     * "Twitter" -> "com.twitter.android"
     * "WhatsApp" -> "com.whatsapp"
     * "LinkedIn" -> "com.linkedin.android"
     * "Instagram" -> "com.instagram.android"
     * "Facebook" -> "com.facebook.katana"
     *
     * @param packageName 分享应用包名
     * @param url 分享链接 Url
     * @param title 分享显示标题
     */
    fun shareLink(packageName: String, url: String, title: String?) {
        var shareIntent = Intent()
        shareIntent.action = Intent.ACTION_SEND
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        shareIntent.type = "text/plain"
        shareIntent.putExtra(Intent.EXTRA_TEXT, "${title}${Auth.separatorLine}${url}")
        shareIntent.setPackage(packageName)
        if(Auth.isInstalled(packageName, shareIntent)){
            shareIntent = Intent.createChooser(shareIntent, "Share To")//需要使用Intent.createChooser，否则会出现应用选择框
            shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if(url.isEmpty()){
                resultError("url 不能为空")
            }else{
                try {
                    Auth.application.startActivity(shareIntent)
                } catch (e: ActivityNotFoundException) {
                    resultError("找不到包名对应的应用: $packageName   ${e.stackTraceToString()}")
                }
            }
        } else {
            resultUninstalled()
        }
    }

    /**
     * 分享图片
     * "Twitter" -> "com.twitter.android"
     * "WhatsApp" -> "com.whatsapp"
     * "LinkedIn" -> "com.linkedin.android"
     * "Instagram" -> "com.instagram.android"
     * "Facebook" -> "com.facebook.katana"
     *
     * @param packageName 分享应用包名
     * @param image 本地图片 uri
     * @param url 分享链接 Url
     * @param title 分享显示标题
     */
    fun shareImage(packageName: String, image: Uri, url: String?, title: String?) {
        var shareIntent = Intent()
        shareIntent.action = Intent.ACTION_SEND
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        shareIntent.putExtra(Intent.EXTRA_TEXT, "${title}${Auth.separatorLine}${url}")
        shareIntent.type = "image/*"
        shareIntent.putExtra(Intent.EXTRA_STREAM, image)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        shareIntent.setPackage(packageName)
        if(Auth.isInstalled(packageName, shareIntent)){
            shareIntent = Intent.createChooser(shareIntent, "Share To")
            shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                Auth.application.startActivity(shareIntent)
            } catch (e: ActivityNotFoundException) {
                resultError("找不到包名对应的应用: $packageName   ${e.stackTraceToString()}")
            }
        } else {
            resultUninstalled()
        }
    }
}