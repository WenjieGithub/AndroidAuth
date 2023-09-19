package cn.moltres.android.auth.hw;

import android.content.Context;

import com.huawei.hms.jos.AppUpdateClient;
import com.huawei.updatesdk.service.otaupdate.CheckUpdateCallBack;

public class HWCheck {
    /**
     * 华为审核不过，添加这个文件的目的是让华为的审核能检测到 checkAppUpdate 方法的调用，
     * 如果调用方法放在 AuthBuildForHW 内，他们的工具就检测不到了, 需要是java文件
     */
    static void checkUp(AppUpdateClient client, Context context, CheckUpdateCallBack callBack) {
        client.checkAppUpdate(context, callBack);
    }
}
