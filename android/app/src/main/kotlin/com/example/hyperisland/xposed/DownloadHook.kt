package com.example.hyperisland.xposed

import android.app.Notification
import android.os.Bundle
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.lang.reflect.Field
import java.util.regex.Pattern

/**
 * Xposed Hook — 拦截下载通知并注入小米超级岛参数
 */
class DownloadHook : IXposedHookLoadPackage {

    companion object {
        private var extrasField: Field? = null

        private val processedNotifications = mutableMapOf<String, NotificationInfo>()
        private val downloadIdMap = mutableMapOf<Long, String>()

        data class NotificationInfo(
            var lastProgress: Int,
            var lastProcessTime: Long,
            var appName: String,
            var downloadId: Long = -1L
        )

        init {
            try {
                extrasField = Notification::class.java.getDeclaredField("extras")
                extrasField?.isAccessible = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            hookNotificationBuilder(lpparam)

            val nmClass = lpparam.classLoader.loadClass("android.app.NotificationManager")
            hookNotifyMethod(nmClass, lpparam, hasTag = true)
            hookNotifyMethod(nmClass, lpparam, hasTag = false)

            hookDownloadManagerService(lpparam)
        } catch (e: Throwable) {
            XposedBridge.log("HyperIsland: Error hooking: ${e.message}")
        }
    }

    // ─── Notification.Builder.build() Hook ───────────────────────────────────

    private fun hookNotificationBuilder(lpparam: XC_LoadPackage.LoadPackageParam) {
        val builderClasses = listOf(
            "android.app.Notification\$Builder",
            "android.app.Notification.Builder"
        )
        for (builderClassName in builderClasses) {
            try {
                val builderClass = lpparam.classLoader.loadClass(builderClassName)
                XposedHelpers.findAndHookMethod(builderClass, "build", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val notif = param.result as? Notification ?: return
                        val extras = extrasField?.get(notif) as? Bundle ?: return

                        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
                        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
                        if (!isDownloadNotification(title, text, extras)) return

                        val appName = lpparam.packageName.substringAfterLast(".").replaceFirstChar { it.uppercase() }
                        val fileName = extractFileName(title, text, extras)
                        val downloadId = extractDownloadId(extras)
                        val progress = extractProgress(title, text, extras)
                        val key = "${lpparam.packageName}_${System.currentTimeMillis()}_${notif.hashCode()}"
                        val now = System.currentTimeMillis()

                        val info = processedNotifications.getOrPut(key) {
                            NotificationInfo(progress, now, appName, downloadId)
                        }
                        if (info.lastProgress == progress && now - info.lastProcessTime < 1000) return
                        info.lastProgress = progress; info.lastProcessTime = now; info.appName = appName
                        if (downloadId > 0) { info.downloadId = downloadId; downloadIdMap[downloadId] = lpparam.packageName }
                        processedNotifications.entries.removeIf { now - it.value.lastProcessTime > 10000 }

                        XposedBridge.log("HyperIsland: [Builder] $appName | $fileName | $progress%")

                        val context = getContext(lpparam) ?: return
                        DownloadIslandNotification.inject(context, extras, title, text, progress, appName, fileName, downloadId, lpparam.packageName)
                        extras.putBoolean("hyperisland_processed", true)
                    }
                })
                XposedBridge.log("HyperIsland: Hooked $builderClassName.build()")
                break
            } catch (_: Throwable) {}
        }
    }

    // ─── NotificationManager.notify() Hook ───────────────────────────────────

    private fun hookNotifyMethod(nmClass: Class<*>, lpparam: XC_LoadPackage.LoadPackageParam, hasTag: Boolean) {
        try {
            val paramTypes = if (hasTag)
                arrayOf(String::class.java, Int::class.javaPrimitiveType, Notification::class.java)
            else
                arrayOf(Int::class.javaPrimitiveType, Notification::class.java)

            XposedHelpers.findAndHookMethod(nmClass, "notify", *paramTypes, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val tag = if (hasTag) param.args[0] as? String else null
                    val id = if (hasTag) param.args[1] as Int else param.args[0] as Int
                    val notif = (if (hasTag) param.args[2] else param.args[1]) as Notification
                    handleNotification(notif, lpparam, id, tag)
                }
            })
        } catch (e: Throwable) {
            XposedBridge.log("HyperIsland: notify hook failed: ${e.message}")
        }
    }

    private fun handleNotification(notif: Notification, lpparam: XC_LoadPackage.LoadPackageParam, id: Int, tag: String?) {
        try {
            val extras = extrasField?.get(notif) as? Bundle ?: return
            if (extras.getBoolean("hyperisland_processed", false)) return

            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
            if (!isDownloadNotification(title, text, extras)) return

            val appName = lpparam.packageName.substringAfterLast(".").replaceFirstChar { it.uppercase() }
            val fileName = extractFileName(title, text, extras)
            val downloadId = extractDownloadId(extras)
            val progress = extractProgress(title, text, extras)
            val key = "${lpparam.packageName}_${tag ?: "null"}_$id"
            val now = System.currentTimeMillis()

            val info = processedNotifications.getOrPut(key) { NotificationInfo(progress, now, appName, downloadId) }
            if (info.lastProgress == progress && now - info.lastProcessTime < 1000) return
            info.lastProgress = progress; info.lastProcessTime = now; info.appName = appName
            if (downloadId > 0) { info.downloadId = downloadId; downloadIdMap[downloadId] = lpparam.packageName }
            processedNotifications.entries.removeIf { now - it.value.lastProcessTime > 10000 }

            XposedBridge.log("HyperIsland: [Notify] $appName | $fileName | $progress%")

            val context = getContext(lpparam) ?: return
            DownloadIslandNotification.inject(context, extras, title, text, progress, appName, fileName, downloadId, lpparam.packageName)
            extras.putBoolean("hyperisland_processed", true)

        } catch (e: Throwable) {
            XposedBridge.log("HyperIsland: handleNotification error: ${e.message}")
        }
    }

    // ─── DownloadManager Hook ─────────────────────────────────────────────────

    private fun hookDownloadManagerService(lpparam: XC_LoadPackage.LoadPackageParam) {
        val candidates = listOf(
            "com.android.providers.downloads.DownloadProvider",
            "com.android.providers.downloads.DownloadThread",
            "com.android.providers.downloads.DownloadManager",
            "android.app.DownloadManager"
        )
        for (className in candidates) {
            try {
                val clazz = lpparam.classLoader.loadClass(className)
                for (method in clazz.declaredMethods) {
                    val name = method.name.lowercase()
                    when {
                        name.contains("pause") -> hookLogMethod(clazz, method.name, "Pause")
                        name.contains("resume") -> hookLogMethod(clazz, method.name, "Resume")
                        name.contains("cancel") || name.contains("remove") || name.contains("delete") ->
                            hookLogMethod(clazz, method.name, "Cancel")
                    }
                }
            } catch (_: ClassNotFoundException) {
            } catch (e: Throwable) {
                XposedBridge.log("HyperIsland: DownloadManager hook error ($className): ${e.message}")
            }
        }
    }

    private fun hookLogMethod(clazz: Class<*>, methodName: String, label: String) {
        try {
            XposedHelpers.findAndHookMethod(clazz, methodName, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    XposedBridge.log("HyperIsland: [$label] $methodName called")
                }
            })
        } catch (_: Throwable) {}
    }

    // ─── Utility ──────────────────────────────────────────────────────────────

    private fun getContext(lpparam: XC_LoadPackage.LoadPackageParam): android.content.Context? {
        return try {
            val activityThread = lpparam.classLoader.loadClass("android.app.ActivityThread")
            activityThread.getMethod("currentApplication").invoke(null) as? android.content.Context
        } catch (e: Exception) {
            try {
                val activityThread = lpparam.classLoader.loadClass("android.app.ActivityThread")
                (activityThread.getMethod("getSystemContext").invoke(null) as? android.content.Context)?.applicationContext
            } catch (_: Exception) { null }
        }
    }

    private fun isDownloadNotification(title: String, text: String, extras: Bundle): Boolean =
        title.contains("正在下载") ||
        title.contains("下载", ignoreCase = true) ||
        title.contains("download", ignoreCase = true) ||
        text.contains("下载", ignoreCase = true) ||
        extras.containsKey("progress")

    private fun extractProgress(title: String, text: String, extras: Bundle): Int {
        extras.getInt("progress", -1).takeIf { it >= 0 }?.let { return it }
        extras.getInt("android.progress", -1).takeIf { it >= 0 }?.let { return it }
        extras.getInt("percent", -1).takeIf { it >= 0 }?.let { return it }
        val m = Pattern.compile("(\\d+)%").matcher(title + text)
        if (m.find()) return m.group(1)?.toIntOrNull() ?: -1
        return -1
    }

    private fun extractDownloadId(extras: Bundle): Long {
        for (key in listOf("android.downloadId", "downloadId", "extra_download_id", "notification_id")) {
            val id = extras.getLong(key, -1L)
            if (id > 0) return id
        }
        val intId = extras.getInt("android.downloadId", -1)
        return if (intId > 0) intId.toLong() else -1L
    }

    private fun extractFileName(title: String, text: String, extras: Bundle): String {
        extractFileNameFromText(title).takeIf { it.isNotEmpty() }?.let { return it }
        extractFileNameFromText(text).takeIf { it.isNotEmpty() }?.let { return it }
        val extraText = extras.getString("android.title") ?: extras.getString("android.text")
        if (extraText != null) extractFileNameFromText(extraText).takeIf { it.isNotEmpty() }?.let { return it }
        return "下载文件"
    }

    private fun extractFileNameFromText(text: String): String {
        if (text.isEmpty()) return ""
        var s = text
        for (prefix in listOf("正在下载", "下载中", "下载", "Downloading", "Download")) {
            if (s.startsWith(prefix)) { s = s.substring(prefix.length).trim(); break }
        }
        for (suffix in listOf("下载中...", "下载中", "下载...", "下载", "Downloading", "Download")) {
            if (s.endsWith(suffix)) { s = s.substring(0, s.length - suffix.length).trim(); break }
        }
        val m = Pattern.compile("([\\u4e00-\\u9fa5\\w\\s\\-_.]+(?:\\.[a-zA-Z0-9]{2,5})?)", Pattern.CASE_INSENSITIVE).matcher(s)
        if (m.find()) {
            val name = m.group(1)?.trim() ?: ""
            return if (name.length > 30) name.substring(0, 27) + "..." else name
        }
        return if (s.length > 30) s.substring(0, 27) + "..." else s
    }
}
