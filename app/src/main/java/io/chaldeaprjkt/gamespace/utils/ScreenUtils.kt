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
package io.chaldeaprjkt.gamespace.utils

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.view.WindowManager
import com.android.internal.util.ScreenshotHelper
import com.android.systemui.screenrecord.IRemoteRecording
import javax.inject.Inject
import kotlin.system.exitProcess

/**
 * utilities for interacting with system screenshot and recorder service
 */
class ScreenUtils @Inject constructor(private val context: Context) {

    private var isRecorderBound = false
    private var remoteRecording: IRemoteRecording? = null
    private var wakelock: PowerManager.WakeLock? = null
    private val recorderConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            try {
                remoteRecording = IRemoteRecording.Stub.asInterface(service)
            } catch (e: Throwable) {
                e.printStackTrace()
                exitProcess(1)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            remoteRecording = null
        }
    }

    val recorder: IRemoteRecording? get() = remoteRecording

    fun bind() {
        // RecordingService là dịch vụ nội bộ của SystemUI, cần quyền
        // com.android.systemui.permission.SCREEN_RECORDING (signature) mới bind
        // được - không có system UID/chữ ký khớp thì bind sẽ fail. Trước đây fail
        // là gọi exitProcess(1), tự giết cả app mỗi lần vào game. Giờ chỉ tắt tính
        // năng FPS counter, không crash phần còn lại.
        isRecorderBound = try {
            context.bindService(Intent().apply {
                component = ComponentName(
                    "com.android.systemui",
                    "com.android.systemui.screenrecord.RecordingService"
                )
            }, recorderConnection, Context.BIND_AUTO_CREATE)
        } catch (e: Throwable) {
            Log.w(TAG, "Không bind được RecordingService (thiếu quyền hệ thống?): ${e.message}")
            false
        }
        @Suppress("DEPRECATION") // we use it for stay-awake feature
        wakelock = (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.FULL_WAKE_LOCK, "GameSpace:ScreenUtils")
    }

    fun unbind() {
        wakelock?.takeIf { it.isHeld }?.release()
        if (isRecorderBound) {
            context.unbindService(recorderConnection)
        }
        remoteRecording = null
    }

    fun takeScreenshot(onComplete: ((Uri?) -> Unit)? = null) {
        val handler = Handler(Looper.getMainLooper())
        ScreenshotHelper(context).takeScreenshot(
            WindowManager.TAKE_SCREENSHOT_FULLSCREEN,
            WindowManager.ScreenshotSource.SCREENSHOT_GLOBAL_ACTIONS, handler
        ) { handler.post { onComplete?.invoke(it) } }
    }

    var stayAwake = false
        get() = wakelock?.isHeld ?: false
        @SuppressLint("WakelockTimeout")
        set(enable) {
            field = enable
            if (enable) {
                wakelock?.takeIf { !it.isHeld }?.acquire()
            } else {
                wakelock?.takeIf { it.isHeld }?.release()
            }
        }

    companion object {
        private const val TAG = "ScreenUtils"
    }
}
