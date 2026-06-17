package com.example.keepoverlay

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class MainHook : IXposedHookLoadPackage {

    companion object {
        private const val TAG = "KeepOverlay"
        private const val TARGET_PACKAGE = "cn.ulearning.yxy"
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != TARGET_PACKAGE) return
        log("模块已挂载")

        // Hook setHideOverlayWindows(boolean)
        try {
            XposedHelpers.findAndHookMethod(
                android.view.Window::class.java,
                "setHideOverlayWindows",
                Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (param.args[0] == true) {
                            param.args[0] = false
                            log("已拦截 setHideOverlayWindows(true)，改为 false")
                        }
                    }
                })
        } catch (e: Exception) {
            log("Hook 失败: ${e.message}")
        }

        log("Hook 安装完成")
    }

    private fun log(msg: String) {
        XposedBridge.log("$TAG: $msg")
        android.util.Log.d(TAG, msg)
    }
}
