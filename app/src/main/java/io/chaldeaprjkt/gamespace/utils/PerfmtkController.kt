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

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Cầu nối giữa GameSpace và module root PerfMTK (JUANIMAN/PerfMTK trên GitHub -
 * module Magisk/KernelSU/APatch cho thiết bị MediaTek) để chỉnh hiệu năng CPU.
 *
 * Gọi thẳng 1 lệnh (không tương tác), tham số là CHỮ theo đúng README gốc của
 * PerfMTK:
 *
 *      su -c "perfmtk performance"
 *      su -c "perfmtk balanced"
 *      su -c "perfmtk powersave"
 *      su -c "perfmtk powersave+"
 *
 * Nội bộ GameSpace vẫn dùng số nguyên 1..4 (khớp UI/tile/thứ tự cycle có sẵn),
 * chỉ map sang đúng tên chữ ngay trước khi gọi lệnh thật.
 */
class PerfmtkController {

    private val scope = CoroutineScope(Job() + Dispatchers.IO)

    @Volatile
    var lastResult: Result = Result.IDLE
        private set

    /**
     * Áp dụng một chế độ hiệu năng CPU thông qua perfmtk. Chạy bất đồng bộ (không
     * chặn luồng gọi); [onResult] luôn được gọi lại trên main thread dù thành công
     * hay thất bại, để UI (vd. GameModeTile) có thể cập nhật trạng thái "đang áp dụng".
     */
    fun apply(mode: Int, onResult: ((Result) -> Unit)? = null) {
        scope.launch {
            val result = runCatching { runPerfmtk(mode) }
                .getOrElse {
                    Log.e(TAG, "perfmtk mode=$mode crashed: ${it.message}")
                    Result.NO_ROOT
                }
            lastResult = result
            onResult?.let { cb -> withContext(Dispatchers.Main) { cb(result) } }
        }
    }

    private fun runPerfmtk(mode: Int): Result {
        val arg = argFor(mode)
        val process = try {
            ProcessBuilder("su", "-c", "perfmtk $arg")
                .redirectErrorStream(true)
                .start()
        } catch (e: Throwable) {
            // Không có binary "su" (thiết bị chưa root) hoặc bị chặn quyền root
            Log.e(TAG, "Không thể chạy 'su -c perfmtk $arg': ${e.message}")
            return Result.NO_ROOT
        }

        // Đợi vài trăm mili giây để perfmtk phản hồi, không chờ vô hạn để tránh
        // đứng UI nếu tiến trình su/perfmtk bị treo hoặc không phản hồi.
        val responded = process.waitFor(RESPONSE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        val output = runCatching {
            process.inputStream.bufferedReader().readText().trim()
        }.getOrDefault("")

        if (!responded) {
            process.destroy()
            Log.w(TAG, "perfmtk $arg: không phản hồi sau ${RESPONSE_TIMEOUT_MS}ms")
            return Result.TIMEOUT
        }

        val exit = process.exitValue()
        Log.d(TAG, "perfmtk $arg -> exit=$exit output=$output")
        return if (exit == 0) Result.SUCCESS else Result.FAILED
    }

    private fun argFor(mode: Int) = when (mode) {
        MODE_PERFORMANCE -> "performance"
        MODE_BALANCED -> "balanced"
        MODE_POWER_SAVE -> "powersave"
        MODE_POWER_SAVE_PLUS -> "powersave+"
        else -> "balanced"
    }

    enum class Result { IDLE, SUCCESS, FAILED, TIMEOUT, NO_ROOT }

    companion object {
        private const val TAG = "PerfmtkController"

        /** Thời gian tối đa chờ perfmtk phản hồi sau khi gửi lệnh (mili giây). */
        private const val RESPONSE_TIMEOUT_MS = 300L

        const val MODE_PERFORMANCE = 1
        const val MODE_BALANCED = 2
        const val MODE_POWER_SAVE = 3
        const val MODE_POWER_SAVE_PLUS = 4

        val ALL_MODES =
            listOf(MODE_PERFORMANCE, MODE_BALANCED, MODE_POWER_SAVE, MODE_POWER_SAVE_PLUS)
        const val DEFAULT_MODE = MODE_BALANCED
    }
}
