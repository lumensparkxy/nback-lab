package com.maswadkar.nback.engine

/** Stable storage codes, independent of enum declaration order. */
fun Outcome.storageCode(): Char = when (this) {
    Outcome.HIT -> 'H'; Outcome.MISS -> 'M'; Outcome.FALSE_ALARM -> 'F'; Outcome.CORRECT_REJECTION -> 'C'
}
fun decodeOutcomes(value: String): List<Outcome> = value.map {
    when (it) { 'H' -> Outcome.HIT; 'M' -> Outcome.MISS; 'F' -> Outcome.FALSE_ALARM; 'C' -> Outcome.CORRECT_REJECTION
        else -> throw IllegalArgumentException("Invalid outcome code") }
}
data class AccuracyPoint(val scoredTurn: Int, val elapsedMillis: Long, val percentage: Double)
fun accuracyTimeline(config: SessionConfig, outcomes: List<Outcome>): List<AccuracyPoint> {
    require(!config.practice && outcomes.size == SessionRules.SCORED_TRIALS)
    var correct = 0
    return outcomes.mapIndexed { index, outcome ->
        if (outcome == Outcome.HIT || outcome == Outcome.CORRECT_REJECTION) correct++
        val turn = index + 1
        AccuracyPoint(turn, (config.level + turn) * config.intervalMillis, 100.0 * correct / turn)
    }
}
