package dev.shiyi.kuaishoufilter.hook

import android.app.Application
import android.content.Context
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.util.concurrent.atomic.AtomicBoolean

class HookEntry : IXposedHookLoadPackage {
    override fun handleLoadPackage(param: XC_LoadPackage.LoadPackageParam) {
        if (param.packageName != TargetProfile.PACKAGE || param.processName != TargetProfile.PACKAGE) return
        val initialized = AtomicBoolean()
        XposedHelpers.findAndHookMethod(Application::class.java, "attach", Context::class.java, object : XC_MethodHook() {
            override fun afterHookedMethod(call: MethodHookParam) {
                if (!initialized.compareAndSet(false, true)) return
                try {
                    HostRuntime(call.args[0] as Context).start(param.classLoader)
                } catch (error: Throwable) {
                    XposedBridge.log("KuaishouFilter: bootstrap ${error.javaClass.simpleName}")
                }
            }
        })
    }
}
