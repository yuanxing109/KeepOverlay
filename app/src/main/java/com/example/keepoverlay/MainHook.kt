package com.example.keepoverlay

import android.os.Build
import android.view.WindowManager
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * KeepOverlay 主 Hook 入口
 *
 * 目标：移除优学院 (cn.ulearning.yxy) WebActivity 等页面中的
 * HIDE_NON_SYSTEM_OVERLAY_WINDOWS 私有标志，使悬浮窗不被强制隐藏。
 *
 * 工作原理：
 * WindowManager.LayoutParams.privateFlags 的 0x00080000 位
 * 对应 PRIVATE_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS。
 * 我们在 Window.setAttributes() 被调用时检查并清除该标志，
 * 同时在 WindowManager.updateViewLayout() 处也做拦截作为补充。
 */
class MainHook : IXposedHookLoadPackage {

    companion object {
        private const val TAG = "KeepOverlay"
        private const val TARGET_PACKAGE = "cn.ulearning.yxy"

        /**
         * PRIVATE_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS = 0x00080000
         * 该标志让窗口隐藏所有非系统悬浮窗（Toast、权限悬浮窗等）
         */
        private const val PRIVATE_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS = 0x00080000
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != TARGET_PACKAGE) return

        log("已挂载到 $TARGET_PACKAGE (pid=${android.os.Process.myPid()})")

        // ========== 方式一：Hook Window.setAttributes ==========
        // 当 Activity 通过 Window.setAttributes() 设置 LayoutParams 时拦截
        try {
            XposedBridge.hookAllMethods(
                android.view.Window::class.java,
                "setAttributes",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val attrs = param.args[0] as? WindowManager.LayoutParams ?: return
                        stripOverlayHideFlag(attrs, "Window.setAttributes")
                    }
                }
            )
        } catch (e: Exception) {
            log("Hook Window.setAttributes 失败: ${e.message}")
        }

        // ========== 方式二：Hook WindowManager.LayoutParams 的 privateFlags 字段写入 ==========
        // 某些场景下 app 通过直接修改 LayoutParams.privateFlags 然后再 setParams 方式设置，
        // 方式一已经能覆盖。再增加对 updateViewLayout 的防御。
        try {
            val wmClass = if (Build.VERSION.SDK_INT >= 23) {
                android.view.WindowManagerImpl::class.java
                    ?: XposedHelpers.findClass("android.view.WindowManagerImpl", lpparam.classLoader)
            } else {
                XposedHelpers.findClass("android.view.WindowManagerImpl", lpparam.classLoader)
            }

            if (wmClass != null) {
                XposedBridge.hookAllMethods(
                    wmClass,
                    "updateViewLayout",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val view = param.args[0] // View
                            val attrs = param.args[1] as? WindowManager.LayoutParams ?: return
                            stripOverlayHideFlag(attrs, "WindowManager.updateViewLayout")
                        }
                    }
                )
            }
        } catch (e: Exception) {
            log("Hook updateViewLayout 失败: ${e.message}")
        }

        log("所有 Hook 安装完成")
    }

    /**
     * 检查并清除 LayoutParams 中的 HIDE_NON_SYSTEM_OVERLAY_WINDOWS 私有标志
     */
    private fun stripOverlayHideFlag(attrs: WindowManager.LayoutParams, source: String) {
        if (attrs.privateFlags and PRIVATE_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS != 0) {
            attrs.privateFlags = attrs.privateFlags and PRIVATE_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS.inv()
            log("[$source] 已清除 HIDE_NON_SYSTEM_OVERLAY_WINDOWS 标志 (privateFlags=${
                Integer.toHexString(attrs.privateFlags)
            })")
        }
    }

    private fun log(msg: String) {
        val full = "$TAG: $msg"
        XposedBridge.log(full)
        android.util.Log.d(TAG, msg)
    }
}
