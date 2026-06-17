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

        // 1. Hook Window.setAttributes (已有)
        hookMethod(android.view.Window::class.java, "setAttributes", WindowManager.LayoutParams::class.java)

        // 2. Hook WindowManager.LayoutParams 的所有构造函数
        //    很多 App 直接 new LayoutParams() 并填入标志
        hookAllConstructors(WindowManager.LayoutParams::class.java)

        // 3. Hook WindowManager.addView (三个参数版本)
        //    有些 App 不调 setAttributes，而是直接 addView 传递 LayoutParams
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

        // 4. Hook Window.setFlags / addFlags / clearFlags (预防 FLAG_SECURE)
        hookWindowFlagMethod("setFlags", Int::class.java, Int::class.java)
        hookWindowFlagMethod("addFlags", Int::class.java)
        hookWindowFlagMethod("clearFlags", Int::class.java)

        log("Hook 安装完成")
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

    private fun hookAllConstructors(clazz: Class<*>) {
        try {
            XposedBridge.hookAllConstructors(clazz, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    stripFlags(param.thisObject)
                }
            })
        } catch (e: Exception) {
            log("Hook 构造器失败: ${e.message}")
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
     * 清除 LayoutParams 对象中的 SECURE 和 HIDE_NON_SYSTEM_OVERLAY_WINDOWS 标志
     */
    private fun stripFlags(obj: Any?) {
        if (obj == null) return
        try {
            // 检查并清除 privateFlags
            if (obj is WindowManager.LayoutParams) {
                if (obj.privateFlags and PRIVATE_FLAG_HIDE != 0) {
                    obj.privateFlags = obj.privateFlags and PRIVATE_FLAG_HIDE.inv()
                    log("已清除 HIDE_NON_SYSTEM_OVERLAY_WINDOWS")
                }
            } else {
                // 对于 ViewGroup.LayoutParams 非子类，尝试反射 privateFlags
                val flagsField = obj.javaClass.getField("privateFlags")
                if (flagsField != null) {
                    var flags = flagsField.getInt(obj)
                    if (flags and PRIVATE_FLAG_HIDE != 0) {
                        flags = flags and PRIVATE_FLAG_HIDE.inv()
                        flagsField.setInt(obj, flags)
                        log("已清除 privateFlags")
                    }
                }
            }
        } catch (_: Exception) {}

        try {
            // 清除 flags 中的 SECURE
            val flagsField = obj.javaClass.getField("flags")
            if (flagsField != null) {
                var flags = flagsField.getInt(obj)
                if (flags and FLAG_SECURE != 0) {
                    flags = flags and FLAG_SECURE.inv()
                    flagsField.setInt(obj, flags)
                    log("已清除 FLAG_SECURE")
                }
            }
        } catch (_: Exception) {}
    }

    private fun log(msg: String) {
        XposedBridge.log("$TAG: $msg")
        android.util.Log.d(TAG, msg)
    }
}
