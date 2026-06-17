package com.example.keepoverlay

import android.os.Build
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * KeepOverlay 主 Hook 入口
 *
 * 目标：移除优学院 (cn.ulearning.yxy) 页面中的
 * HIDE_NON_SYSTEM_OVERLAY_WINDOWS 私有标志，使悬浮窗不被强制隐藏。
 *
 * 工作原理：
 * WindowManager.LayoutParams.privateFlags 的 0x00080000 位
 * 对应 PRIVATE_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS。
 * privateFlags 为 @hide 字段，通过 XposedHelpers 反射读写。
 */
class MainHook : IXposedHookLoadPackage {

    companion object {
        private const val TAG = "KeepOverlay"
        private const val TARGET_PACKAGE = "cn.ulearning.yxy"

        /**
         * PRIVATE_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS = 0x00080000
         * 该标志让窗口隐藏所有非系统悬浮窗
         */
        private const val PRIVATE_FLAG_HIDE = 0x00080000
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != TARGET_PACKAGE) return

        log("已挂载到 $TARGET_PACKAGE")

        // Hook Window.setAttributes(LayoutParams)
        // 当 Activity 通过 Window.setAttributes() 设置窗口属性时拦截
        XposedHelpers.findAndHookMethod(
            android.view.Window::class.java,
            "setAttributes",
            android.view.WindowManager.LayoutParams::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val attrs = param.args[0]
                    stripHideFlag(attrs, "Window.setAttributes")
                }
            }
        )

        log("所有 Hook 安装完成")
    }

    /**
     * 通过反射读取并清除 LayoutParams 中的 HIDE_NON_SYSTEM_OVERLAY_WINDOWS 私有标志
     */
    private fun stripHideFlag(attrs: Any?, source: String) {
        if (attrs == null) return
        try {
            val flags = XposedHelpers.getIntField(attrs, "privateFlags")
            if (flags and PRIVATE_FLAG_HIDE != 0) {
                XposedHelpers.setIntField(attrs, "privateFlags", flags and PRIVATE_FLAG_HIDE.inv())
                log("[$source] 已清除 HIDE_NON_SYSTEM_OVERLAY_WINDOWS 标志")
            }
        } catch (e: Exception) {
            log("[$source] 操作 privateFlags 失败: ${e.message}")
        }
    }

    private fun log(msg: String) {
        XposedBridge.log("$TAG: $msg")
        android.util.Log.d(TAG, msg)
    }
}
