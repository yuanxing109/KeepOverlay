package com.example.keepoverlay

import android.view.WindowManager
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class MainHook : IXposedHookLoadPackage {

    companion object {
        private const val TAG = "KeepOverlay"
        private const val TARGET_PACKAGE = "cn.ulearning.yxy"

        private const val FLAG_SECURE = 0x00002000
        private const val PRIVATE_FLAG_HIDE = 0x00080000
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != TARGET_PACKAGE) return
        log("模块已挂载")

        // Hook Window.setAttributes
        hookMethod(
            android.view.Window::class.java,
            "setAttributes",
            WindowManager.LayoutParams::class.java
        )

        // Hook 所有 LayoutParams 构造器，防止在创建时直接填入标志
        try {
            XposedHelpers.hookAllConstructors(
                WindowManager.LayoutParams::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        stripFlags(param.thisObject)
                    }
                })
        } catch (e: Exception) {
            log("hookAllConstructors 失败: ${e.message}")
        }

        // Hook WindowManager.addView (拦截直接添加的窗口)
        try {
            XposedHelpers.findAndHookMethod(
                android.view.WindowManager::class.java,
                "addView",
                android.view.View::class.java,
                android.view.ViewGroup.LayoutParams::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (param.args.size >= 2) {
                            stripFlags(param.args[1])
                        }
                    }
                })
        } catch (e: Exception) {
            log("addView Hook 失败: ${e.message}")
        }

        // Hook Window.setFlags/addFlags/clearFlags 移除 FLAG_SECURE
        hookWindowFlagMethod("setFlags", Int::class.java, Int::class.java)
        hookWindowFlagMethod("addFlags", Int::class.java)
        hookWindowFlagMethod("clearFlags", Int::class.java)

        log("所有 Hook 安装完成")
    }

    private fun hookMethod(clazz: Class<*>, methodName: String, vararg paramTypes: Class<*>) {
        try {
            XposedHelpers.findAndHookMethod(clazz, methodName, *paramTypes, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (param.args.isNotEmpty()) {
                        stripFlags(param.args[0])
                    }
                }
            })
        } catch (e: Exception) {
            log("Hook $methodName 失败: ${e.message}")
        }
    }

    private fun hookWindowFlagMethod(methodName: String, vararg paramTypes: Class<*>) {
        try {
            XposedHelpers.findAndHookMethod(
                android.view.Window::class.java,
                methodName,
                *paramTypes,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val flagsIndex = if (methodName == "clearFlags") 0 else 0
                        if (param.args.isNotEmpty() && param.args[flagsIndex] is Int) {
                            var flags = param.args[flagsIndex] as Int
                            if (flags and FLAG_SECURE != 0) {
                                flags = flags and FLAG_SECURE.inv()
                                param.args[flagsIndex] = flags
                                log("[$methodName] 已移除 FLAG_SECURE")
                            }
                        }
                    }
                })
        } catch (e: Exception) {
            log("Hook Window.$methodName 失败: ${e.message}")
        }
    }

    /**
     * 通过反射清除 LayoutParams 对象中的 SECURE 和 HIDE_NON_SYSTEM_OVERLAY_WINDOWS 标志
     */
    private fun stripFlags(obj: Any?) {
        if (obj == null) return

        // 清除 privateFlags 中的 HIDE_NON_SYSTEM_OVERLAY_WINDOWS
        try {
            val privateFlagsField = obj.javaClass.getField("privateFlags")
            var privateFlags = privateFlagsField.getInt(obj)
            if (privateFlags and PRIVATE_FLAG_HIDE != 0) {
                privateFlags = privateFlags and PRIVATE_FLAG_HIDE.inv()
                privateFlagsField.setInt(obj, privateFlags)
                log("已清除 HIDE_NON_SYSTEM_OVERLAY_WINDOWS")
            }
        } catch (e: Exception) {
            // 该对象可能没有 privateFlags 字段
        }

        // 清除 flags 中的 SECURE
        try {
            val flagsField = obj.javaClass.getField("flags")
            var flags = flagsField.getInt(obj)
            if (flags and FLAG_SECURE != 0) {
                flags = flags and FLAG_SECURE.inv()
                flagsField.setInt(obj, flags)
                log("已清除 FLAG_SECURE")
            }
        } catch (e: Exception) {
            // 忽略
        }
    }

    private fun log(msg: String) {
        XposedBridge.log("$TAG: $msg")
        android.util.Log.d(TAG, msg)
    }
}
