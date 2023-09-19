# Auth
-keep class cn.moltres.android.auth.xm.* { *; }
# 小米
-keepattributes InnerClasses,Signature,Exceptions,Deprecated,*Annotation*

-dontwarn android.**
-keep class android.** {*;}
-keep class com.google.** {*;}
-keep class com.android.** {*;}
-dontwarn org.apache.**
-keep class org.apache.** { *; }
-keep class sun.misc.Unsafe { *; }
-keep class com.google.zxing.** {*;}


#-libraryjars libs/alipaySdk.jar
-dontwarn com.alipay.**
-keep class com.alipay.** {*;}
-keep class com.ut.device.** {*;}
-keep class com.ta.utdid2.** {*;}

#-libraryjars libs/eventbus-3.jar
-keep class org.greenrobot.eventbus.** { *; }
-keep class de.greenrobot.event.** { *; }
-keep class de.greenrobot.dao.** {*;}

-keepclassmembers class ** {
    public void onEvent*(**);
    void onEvent*(**);
}

-keepclassmembers class ** {
    @org.greenrobot.eventbus.Subscribe <methods>;
}
-keep enum org.greenrobot.eventbus.ThreadMode { *; }
# Only required if you use AsyncExecutor
-keepclassmembers class * extends org.greenrobot.eventbus.util.ThrowableFailureEvent {
    <init>(java.lang.Throwable);
}
#-libraryjars libs/wechat.jar
-keep class com.tencent.** {*;}

#-libraryjars libs/glide.jar
-keep class com.bumptech.glide.** {*;}

-dontwarn com.xiaomi.**
-keep class com.xiaomi.** {*;}
-keep class com.mi.** {*;}
-keep class com.wali.** {*;}
-keep class cn.com.wali.** {*;}
-keep class miui.net.**{*;}
-keep class org.xiaomi.** {*;}
-keep class com.mi.*** {*;}
-keep class demo.csm.*** {*;}

#保留位于View类中的get和set方法
-keepclassmembers public class * extends android.view.View{
    void set*(***);
    *** get*();
}
#保留在Activity中以View为参数的方法不变
-keepclassmembers class * extends android.app.Activity{
    public void *(android.view.View);
}
#保留实现了Parcelable的类名不变，
-keep class * implements android.os.Parcelable{
    public static final android.os.Parcelable$Creator *;
}
#保留R$*类中静态成员的变量名
-keep class **.R$* {*;}

-dontwarn android.support.**
-keep class **.R$styleable{*;}
