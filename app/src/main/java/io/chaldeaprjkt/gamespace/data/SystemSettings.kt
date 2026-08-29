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
import android.os.UserHandle
import android.provider.Settings
import io.chaldeaprjkt.gamespace.utils.GameModeUtils
import javax.inject.Inject

class SystemSettings @Inject constructor(
    context: Context,
    private val gameModeUtils: GameModeUtils
) {

    private val resolver = context.contentResolver

    // SharedPreferences thay cho Settings.System.GAMESPACE_GAME_LIST: key đó chỉ
    // tồn tại trên framework đã được vá riêng cho GameSpace. Không có system UID
    // + framework vá đúng bản thì Settings.System có thể không lưu được (bị chặn
    // hoặc âm thầm không ghi). SharedPreferences hoạt động trên mọi ROM, không
    // cần quyền gì cả.
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

    var autoBrightness
        get() =
            Settings.System.getIntForUser(
                resolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC,
                UserHandle.USER_CURRENT
            ) ==
                    Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        set(auto) {
            Settings.System.putIntForUser(
                resolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                if (auto) Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                else Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
                UserHandle.USER_CURRENT
            )
        }

    var threeScreenshot
        get() = Settings.System.getIntForUser(
            resolver, Settings.System.SWIPE_TO_SCREENSHOT, 0,
            UserHandle.USER_CURRENT
        ) == 1
        set(it) {
            Settings.System.putIntForUser(
                resolver, Settings.System.SWIPE_TO_SCREENSHOT,
                it.toInt(), UserHandle.USER_CURRENT
            )
        }

    var suppressFullscreenIntent
        get() = Settings.System.getIntForUser(
            resolver,
            Settings.System.GAMESPACE_SUPPRESS_FULLSCREEN_INTENT,
            0,
            UserHandle.USER_CURRENT
        ) == 1
        set(it) {
            Settings.System.putIntForUser(
                resolver,
                Settings.System.GAMESPACE_SUPPRESS_FULLSCREEN_INTENT,
                it.toInt(),
                UserHandle.USER_CURRENT
            )
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
        private const val KEY_USER_GAMES = "user_games"
    }
}
