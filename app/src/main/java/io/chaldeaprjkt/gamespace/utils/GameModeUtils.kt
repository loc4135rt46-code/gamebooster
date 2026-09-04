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

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.IDeviceIdleController
import android.os.ServiceManager
import android.provider.DeviceConfig
import android.util.Log
import io.chaldeaprjkt.gamespace.R
import io.chaldeaprjkt.gamespace.data.GameConfig
import io.chaldeaprjkt.gamespace.data.GameConfig.Companion.asConfig
import io.chaldeaprjkt.gamespace.data.SystemSettings
import io.chaldeaprjkt.gamespace.data.UserGame
import javax.inject.Inject

class GameModeUtils @Inject constructor(private val context: Context) {

    // Bộ điều khiển hiệu năng CPU chạy quyền root (su -c perfmtk <mode>).
    // Thay thế hoàn toàn cho android.app.GameManager#setGameMode() trước đây.
    // Không phụ thuộc quyền hệ thống nào - chỉ cần root, nên vẫn chạy được dù
    // app không còn sharedUserId=android.uid.system.
    private val perfmtk = PerfmtkController()
    var activeGame: UserGame? = null

    // Tính năng tuỳ chọn (downscale độ phân giải / ANGLE renderer qua DeviceConfig).
    // Cần READ/WRITE_DEVICE_CONFIG (signature|privileged) - không có system UID thì
    // sẽ ném SecurityException, nên bọc try/catch để chỉ tính năng này im lặng bỏ
    // qua, không kéo sập cả app.
    fun setIntervention(packageName: String, modeData: List<GameConfig>? = null) {
        try {
            DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_GAME_OVERLAY, packageName, modeData?.asConfig(), false
            )
        } catch (e: Throwable) {
            Log.w(TAG, "setIntervention bị từ chối (thiếu quyền hệ thống?): ${e.message}")
        }
    }

    /**
     * Áp dụng chế độ hiệu năng cho game đang active: lưu lựa chọn của người dùng rồi
     * gửi lệnh xuống perfmtk (root) để thực sự đổi CPU governor trên thiết bị.
     * [onApplied] được gọi lại trên main thread khi perfmtk phản hồi (hoặc hết thời
     * gian chờ / lỗi), để UI (vd. GameModeTile) cập nhật lại trạng thái "đang áp dụng".
     */
    fun setActiveGameMode(
        systemSettings: SystemSettings,
        mode: Int,
        onApplied: ((PerfmtkController.Result) -> Unit)? = null
    ) {
        val packageName = activeGame?.packageName ?: return
        activeGame = setGameModeFor(packageName, systemSettings, mode)
        perfmtk.apply(mode, onApplied)
    }

    fun setGameModeFor(packageName: String, systemSettings: SystemSettings, mode: Int): UserGame {
        val data = UserGame(packageName, mode)
        systemSettings.userGames = systemSettings.userGames
            .filter { x -> x.packageName != packageName }
            .toMutableList()
            .apply { add(data) }

        return data
    }

    fun setupBatteryMode(enable: Boolean) {
        val svc = IDeviceIdleController.Stub.asInterface(
            ServiceManager.getService(Context.DEVICE_IDLE_CONTROLLER)
        )
        try {
            val isListed = svc?.isPowerSaveWhitelistApp(context.packageName) ?: false
            if (enable && !isListed) {
                svc?.addPowerSaveWhitelistApp(context.packageName)
            } else if (!enable && isListed) {
                svc?.removePowerSaveWhitelistApp(context.packageName)
            }
        } catch (e: Throwable) {
            // RemoteException (service lạ) hoặc SecurityException (thiếu quyền
            // hệ thống) - không có quyền whitelist pin thì bỏ qua, không crash.
            Log.w(TAG, "setupBatteryMode bị từ chối: ${e.message}")
        }
    }


    fun findAnglePackage(): ActivityInfo? = try {
        val intent = Intent(ACTION_ANGLE_FOR_ANDROID)
        val flags = PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_SYSTEM_ONLY.toLong())
        val info = context.packageManager.queryIntentActivities(intent, flags)
        info.firstOrNull()?.activityInfo
    } catch (e: Throwable) {
        Log.w(TAG, "findAnglePackage bị từ chối: ${e.message}")
        null
    }

    fun isAngleUsed(packageName: String?) = packageName?.let {
        try {
            DeviceConfig.getString(DeviceConfig.NAMESPACE_GAME_OVERLAY, it, null)
                ?.contains("useAngle=true")
        } catch (e: Throwable) {
            Log.w(TAG, "isAngleUsed bị từ chối (thiếu quyền hệ thống?): ${e.message}")
            false
        }
    } ?: false

    companion object {
        private const val TAG = "GameModeUtils"
        const val defaultPreferredMode = PerfmtkController.DEFAULT_MODE
        const val ACTION_ANGLE_FOR_ANDROID = "android.app.action.ANGLE_FOR_ANDROID"

        fun Context.describeGameMode(mode: Int) =
            resources.getStringArray(R.array.game_mode_names).getOrNull(mode) ?: "Unsupported"
    }
}
