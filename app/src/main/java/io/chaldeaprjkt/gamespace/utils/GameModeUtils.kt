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
import android.os.RemoteException
import android.os.ServiceManager
import android.provider.DeviceConfig
import io.chaldeaprjkt.gamespace.R
import io.chaldeaprjkt.gamespace.data.GameConfig
import io.chaldeaprjkt.gamespace.data.GameConfig.Companion.asConfig
import io.chaldeaprjkt.gamespace.data.SystemSettings
import io.chaldeaprjkt.gamespace.data.UserGame
import javax.inject.Inject

class GameModeUtils @Inject constructor(private val context: Context) {

    // Bộ điều khiển hiệu năng CPU chạy quyền root (su -c perfmtk <mode>).
    // Thay thế hoàn toàn cho android.app.GameManager#setGameMode() trước đây.
    private val perfmtk = PerfmtkController()
    var activeGame: UserGame? = null

    fun setIntervention(packageName: String, modeData: List<GameConfig>? = null) {
        DeviceConfig.setProperty(
            DeviceConfig.NAMESPACE_GAME_OVERLAY, packageName, modeData?.asConfig(), false
        )
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
        } catch (e: RemoteException) {
            e.printStackTrace()
        }
    }


    fun findAnglePackage(): ActivityInfo? {
        val intent = Intent(ACTION_ANGLE_FOR_ANDROID)
        val flags = PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_SYSTEM_ONLY.toLong())
        val info = context.packageManager.queryIntentActivities(intent, flags)
        return info.firstOrNull()?.activityInfo
    }

    fun isAngleUsed(packageName: String?) = packageName?.let {
        DeviceConfig.getString(DeviceConfig.NAMESPACE_GAME_OVERLAY, it, null)
            ?.contains("useAngle=true")
    } ?: false

    companion object {
        const val defaultPreferredMode = PerfmtkController.DEFAULT_MODE
        const val ACTION_ANGLE_FOR_ANDROID = "android.app.action.ANGLE_FOR_ANDROID"

        fun Context.describeGameMode(mode: Int) =
            resources.getStringArray(R.array.game_mode_names).getOrNull(mode) ?: "Unsupported"
    }
}
