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
package io.chaldeaprjkt.gamespace.widget.tiles

import android.content.Context
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.View
import io.chaldeaprjkt.gamespace.R
import io.chaldeaprjkt.gamespace.utils.GameModeUtils.Companion.describeGameMode
import io.chaldeaprjkt.gamespace.utils.PerfmtkController
import io.chaldeaprjkt.gamespace.utils.di.ServiceViewEntryPoint
import io.chaldeaprjkt.gamespace.utils.entryPointOf

class GameModeTile @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : BaseTile(context, attrs) {

    private val gameModeUtils by lazy {
        context.entryPointOf<ServiceViewEntryPoint>().gameModeUtils()
    }

    // 1=Performance, 2=Balanced, 3=Power save, 4=Power save+ (đúng tham số perfmtk nhận)
    private val modes = PerfmtkController.ALL_MODES

    // Khoá tile trong lúc chờ perfmtk phản hồi để tránh bấm dồn dập khi chuyển mode
    private var isApplying = false
        set(value) {
            field = value
            isEnabled = !value
            icon?.alpha = if (value) 0.4f else 1f
        }

    private var activeMode = PerfmtkController.DEFAULT_MODE
        set(value) {
            field = value
            summary?.text = context.describeGameMode(value)
            isSelected = value != PerfmtkController.DEFAULT_MODE
            icon?.setImageResource(iconFor(value))
        }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        title?.text = context.getString(R.string.game_mode_title)
        activeMode = gameModeUtils.activeGame?.mode ?: PerfmtkController.DEFAULT_MODE
    }

    override fun onClick(v: View?) {
        super.onClick(v)
        if (isApplying) return

        val current = modes.indexOf(activeMode)
        val next = modes[if (current == modes.size - 1) 0 else current + 1]
        performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)

        activeMode = next
        isApplying = true
        gameModeUtils.setActiveGameMode(systemSettings, next) {
            // callback từ PerfmtkController, luôn chạy trên main thread
            isApplying = false
        }
    }

    private fun iconFor(mode: Int) = when (mode) {
        PerfmtkController.MODE_PERFORMANCE -> R.drawable.ic_speed
        PerfmtkController.MODE_BALANCED -> R.drawable.ic_mode_balanced
        PerfmtkController.MODE_POWER_SAVE -> R.drawable.ic_mode_powersave
        PerfmtkController.MODE_POWER_SAVE_PLUS -> R.drawable.ic_mode_powersave_plus
        else -> R.drawable.ic_speed
    }
}
