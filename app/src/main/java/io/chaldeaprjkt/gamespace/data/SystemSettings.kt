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
package io.chaldeaprjkt.gamespace.data

import android.content.Context
import android.provider.Settings
import android.util.Log
import io.chaldeaprjkt.gamespace.utils.GameModeUtils
import javax.inject.Inject

class SystemSettings @Inject constructor(
    context: Context,
    private val gameModeUtils: GameModeUtils
) {

    private val resolver = context.contentResolver

    // SharedPreferences cho các key vốn cần framework đã vá riêng hoặc quyền hệ
    // thống mới đọc/ghi được (GAMESPACE_GAME_LIST, GAMESPACE_SUPPRESS_FULLSCREEN_
    // INTENT). Hoạt động trên mọi ROM, không cần quyền gì cả.
    private val prefs = context.getSharedPreferences("gamespace_prefs", Context.MODE_PRIVATE)

    var headsUp
        get() =
            Settings.Global.getInt(resolver, Settings.Global.HEADS_UP_NOTIFICATIONS_ENABLED, 1) == 1
        set(it) {
            Settings.Global.putInt(
                resolver,
                Settings.Global.HEADS_UP_NOTIFICATIONS_ENABLED,
                it.toInt()
            )
        }

    // Bỏ *ForUser(..., UserHandle.USER_CURRENT): biến thể "ForUser" cần quyền
    // INTERACT_ACROSS_USERS_FULL để hệ thống resolve user -2, ngay cả khi chạy
    // cho đúng user hiện tại. Dùng bản thường (getInt/putInt) - áp dụng ngầm cho
    // user gọi, không cần quyền gì. Bọc try/catch phòng khi ghi key hệ thống
    // (SCREEN_BRIGHTNESS_MODE) bị chặn do thiếu WRITE_SETTINGS.
    var autoBrightness
        get() = try {
            Settings.System.getInt(
                resolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
            ) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        } catch (e: Throwable) {
            Log.w(TAG, "autoBrightness bị từ chối: ${e.message}")
            false
        }
        set(auto) {
            try {
                Settings.System.putInt(
                    resolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    if (auto) Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                    else Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                )
            } catch (e: Throwable) {
                Log.w(TAG, "autoBrightness (ghi) bị từ chối: ${e.message}")
            }
        }

    var threeScreenshot
        get() = try {
            Settings.System.getInt(resolver, Settings.System.SWIPE_TO_SCREENSHOT, 0) == 1
        } catch (e: Throwable) {
            Log.w(TAG, "threeScreenshot bị từ chối: ${e.message}")
            false
        }
        set(it) {
            try {
                Settings.System.putInt(resolver, Settings.System.SWIPE_TO_SCREENSHOT, it.toInt())
            } catch (e: Throwable) {
                Log.w(TAG, "threeScreenshot (ghi) bị từ chối: ${e.message}")
            }
        }

    var suppressFullscreenIntent
        get() = prefs.getBoolean(KEY_SUPPRESS_FULLSCREEN_INTENT, false)
        set(it) {
            prefs.edit().putBoolean(KEY_SUPPRESS_FULLSCREEN_INTENT, it).apply()
        }

    var userGames
        get() =
            prefs.getString(KEY_USER_GAMES, null)
                ?.split(";")
                ?.toList()?.filter { it.isNotEmpty() }
                ?.map { UserGame.fromSettings(it) } ?: emptyList()
        set(games) {
            prefs.edit()
                .putString(
                    KEY_USER_GAMES,
                    if (games.isEmpty()) "" else games.joinToString(";") { it.toString() }
                )
                .apply()
            gameModeUtils.setupBatteryMode(games.isNotEmpty())
        }

    private fun Boolean.toInt() = if (this) 1 else 0

    companion object {
        private const val TAG = "SystemSettings"
        private const val KEY_USER_GAMES = "user_games"
        private const val KEY_SUPPRESS_FULLSCREEN_INTENT = "suppress_fullscreen_intent"
    }
}
