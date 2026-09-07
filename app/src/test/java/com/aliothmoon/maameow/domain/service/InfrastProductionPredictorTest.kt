package com.aliothmoon.maameow.domain.service

import com.aliothmoon.maameow.data.model.CustomInfrastConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class InfrastProductionPredictorTest {

    private val today = LocalDate.of(2026, 9, 7)

    @Test
    fun `predicts gold exp and lmd production from selected plan`() {
        val prediction = predict(
            plan(
                manufacture = listOf(room("Pure Gold"), room("Battle Record")),
                trading = listOf(room("LMD")),
            )
        )

        assertEquals(10_000.0, prediction.flow.lmdProduced, 0.01)
        assertEquals(8_000.0, prediction.flow.battleRecordExpProduced, 0.01)
        assertEquals(20.0, prediction.flow.pureGoldProduced, 0.01)
        assertEquals(20.0, prediction.flow.pureGoldConsumed, 0.01)
        assertTrue(prediction.flow.isPureGoldBalanced)
    }

    @Test
    fun `originium shard production consumes rock and lmd and calculates runway`() {
        val prediction = predict(
            plan(manufacture = listOf(room("Originium Shard"))),
            depot = mapOf(
                InfrastProductionPredictor.ORIROCK_CUBE_ID to 96,
                InfrastProductionPredictor.LMD_ID to 76_800,
            ),
        )

        assertEquals(24.0, prediction.flow.originiumShardProduced, 0.01)
        assertEquals(48.0, prediction.flow.orirockCubeConsumed, 0.01)
        assertEquals(38_400.0, prediction.flow.lmdConsumed, 0.01)
        prediction.runways
            .filter { it.material in setOf(InfrastMaterial.LMD, InfrastMaterial.ORIROCK_CUBE) }
            .forEach {
                assertEquals(2.0, it.daysRemaining!!, 0.01)
                assertEquals(today.plusDays(2), it.shortageDate)
            }
    }

    @Test
    fun `orundum order consumes the shards made by one factory`() {
        val prediction = predict(
            plan(
                manufacture = listOf(room("Originium Shard")),
                trading = listOf(room("Orundum")),
            )
        )

        assertEquals(240.0, prediction.flow.orundumProduced, 0.01)
        assertEquals(0.0, prediction.flow.originiumShardNet, 0.01)
    }

    @Test
    fun `automatic schedule weights plans by their periods`() {
        val config = CustomInfrastConfig(
            plans = listOf(
                plan(
                    manufacture = listOf(room("Pure Gold")),
                    period = listOf(listOf("00:00", "11:59")),
                ),
                plan(
                    manufacture = listOf(room("Battle Record")),
                    period = listOf(listOf("12:00", "23:59")),
                ),
            )
        )

        val prediction = InfrastProductionPredictor.predict(
            config = config,
            selectedPlanIndex = -1,
            depot = emptyMap(),
            manufactureEfficiencyPercent = 100,
            tradingEfficiencyPercent = 100,
            today = today,
        )

        assertEquals(10.0, prediction.flow.pureGoldProduced, 0.01)
        assertEquals(4_000.0, prediction.flow.battleRecordExpProduced, 0.01)
    }

    @Test
    fun `efficiency calibration scales each facility type`() {
        val prediction = predict(
            plan(
                manufacture = listOf(room("Pure Gold")),
                trading = listOf(room("LMD")),
            ),
            manufactureEfficiency = 175,
            tradingEfficiency = 150,
        )

        assertEquals(35.0, prediction.flow.pureGoldProduced, 0.01)
        assertEquals(15_000.0, prediction.flow.lmdProduced, 0.01)
        assertEquals(-5.0, prediction.flow.pureGoldNet, 0.01)
        assertFalse(prediction.flow.isPureGoldBalanced)
    }

    private fun predict(
        plan: CustomInfrastConfig.Plan,
        depot: Map<String, Int> = emptyMap(),
        manufactureEfficiency: Int = 100,
        tradingEfficiency: Int = 100,
    ) = InfrastProductionPredictor.predict(
        config = CustomInfrastConfig(plans = listOf(plan)),
        selectedPlanIndex = 0,
        depot = depot,
        manufactureEfficiencyPercent = manufactureEfficiency,
        tradingEfficiencyPercent = tradingEfficiency,
        today = today,
    )

    private fun plan(
        manufacture: List<CustomInfrastConfig.ProductionRoom> = emptyList(),
        trading: List<CustomInfrastConfig.ProductionRoom> = emptyList(),
        period: List<List<String>> = emptyList(),
    ) = CustomInfrastConfig.Plan(
        period = period,
        rooms = CustomInfrastConfig.Rooms(trading = trading, manufacture = manufacture),
    )

    private fun room(product: String) = CustomInfrastConfig.ProductionRoom(product = product)
}
