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
import org.isoron.uhabits.core.ui.views.Theme
import kotlin.math.max

data class TargetCardState(
    val color: PaletteColor,
    val values: List<Double> = listOf(),
    val targets: List<Double> = listOf(),
    val intervals: List<Int> = listOf(),
    val theme: Theme
)

class TargetCardPresenter {
    companion object {
        fun buildState(
            habit: Habit,
            firstWeekday: Int,
            theme: Theme
        ): TargetCardState {
            val today = getToday()
            val oldest = habit.computedEntries.getKnown().lastOrNull()?.date ?: today
            val entries = habit.computedEntries.getByInterval(oldest, today)

            // NOTE: these must have the same size as TruncateField.values()
            val daysInPeriod = arrayOf(1, 7, today.monthLength, today.quarterLength, today.yearLength)
            val monthsInPeriod = arrayOf(1/today.monthLength, 7/today.monthLength, 1, 3, 12)

            val denominator = when (habit.frequency.denominator) {
                30 -> daysInPeriod[TruncateField.MONTH.ordinal]
                else -> habit.frequency.denominator
            }

            val values = mutableListOf<Double>()
            val targets = mutableListOf<Double>()
            val intervals = mutableListOf<Int>()

            for ((i, timePeriod) in TruncateField.entries.withIndex()) {
                if (denominator > daysInPeriod[i])
                    continue

                val value = entries.groupedSum(
                    truncateField = timePeriod,
                    firstWeekday = firstWeekday,
                    isNumerical = habit.isNumerical
                ).firstOrNull()?.value ?: 0

                val skippedDays = entries.countSkippedDays(
                    truncateField = timePeriod,
                    firstWeekday = firstWeekday
                ).firstOrNull()?.value ?: 0

                var target = when (habit.frequency.denominator) {
                    30 -> habit.targetValue * monthsInPeriod[i]
                    7 -> habit.targetValue/7 * daysInPeriod[i]
                    else -> habit.targetValue * daysInPeriod[i]
                }
                // FIXME: Even though the daily target for a monthly habit
                //   changes depending on the length of the current month, skip
                //   days contribute the average over 3/12 months to the
                //   quarter/year targets. This is only a slight inaccuracy and
                //   to correct it we would need to look up the length of the
                //   month of each skip day, so maybe not worth it.
                target = max(0.0, target - target/daysInPeriod[i] * skippedDays)

                values.add(value / 1e3)
                targets.add(target)
                intervals.add(daysInPeriod[i])
            }

            return TargetCardState(
                color = habit.color,
                values = values,
                targets = targets,
                intervals = intervals,
                theme = theme
            )
        }
    }
}
