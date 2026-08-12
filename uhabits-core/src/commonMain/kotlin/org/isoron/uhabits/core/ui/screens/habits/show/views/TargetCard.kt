/*
 * Copyright (C) 2016-2025 Álinson Santos Xavier <git@axavier.org>
 *
 * This file is part of Loop Habit Tracker.
 *
 * Loop Habit Tracker is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * Loop Habit Tracker is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.isoron.uhabits.core.ui.screens.habits.show.views

import org.isoron.platform.time.TruncateField
import org.isoron.platform.time.getToday
import org.isoron.uhabits.core.models.Habit
import org.isoron.uhabits.core.models.PaletteColor
import org.isoron.uhabits.core.models.countSkippedDays
import org.isoron.uhabits.core.models.groupedSum
import org.isoron.uhabits.core.preferences.Preferences
import org.isoron.uhabits.core.ui.views.Theme
import kotlin.math.max

data class TargetCardState(
    val color: PaletteColor,
    val values: List<Double> = listOf(),
    val targets: List<Double> = listOf(),
    val intervals: List<Int> = listOf(),
    val spinnerPosition: Int,
    val theme: Theme
)

class TargetCardPresenter(
    val preferences: Preferences,
    val screen: Screen
) {
    companion object {
        fun buildState(
            habit: Habit,
            firstWeekday: Int,
            spinnerPosition: Int,
            theme: Theme
        ): TargetCardState {
            val today = getToday()
            val oldest = habit.computedEntries.getKnown().lastOrNull()?.date ?: today
            val entries = habit.computedEntries.getByInterval(oldest, today)

            // TODO; have a look at ScoreCard's getTruncateField
            val periods = arrayOf(
                TruncateField.DAY,
                TruncateField.WEEK_NUMBER,
                TruncateField.MONTH,
                TruncateField.QUARTER,
                TruncateField.YEAR
            )

            val valueThisPeriod = IntArray(periods.size)
            val daysThisPeriod = IntArray(periods.size)
            val skippedDaysThisPeriod = IntArray(periods.size)

            for ((i, truncateField) in periods.withIndex()) {
                valueThisPeriod[i] = entries.groupedSum(
                    truncateField = truncateField,
                    firstWeekday = firstWeekday,
                    isNumerical = habit.isNumerical
                ).firstOrNull()?.value ?: 0

                // TODO: is this 1 at index 0, i.e. "daysToday"?
                daysThisPeriod[i] = today.truncate(
                    field = truncateField,
                    firstWeekday = firstWeekday
                ).daysUntil(today) + 1

                skippedDaysThisPeriod[i] = entries.countSkippedDays(
                    truncateField = truncateField,
                    firstWeekday = firstWeekday // TODO: do we need this?
                ).firstOrNull()?.value ?: 0
            }

            val daysInMonth = today.monthLength
            val daysInYear = today.yearLength

            val daysInPeriod = intArrayOf(
                1,
                7,
                daysInMonth,
                91,
                daysInYear
            )

            val denominator = when (habit.frequency.denominator) {
                30 -> daysInMonth
                else -> habit.frequency.denominator
            }
            val dailyTarget = habit.targetValue / denominator

            // val denominators = intArrayOf(1, 7, 31, 92, 365)
            // val denominators = intArrayOf(1, 7, 30, 91, 365)
            val targetThisPeriod = DoubleArray(daysInPeriod.size)
            for ((i, n) in daysInPeriod.withIndex()) {
                targetThisPeriod[i] = max(
                    0.0,
                    dailyTarget * n - dailyTarget * skippedDaysThisPeriod[i]
                )
            }

            val values = mutableListOf<Double>()
            val targets = mutableListOf<Double>()

            for (i in 0..<daysInPeriod.size) {
                if (denominator > daysInPeriod[i]) {
                    continue
                }
                if (spinnerPosition == 0) {
                    // sum
                    values.add(valueThisPeriod[i] / 1e3)
                    targets.add(targetThisPeriod[i])
                } else {
                    // average
                    if (daysThisPeriod[i] == skippedDaysThisPeriod[i]) {
                        values.add(0.0)
                        targets.add(0.0)
                    } else {
                        values.add(valueThisPeriod[i] / 1e3 / (daysThisPeriod[i] - skippedDaysThisPeriod[i]) * denominator)
                        targets.add(habit.targetValue)
                    }
                }
            }

            val intervals = mutableListOf<Int>()
            if (denominator <= 1) intervals.add(1)
            if (denominator <= 7) intervals.add(7)
            // TODO: daysInMonth?
            intervals.add(30)
            intervals.add(91)
            // TODO: daysInYear?
            intervals.add(365)

            return TargetCardState(
                color = habit.color,
                values = values,
                targets = targets,
                intervals = intervals,
                spinnerPosition = spinnerPosition,
                theme = theme
            )
        }
    }

    fun onSpinnerPosition(position: Int) {
        preferences.targetCardSpinnerPosition = position
        screen.updateWidgets()
        screen.refresh()
    }

    interface Screen {
        fun updateWidgets()
        fun refresh()
    }
}
