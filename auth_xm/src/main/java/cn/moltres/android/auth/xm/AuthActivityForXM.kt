package cn.moltres.android.auth.xm

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class AuthActivityForXM : AppCompatActivity() {
    companion object {
        internal var authBuildForXM: AuthBuildForXM? = null
        internal var callbackActivity: ((activityForXM: AuthActivityForXM) -> Unit)? = null
        internal var callbackActivityResult: ((requestCode: Int, resultCode: Int, data: Intent?) -> Unit)? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        callbackActivity?.invoke(this) ?: finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        callbackActivityResult?.invoke(requestCode, resultCode, data) ?: finish()
    }

    override fun onDestroy() {
        authBuildForXM = null
        callbackActivity = null
        callbackActivityResult = null
        super.onDestroy()
    }
}
