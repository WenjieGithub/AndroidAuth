package cn.moltres.android.auth.zfb

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class AuthActivityForZFB : AppCompatActivity() {
    companion object {
        internal var authBuildForZFB: AuthBuildForZFB? = null
        internal var callbackActivity: ((activityForZFB: AuthActivityForZFB) -> Unit)? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (authBuildForZFB == null) {
            callbackActivity?.invoke(this) ?: finish()
            callbackActivity = null
        } else {
            authBuildForZFB?.onTreatyPayResult(intent)
            authBuildForZFB = null
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
