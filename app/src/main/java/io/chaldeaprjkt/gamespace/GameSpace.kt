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
package io.chaldeaprjkt.gamespace

import android.app.Application
import android.util.Log

import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp(Application::class)
class GameSpace : Hilt_GameSpace() {

    override fun onCreate() {
        super.onCreate()
        grantSystemPermissionsViaRoot()
    }

    /**
     * Máy đã root nên tự cấp thẳng các quyền signature-level bằng "pm grant" qua
     * su, thay vì chỉ né bằng try/catch. "pm grant" chạy từ shell/root vốn được
     * phép cấp nhiều quyền signature dù app không cùng chữ ký hệ thống - đây là
     * cách dân root hay làm qua adb, giờ tự làm bằng root luôn không cần máy
     * tính. Quyền nào không cấp được kiểu này (API nội bộ kiểm UID trực tiếp
     * thay vì permission) thì các chỗ gọi tương ứng đã có try/catch riêng để
     * không crash, không phụ thuộc vào việc này thành công hay không.
     */
    private fun grantSystemPermissionsViaRoot() {
        val perms = listOf(
            "android.permission.WRITE_SECURE_SETTINGS",
            "android.permission.INTERACT_ACROSS_USERS",
            "android.permission.INTERACT_ACROSS_USERS_FULL",
            "android.permission.READ_DEVICE_CONFIG",
            "android.permission.WRITE_DEVICE_CONFIG",
            "android.permission.MANAGE_GAME_MODE",
            "com.android.systemui.permission.SCREEN_RECORDING",
            "android.permission.ACCESS_FPS_COUNTER",
            "android.permission.STATUS_BAR_SERVICE",
        )
        Thread {
            try {
                // dùng ";" (không phải "&&") để 1 lệnh grant fail không chặn các
                // lệnh còn lại - mỗi quyền thử độc lập.
                val cmd = perms.joinToString(" ; ") { "pm grant $packageName $it" } +
                    " ; appops set $packageName GET_USAGE_STATS allow"
                ProcessBuilder("su", "-c", cmd)
                    .redirectErrorStream(true)
                    .start()
                    .waitFor()
            } catch (e: Exception) {
                Log.w(TAG, "Không tự cấp được quyền qua root: ${e.message}")
            }
        }.start()
    }

    companion object {
        private const val TAG = "GameSpace"
    }
}
