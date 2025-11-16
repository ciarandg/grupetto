package com.spop.poverlay

import org.junit.Test
import org.junit.Assert.*

class WorkoutPlanTest {
    @Test
    fun testEmptyWorkoutPlan() {
        val s1 = WorkoutPlanState(targetRpm = 10, targetResistance = 20)
        val plan = WorkoutPlan.build(s1, listOf())

        assertEquals(s1, plan.stateAt(0))
        assertEquals(s1, plan.stateAt(-1))
        assertEquals(s1, plan.stateAt(-100))
        assertEquals(s1, plan.stateAt(100))
    }

    @Test
    fun testSimpleWorkoutPlan() {
        val s1 = WorkoutPlanState(targetRpm = 10, targetResistance = 20)
        val s2 = WorkoutPlanState(targetRpm = 30, targetResistance = 40)
        val plan = WorkoutPlan.build(s1, listOf(Pair(100L, s2)))

        assertEquals(s1, plan.stateAt(0))
        assertEquals(s1, plan.stateAt(-1))
        assertEquals(s1, plan.stateAt(-100))
        assertEquals(s1, plan.stateAt(99))
        assertEquals(s2, plan.stateAt(100))
        assertEquals(s2, plan.stateAt(101))
        assertEquals(s2, plan.stateAt(500))
    }

    @Test
    fun testComplexWorkoutPlan() {
        val s1 = WorkoutPlanState(targetRpm = 10, targetResistance = 20)
        val s2 = WorkoutPlanState(targetRpm = 30, targetResistance = 40)
        val s3 = WorkoutPlanState(targetRpm = 50, targetResistance = 60)
        val plan = WorkoutPlan.build(s1, listOf(
            Pair(100L, s2),
            Pair(200L, s3),
        ))

        assertEquals(s1, plan.stateAt(0))
        assertEquals(s1, plan.stateAt(-1))
        assertEquals(s1, plan.stateAt(-100))
        assertEquals(s1, plan.stateAt(99))
        assertEquals(s2, plan.stateAt(100))
        assertEquals(s2, plan.stateAt(101))
        assertEquals(s2, plan.stateAt(199))
        assertEquals(s3, plan.stateAt(200))
        assertEquals(s3, plan.stateAt(201))
        assertEquals(s3, plan.stateAt(500))
    }

    @Test
    fun testComplexWorkoutPlanOutOfOrder() {
        val s1 = WorkoutPlanState(targetRpm = 10, targetResistance = 20)
        val s2 = WorkoutPlanState(targetRpm = 30, targetResistance = 40)
        val s3 = WorkoutPlanState(targetRpm = 50, targetResistance = 60)
        val plan = WorkoutPlan.build(s1, listOf(
            Pair(200L, s3),
            Pair(100L, s2),
        ))

        assertEquals(s1, plan.stateAt(0))
        assertEquals(s1, plan.stateAt(-1))
        assertEquals(s1, plan.stateAt(-100))
        assertEquals(s1, plan.stateAt(99))
        assertEquals(s2, plan.stateAt(100))
        assertEquals(s2, plan.stateAt(101))
        assertEquals(s2, plan.stateAt(199))
        assertEquals(s3, plan.stateAt(200))
        assertEquals(s3, plan.stateAt(201))
        assertEquals(s3, plan.stateAt(500))
    }

    @Test
    fun testComplexWorkoutPlanConflictingTimestamps() {
        val s1 = WorkoutPlanState(targetRpm = 10, targetResistance = 20)
        val s2 = WorkoutPlanState(targetRpm = 30, targetResistance = 40)
        val s3 = WorkoutPlanState(targetRpm = 50, targetResistance = 60)
        val plan = WorkoutPlan.build(s1, listOf(
            Pair(100L, s2),
            Pair(100L, s3),
        ))

        // Don't really care too much about the behaviour here, but would prefer to enforce consistency
        assertEquals(s1, plan.stateAt(0))
        assertEquals(s2, plan.stateAt(100))
        assertEquals(s3, plan.stateAt(101))
    }
}
