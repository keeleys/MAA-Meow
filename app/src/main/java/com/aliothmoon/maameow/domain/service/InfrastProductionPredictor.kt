package com.aliothmoon.maameow.domain.service

import com.aliothmoon.maameow.data.model.CustomInfrastConfig
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

/**
 * 根据 MAA 自定义基建排班的房间产物预测 24 小时生产链。
 *
 * 基础速率按三级设施、总效率 100% 计算。干员技能组合、订单概率、心情断档和无人机
 * 无法仅从排班 JSON 精确复现，因此由调用方传入可校准的制造站/贸易站总效率。
 */
object InfrastProductionPredictor {

    const val LMD_ID = "4001"
    const val PURE_GOLD_ID = "3003"
    const val ORIROCK_CUBE_ID = "30012"
    const val ORIGINIUM_SHARD_ID = "3141"
    const val ORUNDUM_ID = "4003"

    private const val MINUTES_PER_DAY = 24 * 60

    // 三级设施在 100% 总效率下的基准日产。
    private const val GOLD_PER_MFG_DAY = 20.0
    private const val EXP_PER_MFG_DAY = 8_000.0
    private const val SHARD_PER_MFG_DAY = 24.0
    private const val LMD_PER_TRADE_DAY = 10_000.0
    private const val GOLD_PER_TRADE_DAY = 20.0
    private const val ORUNDUM_PER_TRADE_DAY = 240.0
    private const val SHARD_PER_TRADE_DAY = 24.0
    private const val ROCK_PER_SHARD = 2.0
    private const val LMD_PER_SHARD = 1_600.0

    fun predict(
        config: CustomInfrastConfig,
        selectedPlanIndex: Int,
        depot: Map<String, Int>,
        manufactureEfficiencyPercent: Int,
        tradingEfficiencyPercent: Int,
        today: LocalDate = LocalDate.now(),
    ): InfrastProductionPrediction {
        if (config.plans.isEmpty()) {
            return InfrastProductionPrediction(
                warnings = setOf(InfrastPredictionWarning.NO_PLAN),
            )
        }

        val weights = planMinuteWeights(config.plans, selectedPlanIndex)
        val lines = mutableMapOf<InfrastProductLine, Double>()
        val warnings = linkedSetOf<InfrastPredictionWarning>()
        val unsupported = linkedSetOf<String>()

        config.plans.forEachIndexed { index, plan ->
            val dayShare = weights[index] / MINUTES_PER_DAY.toDouble()
            if (dayShare <= 0.0) return@forEachIndexed

            plan.rooms.manufacture.forEach { room ->
                addRoom(
                    room = room,
                    dayShare = dayShare,
                    facility = Facility.MANUFACTURE,
                    lines = lines,
                    warnings = warnings,
                    unsupported = unsupported,
                )
            }
            plan.rooms.trading.forEach { room ->
                addRoom(
                    room = room,
                    dayShare = dayShare,
                    facility = Facility.TRADING,
                    lines = lines,
                    warnings = warnings,
                    unsupported = unsupported,
                )
            }
            if (plan.drones?.enable == true) {
                warnings += InfrastPredictionWarning.DRONES_NOT_INCLUDED
            }
        }

        if (lines.isEmpty()) warnings += InfrastPredictionWarning.NO_PRODUCTION_ROOM
        if (unsupported.isNotEmpty()) warnings += InfrastPredictionWarning.UNSUPPORTED_PRODUCT

        val mfgScale = manufactureEfficiencyPercent.coerceIn(100, 300) / 100.0
        val tradeScale = tradingEfficiencyPercent.coerceIn(100, 300) / 100.0
        val goldMade = lines[InfrastProductLine.PURE_GOLD].orZero() * GOLD_PER_MFG_DAY * mfgScale
        val expMade = lines[InfrastProductLine.BATTLE_RECORD].orZero() * EXP_PER_MFG_DAY * mfgScale
        val shardMade = lines[InfrastProductLine.ORIGINIUM_SHARD].orZero() * SHARD_PER_MFG_DAY * mfgScale
        val lmdMade = lines[InfrastProductLine.LMD_ORDER].orZero() * LMD_PER_TRADE_DAY * tradeScale
        val goldUsed = lines[InfrastProductLine.LMD_ORDER].orZero() * GOLD_PER_TRADE_DAY * tradeScale
        val orundumMade = lines[InfrastProductLine.ORUNDUM_ORDER].orZero() * ORUNDUM_PER_TRADE_DAY * tradeScale
        val shardUsed = lines[InfrastProductLine.ORUNDUM_ORDER].orZero() * SHARD_PER_TRADE_DAY * tradeScale
        val rockUsed = shardMade * ROCK_PER_SHARD
        val shardLmdUsed = shardMade * LMD_PER_SHARD

        val flow = InfrastDailyFlow(
            lmdProduced = lmdMade,
            lmdConsumed = shardLmdUsed,
            battleRecordExpProduced = expMade,
            pureGoldProduced = goldMade,
            pureGoldConsumed = goldUsed,
            orundumProduced = orundumMade,
            orirockCubeConsumed = rockUsed,
            originiumShardProduced = shardMade,
            originiumShardConsumed = shardUsed,
        )

        val runways = listOf(
            runway(InfrastMaterial.LMD, depot[LMD_ID] ?: 0, flow.lmdNet, today),
            runway(InfrastMaterial.PURE_GOLD, depot[PURE_GOLD_ID] ?: 0, flow.pureGoldNet, today),
            runway(InfrastMaterial.ORIROCK_CUBE, depot[ORIROCK_CUBE_ID] ?: 0, flow.orirockCubeNet, today),
            runway(InfrastMaterial.ORIGINIUM_SHARD, depot[ORIGINIUM_SHARD_ID] ?: 0, flow.originiumShardNet, today),
        )

        return InfrastProductionPrediction(
            flow = flow,
            lineDays = lines.toMap(),
            runways = runways,
            warnings = warnings,
            unsupportedProducts = unsupported,
        )
    }

    private fun addRoom(
        room: CustomInfrastConfig.ProductionRoom,
        dayShare: Double,
        facility: Facility,
        lines: MutableMap<InfrastProductLine, Double>,
        warnings: MutableSet<InfrastPredictionWarning>,
        unsupported: MutableSet<String>,
    ) {
        if (room.skip) {
            warnings += InfrastPredictionWarning.SKIPPED_ROOM
            return
        }
        val raw = room.product?.trim().orEmpty()
        val product = normalizeProduct(facility, raw)
        if (product == null) {
            warnings += InfrastPredictionWarning.MISSING_PRODUCT
            if (raw.isNotEmpty()) unsupported += raw
            return
        }
        lines[product] = lines[product].orZero() + dayShare
    }

    private fun normalizeProduct(facility: Facility, raw: String): InfrastProductLine? {
        val key = raw.lowercase().filter(Char::isLetterOrDigit)
        return when (facility) {
            Facility.MANUFACTURE -> when (key) {
                "battlerecord", "combatrecord" -> InfrastProductLine.BATTLE_RECORD
                "puregold" -> InfrastProductLine.PURE_GOLD
                "originiumshard", "originstone" -> InfrastProductLine.ORIGINIUM_SHARD
                else -> null
            }

            Facility.TRADING -> when (key) {
                "lmd", "money" -> InfrastProductLine.LMD_ORDER
                "orundum", "syntheticjade" -> InfrastProductLine.ORUNDUM_ORDER
                else -> null
            }
        }
    }

    private fun planMinuteWeights(
        plans: List<CustomInfrastConfig.Plan>,
        selectedPlanIndex: Int,
    ): IntArray {
        val weights = IntArray(plans.size)
        if (selectedPlanIndex in plans.indices) {
            weights[selectedPlanIndex] = MINUTES_PER_DAY
            return weights
        }

        val hasPeriod = plans.any { it.period.isNotEmpty() }
        if (!hasPeriod) {
            weights[0] = MINUTES_PER_DAY
            return weights
        }

        repeat(MINUTES_PER_DAY) { minute ->
            val planIndex = plans.indexOfFirst { plan ->
                plan.period.any { range -> minuteInRange(minute, range) }
            }.takeIf { it >= 0 } ?: 0
            weights[planIndex]++
        }
        return weights
    }

    private fun minuteInRange(minute: Int, range: List<String>): Boolean {
        if (range.size < 2) return false
        val formatter = DateTimeFormatter.ofPattern("H:mm")
        val start = runCatching { LocalTime.parse(range[0], formatter) }.getOrNull() ?: return false
        val end = runCatching { LocalTime.parse(range[1], formatter) }.getOrNull() ?: return false
        val startMinute = start.hour * 60 + start.minute
        val endMinute = end.hour * 60 + end.minute
        return if (startMinute <= endMinute) {
            minute in startMinute..endMinute
        } else {
            minute >= startMinute || minute <= endMinute
        }
    }

    private fun runway(
        material: InfrastMaterial,
        stock: Int,
        dailyNet: Double,
        today: LocalDate,
    ): InfrastMaterialRunway {
        val days = if (dailyNet < 0.0) stock / -dailyNet else null
        val shortageDate = days?.let { today.plusDays(ceil(it).toLong()) }
        return InfrastMaterialRunway(material, stock, dailyNet, days, shortageDate)
    }

    private fun Double?.orZero(): Double = this ?: 0.0

    private enum class Facility { MANUFACTURE, TRADING }
}

enum class InfrastProductLine {
    BATTLE_RECORD,
    PURE_GOLD,
    ORIGINIUM_SHARD,
    LMD_ORDER,
    ORUNDUM_ORDER,
}

enum class InfrastMaterial {
    LMD,
    PURE_GOLD,
    ORIROCK_CUBE,
    ORIGINIUM_SHARD,
}

enum class InfrastPredictionWarning {
    NO_PLAN,
    NO_PRODUCTION_ROOM,
    MISSING_PRODUCT,
    SKIPPED_ROOM,
    UNSUPPORTED_PRODUCT,
    DRONES_NOT_INCLUDED,
}

data class InfrastDailyFlow(
    val lmdProduced: Double = 0.0,
    val lmdConsumed: Double = 0.0,
    val battleRecordExpProduced: Double = 0.0,
    val pureGoldProduced: Double = 0.0,
    val pureGoldConsumed: Double = 0.0,
    val orundumProduced: Double = 0.0,
    val orirockCubeConsumed: Double = 0.0,
    val originiumShardProduced: Double = 0.0,
    val originiumShardConsumed: Double = 0.0,
) {
    val lmdNet: Double get() = lmdProduced - lmdConsumed
    val pureGoldNet: Double get() = pureGoldProduced - pureGoldConsumed
    val orirockCubeNet: Double get() = -orirockCubeConsumed
    val originiumShardNet: Double get() = originiumShardProduced - originiumShardConsumed
    val isPureGoldBalanced: Boolean get() = pureGoldNet >= -0.01
}

data class InfrastMaterialRunway(
    val material: InfrastMaterial,
    val stock: Int,
    val dailyNet: Double,
    val daysRemaining: Double?,
    val shortageDate: LocalDate?,
)

data class InfrastProductionPrediction(
    val flow: InfrastDailyFlow = InfrastDailyFlow(),
    val lineDays: Map<InfrastProductLine, Double> = emptyMap(),
    val runways: List<InfrastMaterialRunway> = emptyList(),
    val warnings: Set<InfrastPredictionWarning> = emptySet(),
    val unsupportedProducts: Set<String> = emptySet(),
)
