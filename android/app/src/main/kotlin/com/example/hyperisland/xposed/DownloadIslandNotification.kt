package com.example.hyperisland.xposed

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Bundle
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
        packageName: String
    ) {
        try {
            val isComplete = progress >= 100
            val displayTitle = if (progress in 0..99) "下载中 $progress%" else title
            val displayContent = if (isComplete) "下载完成" else text.ifEmpty { fileName }

            val downloadIconRes = if (isComplete) android.R.drawable.stat_sys_download_done
                else android.R.drawable.stat_sys_download
            val downloadIcon = Icon.createWithResource(context, downloadIconRes)

            val pausePendingIntent = createControlIntent(context, DownloadControlReceiver.ACTION_PAUSE, downloadId, fileName, packageName, 1001)
            val cancelPendingIntent = createControlIntent(context, DownloadControlReceiver.ACTION_CANCEL, downloadId, fileName, packageName, 1002)

            val islandExtras = FocusNotification.buildV3 {
                val downloadIconKey = createPicture("key_download_icon", downloadIcon)

                islandFirstFloat = false
                enableFloat = false
                updatable = true
                ticker = displayTitle
                tickerPic = downloadIconKey

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
                        }
                        imageTextInfoRight {
                            type = 3
                            textInfo {
                                this.title = if (progress >= 0) "$fileName $progress%" else fileName
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

                picInfo {
                    type = 1
                    pic = downloadIconKey
                }

                // 操作按钮
                textButton {
                    if (!isComplete) {
                        addActionInfo {
                            val pauseAction = Notification.Action.Builder(
                                Icon.createWithResource(context, android.R.drawable.ic_media_pause),
                                "暂停",
                                pausePendingIntent
                            ).build()
                            action = createAction("action_pause", pauseAction)
                            actionTitle = "暂停"
                        }
                    }
                    addActionInfo {
                        val cancelAction = Notification.Action.Builder(
                            Icon.createWithResource(context, android.R.drawable.ic_delete),
                            if (isComplete) "完成" else "取消",
                            cancelPendingIntent
                        ).build()
                        action = createAction("action_cancel", cancelAction)
                        actionTitle = if (isComplete) "完成" else "取消"
                        if (isComplete) {
                            actionBgColor = "#006EFF"
                            actionBgColorDark = "#006EFF"
                            actionTitleColor = "#FFFFFF"
                            actionTitleColorDark = "#FFFFFF"
                        }
                    }
                }
            }

            extras.putAll(islandExtras)

            XposedBridge.log("HyperIsland: Island injected — $fileName ($progress%)")

        } catch (e: Exception) {
            XposedBridge.log("HyperIsland: Island injection error: ${e.message}")
        }
    }

    private fun createControlIntent(
        context: Context,
        action: String,
        downloadId: Long,
        fileName: String,
        packageName: String,
        requestCode: Int
    ): PendingIntent {
        val intent = Intent(DownloadControlReceiver.ACTION_CONTROL).apply {
            putExtra(DownloadControlReceiver.EXTRA_ACTION, action)
            putExtra(DownloadControlReceiver.EXTRA_DOWNLOAD_ID, downloadId)
            putExtra(DownloadControlReceiver.EXTRA_FILE_NAME, fileName)
            putExtra(DownloadControlReceiver.EXTRA_PACKAGE_NAME, packageName)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
