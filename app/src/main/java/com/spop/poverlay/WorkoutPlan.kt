package com.spop.poverlay

class WorkoutPlan private constructor(
    private val times: LongArray,
    private val states: Array<WorkoutPlanState>
) {
    fun stateAt(t: Long): WorkoutPlanState {
        val idx = times.binarySearch(t)
        return when {
            idx >= 0 -> states[idx]
            else -> {
                val i = -idx - 2 // Floor index
                states[maxOf(i, 0)]
            }
        }
    }

    companion object {
        fun build(initialState: WorkoutPlanState, entries: List<Pair<Long, WorkoutPlanState>>): WorkoutPlan {
            val combined = entries.plus(Pair(0L, initialState))
            val sorted = combined.sortedBy { it.first }
            val times = sorted.map { it.first }.toLongArray()
            val states = sorted.map { it.second }.toTypedArray()
            return WorkoutPlan(times, states)
        }
    }
}

data class WorkoutPlanState(
    val targetRpm: Int,
    val targetResistance: Int
)