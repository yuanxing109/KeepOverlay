# KeepOverlay (悬浮窗守护)

一个基于 **LSPosed** 的 Xposed 模块，用于阻止优学院（`cn.ulearning.yxy`）强制隐藏其他应用的悬浮窗。

## 问题背景

从 `dumpsys window windows` 分析可知，优学院的 `WebActivity` 设置了 `HIDE_NON_SYSTEM_OVERLAY_WINDOWS`（`0x00080000`）私有窗口标志：

```
Window{eb3bae9 u0 cn.ulearning.yxy/cn.ulearning.cordova.WebActivity}:
    pfl=... HIDE_NON_SYSTEM_OVERLAY_WINDOWS ...
```

此标志会强制隐藏所有非系统悬浮窗（翻译工具、笔记、计时器等），导致无法在学习过程中使用这些辅助工具。

## 解决方案

本模块通过 Hook 以下两个关键方法，在窗口属性设置时自动清除该标志：

- **`Window.setAttributes()`** — 拦截 Activity 设置窗口属性
- **`WindowManager.updateViewLayout()`** — 拦截窗口布局更新时的属性设置

## 使用前提

- Android 设备已 Root（推荐 Magisk / KernelSU / APatch）
- 已安装并激活 LSPosed 框架（API 82+）

## 安装与使用

1. 在 Android Studio 中打开本项目，点击 **Build → Build Bundle(s) / APK(s) → Build APK(s)**
2. 编译产物位于 `app/build/outputs/apk/debug/app-debug.apk`
3. 将 APK 传输到手机并安装
4. 打开 **LSPosed** 管理器 → **模块** → 勾选 **KeepOverlay**
5. 作用域勾选 **优学院**（`cn.ulearning.yxy`）
6. 强制停止优学院或重启手机
7. 打开优学院进入课程页面，确认悬浮窗保持可见

## 技术细节

| 属性 | 值 |
|------|-----|
| 包名 | `com.example.keepoverlay` |
| 目标应用 | `cn.ulearning.yxy` |
| 目标标志 | `PRIVATE_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS` = `0x00080000` |
| 最低 API | 27 |
| 编译 SDK | 34 |

## 免责声明

- 本模块仅供个人学习与 Android 逆向工程研究使用
- 请勿用于任何违规、违法或损害他人利益的场景
- 使用本模块所引发的任何后果，概由使用者自行承担
- 请于下载测试后 24 小时内自觉删除
