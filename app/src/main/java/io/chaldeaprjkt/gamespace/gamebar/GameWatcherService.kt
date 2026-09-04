/*
 * Copyright (C) 2021 Chaldeaprjkt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.chaldeaprjkt.gamespace.gamebar

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import io.chaldeaprjkt.gamespace.data.SystemSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Thay cho GameBroadcastReceiver: ROM không gửi broadcast GAME_START/STOP tới app
 * (cần quyền MANAGE_GAME_MODE mà app không còn có), nên tự polling app đang ở
 * foreground bằng UsageStatsManager - API công khai, chỉ cần quyền "Usage access".
 * Máy đã root nên tự cấp quyền đó bằng appops luôn, khỏi bắt người dùng vào Settings
 * bật tay. Phát hiện game trong danh sách mở/đóng thì gọi thẳng
 * SessionService.start()/stop() - y hệt luồng cũ, không đổi gì ở SessionService.
 */
@AndroidEntryPoint(Service::class)
class GameWatcherService : Hilt_GameWatcherService() {

    @Inject
    lateinit var settings: SystemSettings

    private val scope = CoroutineScope(Job() + Dispatchers.IO)
    private var activeGame: String? = null
    private var lastPollTime = System.currentTimeMillis()

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotification())
        // grantUsageAccessViaRoot() gọi "su" - lệnh blocking, có thể mất thời
        // gian không đoán trước được (lần đầu xin root, KernelSU/APatch có thể
        // phải chờ xác nhận). Trước đây gọi thẳng trên main thread trong
        // onCreate() khiến service bị treo main thread -> bị hệ thống coi là
        // crash. Giờ chạy hết trong coroutine trên Dispatchers.IO.
        scope.launch {
            grantUsageAccessViaRoot()
            pollLoop()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun pollLoop() {
        while (true) {
            try {
                checkForegroundApp()
            } catch (e: Throwable) {
                Log.w(TAG, "poll lỗi: ${e.message}")
            }
            delay(POLL_INTERVAL_MS)
        }
    }

    private fun checkForegroundApp() {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return
        val end = System.currentTimeMillis()
        val events = usm.queryEvents(lastPollTime, end)
        lastPollTime = end

        var newest: String? = null
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                newest = event.packageName
            }
        }

        if (newest == null || newest == activeGame) return

        val games = settings.userGames.map { it.packageName }
        when {
            games.contains(newest) -> {
                if (activeGame != null) SessionService.stop(this)
                activeGame = newest
                SessionService.start(this, newest)
            }
            activeGame != null -> {
                SessionService.stop(this)
                activeGame = null
            }
        }
    }

    private fun grantUsageAccessViaRoot() {
        try {
            ProcessBuilder("su", "-c", "appops set $packageName GET_USAGE_STATS allow")
                .redirectErrorStream(true)
                .start()
                .waitFor()
        } catch (e: Throwable) {
            Log.w(TAG, "Không tự cấp được usage access qua root: ${e.message}")
        }
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Game Booster", NotificationManager.IMPORTANCE_MIN)
                    .apply { setShowBadge(false) }
            )
        }
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Game Booster")
            .setContentText("Đang theo dõi game để tự bật chế độ hiệu năng")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "GameWatcherService"
        private const val CHANNEL_ID = "game_watcher"
        private const val NOTIF_ID = 1001
        private const val POLL_INTERVAL_MS = 1200L

        fun start(context: Context) {
            val intent = Intent(context, GameWatcherService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
