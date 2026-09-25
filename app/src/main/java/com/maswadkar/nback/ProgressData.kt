package com.maswadkar.nback

/** Only rule versions with explicitly compatible score semantics may share a group. */
data class ComparisonGroup(val modeMask: Int, val level: Int, val intervalSeconds: Int, val sessionLength: Int)
fun HistoryRecord.comparisonGroup(): ComparisonGroup {
    require(rulesVersion in 1..4)
    return ComparisonGroup(modeMask, level, intervalSeconds, sessionLength)
}
internal data class ResultFilters(val level: Int, val mode: Int, val pace: Int, val length: Int)
internal data class ProgressGroup(val key: ComparisonGroup, val sessions: List<HistoryRecord>, val latest: HistoryRecord,
    val scores: List<Map<com.maswadkar.nback.engine.StimulusType, com.maswadkar.nback.engine.SessionResult>>)
// Completion is tied to a source identity, even when a reload returns equal rows.
// Identity equality prevents Compose state from conflating that fresh completion.
internal class PreparedHistory(val source: List<HistoryRecord>, val filters: ResultFilters,
    val rows: List<HistoryRecord>, val groups: List<ProgressGroup>)

/** Pure transformation; caller dispatches this away from Compose's UI thread. */
internal fun prepareHistory(records: List<HistoryRecord>, filters: ResultFilters): PreparedHistory {
    val newest = records.sortedWith(compareByDescending<HistoryRecord> { it.completedAt }.thenBy { it.id })
    val rows = newest.filter { (filters.level == 0 || it.level == filters.level) &&
        (filters.mode == 0 || it.modeMask == filters.mode) &&
        (filters.pace == 0 || it.intervalSeconds == filters.pace) &&
        (filters.length == 0 || it.sessionLength == filters.length) }
    val groups = newest.groupBy { it.comparisonGroup() }.map { (key, values) ->
        val ordered = values.sortedWith(compareBy<HistoryRecord> { it.completedAt }.thenBy { it.id })
        ProgressGroup(key, ordered, values.first(), ordered.map { it.results() })
    }
    return PreparedHistory(records, filters, rows, groups)
}
