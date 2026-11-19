package com.taigashinohara.mochimori

/**
 * Pure, explainable judgement for the home screen's faces.
 *
 * Honesty rule (DESIGN v0.3): wording must match what actually happens.
 * "作動中" is only returned when [Input.profileApplied] is true, i.e. the
 * periodic checker has really lowered brightness / shortened screen timeout.
 *
 * Rules (first match wins):
 *  0. Level unknown -> UNKNOWN (never guess a face)
 *  1. Charging -> CHARGING (profiles do not apply while charging)
 *  2. Temperature >= 40.0C -> NOT_SAVING (heat; the app cannot fix this)
 *  3. Level <= 15% -> EMERGENCY (show the big battery-saver button)
 *  4. Brightness >= 230/255 -> NOT_SAVING (fixable by lowering brightness)
 *  5. Level <= threshold and auto-saving OFF -> NOT_SAVING
 *  6. Level <= threshold and auto-saving ON:
 *       applied -> SAVING ("作動中"), not yet applied -> NOT_SAVING (apply now)
 *  Otherwise -> SAVING.
 */
object BatteryJudge {
    const val HOT_TEMP_C = 40.0f
    const val BRIGHTNESS_NEAR_MAX = 230
    const val EMERGENCY_PERCENT = 15
    const val CHARGE_GOOD_PERCENT = 80

    enum class State { SAVING, NOT_SAVING, EMERGENCY, CHARGING, UNKNOWN }
    enum class FixKind { NONE, BRIGHTNESS, HEAT, AUTO_OFF, APPLY_NOW, NEED_PERMISSION, EMERGENCY_SAVER, CHARGING_FULL }

    data class Input(
        val levelPercent: Int,
        val tempC: Float,
        val brightness: Int, // 0..255, -1 when unknown
        val autoSavingEnabled: Boolean,
        val saverThresholdPercent: Int = 30,
        val charging: Boolean = false,
        /** True only when the auto profile has really been applied. */
        val profileApplied: Boolean = false,
        /** True when WRITE_SETTINGS has been granted. */
        val canWriteSettings: Boolean = true,
        /** False when the battery level could not be read. */
        val levelKnown: Boolean = true,
    )

    data class Result(
        val state: State,
        val reason: String,
        val fixKind: FixKind,
        /** Rough estimate only (level x ~18h at 100%); wording must stay modest. */
        val estimatedHours: Float,
    ) {
        val saving: Boolean get() = state == State.SAVING || state == State.CHARGING
    }

    fun judge(input: Input): Result {
        val hours = (input.levelPercent.coerceIn(0, 100) / 100f) * 18f
        return when {
            !input.levelKnown -> Result(
                State.UNKNOWN,
                "電池の状態を確認中です",
                FixKind.NONE,
                0f,
            )
            input.charging -> if (input.levelPercent >= CHARGE_GOOD_PERCENT) {
                Result(
                    State.CHARGING,
                    "充電中・${input.levelPercent}%です。抜くと長持ちします",
                    FixKind.CHARGING_FULL,
                    hours,
                )
            } else {
                Result(
                    State.CHARGING,
                    "充電中・${input.levelPercent}%まで充電しています",
                    FixKind.NONE,
                    hours,
                )
            }
            input.tempC >= HOT_TEMP_C -> Result(
                State.NOT_SAVING,
                "電池が熱い（${formatTemp(input.tempC)}℃）",
                FixKind.HEAT,
                hours,
            )
            input.levelPercent <= EMERGENCY_PERCENT -> Result(
                State.EMERGENCY,
                "残量がわずかです（${input.levelPercent}%）",
                FixKind.EMERGENCY_SAVER,
                hours,
            )
            input.brightness >= BRIGHTNESS_NEAR_MAX -> Result(
                State.NOT_SAVING,
                "明るさが最大付近のまま",
                FixKind.BRIGHTNESS,
                hours,
            )
            input.levelPercent <= input.saverThresholdPercent && !input.autoSavingEnabled ->
                Result(
                    State.NOT_SAVING,
                    "残量が少ないのに自動節電がOFF",
                    FixKind.AUTO_OFF,
                    hours,
                )
            input.levelPercent <= input.saverThresholdPercent && input.autoSavingEnabled ->
                when {
                    input.profileApplied -> Result(
                        State.SAVING,
                        "節電プロファイル作動中・明るさ自動",
                        FixKind.NONE,
                        hours,
                    )
                    !input.canWriteSettings -> Result(
                        State.NOT_SAVING,
                        "節電の出番です（許可が必要です）",
                        FixKind.NEED_PERMISSION,
                        hours,
                    )
                    else -> Result(
                        State.NOT_SAVING,
                        "節電の出番です（まだ適用されていません）",
                        FixKind.APPLY_NOW,
                        hours,
                    )
                }
            else -> Result(State.SAVING, "無駄なし・静かに見守り中", FixKind.NONE, hours)
        }
    }

    private fun formatTemp(t: Float): String =
        if (t % 1f == 0f) t.toInt().toString() else String.format("%.1f", t)
}
