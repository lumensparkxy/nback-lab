package com.maswadkar.nback

import org.junit.Assert.*
import org.junit.Test

class ProgressDataTest {
    private fun row(id:String, time:Long=1000, n:Int=2, length:Int=20, pace:Int=3, mode:Int=1, rules:Int=1)=
        HistoryRecord(id,time,n,rules,hits=4,misses=2,falseAlarms=3,correctRejections=11,
            modeMask=mode,intervalSeconds=pace,sessionLength=length)
    @Test fun exactGroupsKeepLegacyEndpointsAndDoNotCombineDifferentTasks() {
        val first=row("a")
        val second=row("b",2000,rules=3).copy(hits=5,misses=1,falseAlarms=1,correctRejections=13)
        val all=listOf(first,second,row("n",n=3),row("pace",pace=8),row("length",length=50),row("mode",mode=3))
        val data=prepareHistory(all,ResultFilters(0,0,0,0))
        assertEquals(6,data.rows.size);assertEquals(5,data.groups.size)
        val group=data.groups.first { it.key == first.comparisonGroup() }
        assertEquals(listOf("a","b"),group.sessions.map { it.id })
        assertEquals(listOf(75,90),group.sessions.map { it.result().accuracy })
        assertEquals(second,group.latest)
        assertEquals(listOf(second,first),prepareHistory(all,ResultFilters(2,1,3,20)).rows)
    }
    @Test fun allTenThousandSessionsAreKeptAndEqualTimesHaveDeterministicOrdering() {
        val rows=(0 until 10000).map { row(it.toString().padStart(5,'0'), (it/2).toLong()) }.reversed()
        val data=prepareHistory(rows,ResultFilters(0,0,0,0))
        assertEquals(10000,data.rows.size);assertEquals(10000,data.groups.single().sessions.size)
        assertEquals("09998",data.rows.first().id)
        assertEquals("00000",data.groups.single().sessions.first().id)
        assertEquals("09999",data.groups.single().sessions.last().id)
        assertTrue(prepareHistory(rows,ResultFilters(3,0,0,0)).rows.isEmpty())
        assertTrue(prepareHistory(emptyList(),ResultFilters(0,0,0,0)).groups.isEmpty())
    }
}
