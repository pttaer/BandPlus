package vn.bandplus.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.time.LocalDate

class HealthRepository(private val src: HealthSource) {
    fun day(date: LocalDate): Flow<DaySummary> = io { src.day(date) }
    fun range(from: LocalDate, to: LocalDate): Flow<List<DaySummary>> = io {
        generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.map(src::day).toList()
    }
    fun hr(date: LocalDate) = io { src.hr(date) }
    fun spo2(date: LocalDate) = io { src.spo2(date) }
    fun stress(date: LocalDate) = io { src.stress(date) }
    fun lastSyncedAt() = io { src.lastSyncedAt() }

    private fun <T> io(block: () -> T): Flow<T> = flow { emit(block()) }.flowOn(Dispatchers.IO)
}
