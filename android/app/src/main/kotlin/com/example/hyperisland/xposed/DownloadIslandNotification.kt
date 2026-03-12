package com.example.hyperisland.xposed

import android.app.Notification
import android.graphics.drawable.Icon
import android.os.Bundle
import android.content.Context
import com.xzakota.hyper.notification.focus.FocusNotification
import de.robv.android.xposed.XposedBridge

/**
 * 下载灵动岛通知构建器
 * 使用 FocusNotification.buildV3 DSL 构建小米超级岛通知
 */
object DownloadIslandNotification {

    fun inject(
        context: Context,
        extras: Bundle,
        title: String,
        text: String,
        progress: Int,
        appName: String,
        fileName: String,
        downloadId: Long,
        packageName: String,
        isPaused: Boolean = false
    ) {
        try {
            val isComplete = progress >= 100
            val isMultiFile = Regex("""\d+个文件""").containsMatchIn(title + text + fileName)

            val displayTitle = when {
                isComplete -> title
                isPaused   -> if (isMultiFile) "$fileName 已暂停" else "已暂停 $progress%"
                else       -> "$fileName 下载中 $progress%"
            }
            val displayContent = when {
                isComplete -> "下载完成"
                isPaused   -> "已暂停"
                else       -> text.ifEmpty { fileName }
            }
            val islandStateTitle = when {
                isComplete -> "下载完成"
                isPaused   -> "已暂停"
                else       -> "下载中"
            }

            val downloadIconRes = if (isComplete) android.R.drawable.stat_sys_download_done
                else android.R.drawable.stat_sys_download
            val tintColor = when {
                isComplete -> 0xFF4CAF50.toInt()  // 绿
                isPaused   -> 0xFFFF9800.toInt()  // 橙
                else       -> 0xFF2196F3.toInt()  // 蓝
            }
            val downloadIcon = Icon.createWithResource(context, downloadIconRes).apply { setTint(tintColor) }

            // 主按钮：暂停中→恢复，下载中→暂停
            val primaryIntent = when {
                isPaused && isMultiFile -> InProcessController.resumeAllIntent(context)
                isPaused               -> InProcessController.resumeIntent(context, downloadId)
                isMultiFile            -> InProcessController.pauseAllIntent(context)
                else                   -> InProcessController.pauseIntent(context, downloadId)
            }
            val cancelPendingIntent = if (isMultiFile) InProcessController.cancelAllIntent(context)
                                      else             InProcessController.cancelIntent(context, downloadId)
            val primaryLabel = when {
                isPaused && isMultiFile -> "全部恢复"
                isPaused               -> "恢复"
                isMultiFile            -> "全部暂停"
                else                   -> "暂停"
            }
            val cancelLabel  = if (isMultiFile) "全部取消" else "取消"
            val primaryIconRes = if (isPaused) android.R.drawable.ic_media_play
                                 else          android.R.drawable.ic_media_pause

            val islandExtras = FocusNotification.buildV3 {
                val downloadIconKey = createPicture("key_download_icon", downloadIcon)

                islandFirstFloat = false
                enableFloat = false
                updatable = true

                // 小米岛 摘要态
                island {
                    islandProperty = 1
                    bigIslandArea {
                        imageTextInfoLeft {
                            type = 1
                            picInfo {
                                type = 1
                                pic = downloadIconKey
                            }
                            textInfo {
                                this.title = islandStateTitle
                            }
                        }
                        progressTextInfo {
                            textInfo {
                                this.title = fileName
                                narrowFont = true
                            }
                            if (!isComplete) {
                                progressInfo {
                                    this.progress = progress
                                }
                            }
                        }
                    }
                    smallIslandArea {
                        picInfo {
                            type = 1
                            pic = downloadIconKey
                        }
                    }
                }

                // 焦点通知 展开态
                iconTextInfo {
                    this.title = displayTitle
                    content = displayContent
                    animIconInfo {
                        type = 0
                        src = downloadIconKey
                    }
                }

                // 操作按钮（完成时不显示）
                if (!isComplete) {
                    textButton {
                        addActionInfo {
                            val primaryAction = Notification.Action.Builder(
                                Icon.createWithResource(context, primaryIconRes),
                                primaryLabel,
                                primaryIntent
                            ).build()
                            action = createAction("action_primary", primaryAction)
                            actionTitle = primaryLabel
                        }
                        addActionInfo {
                            val cancelAction = Notification.Action.Builder(
                                Icon.createWithResource(context, android.R.drawable.ic_delete),
                                cancelLabel,
                                cancelPendingIntent
                            ).build()
                            action = createAction("action_cancel", cancelAction)
                            actionTitle = cancelLabel
                        }
                    }
                }
            }

            extras.putAll(islandExtras)

            // AOD 息屏显示：合并进已有的 miui.focus.param
            val aodTitle = when {
                isComplete -> "下载完成"
                isPaused   -> "已暂停 $progress%"
                else       -> "下载中 $progress%"
            }
            val existingParam = extras.getString("miui.focus.param")
            if (existingParam != null) {
                try {
                    val json = org.json.JSONObject(existingParam)
                    val pv2 = json.optJSONObject("param_v2") ?: org.json.JSONObject()
                    pv2.put("aodTitle", aodTitle)
                    json.put("param_v2", pv2)
                    extras.putString("miui.focus.param", json.toString())
                } catch (_: Exception) {}
            }

            val stateTag = when { isComplete -> "done"; isPaused -> "paused"; else -> "${progress}%" }
            XposedBridge.log("HyperIsland: Island injected — $fileName ($stateTag)")

        } catch (e: Exception) {
            XposedBridge.log("HyperIsland: Island injection error: ${e.message}")
        }
    }

}
