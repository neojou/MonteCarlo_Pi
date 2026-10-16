package com.neojou.montecarlopi.sim

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SimulationTest {
    @Test
    fun mulberry32MatchesJavaScript() {
        // Values produced by the HTML's mulberry32(42) in Node.js
        val expected = doubleArrayOf(
            0.6011037519201636, 0.44829055899754167, 0.8524657934904099,
            0.6697340414393693, 0.17481389874592423,
        )
        val r = Mulberry32(42)
        expected.forEach { assertEquals(it, r.next(), 0.0) }
    }

    @Test
    fun seed42First5000PointsMatchHtml() {
        val e = MonteCarloEngine()
        e.rate = 5000; e.total = 1_000_000
        e.start(42)
        e.step()
        assertEquals(5000L, e.n)
        assertEquals(3937L, e.inside) // HTML screenshot: 圓內點數 3,937
        assertEquals("3.14960", toFixed(Estimator.estimate(e.inside, e.n), 5))
        assertEquals("0.023145", toFixed(Estimator.sigma(e.inside, e.n), 6))
        assertEquals(5000, e.drawn)
    }

    @Test
    fun sigmaAndInterval() {
        val s = Estimator.sigma(3937, 5000)
        assertTrue(abs(s - 0.02314484754756445) < 1e-15)
        val (lo, hi) = Estimator.interval(3937, 5000)
        assertEquals("3.08017", toFixed(lo, 5))
        assertEquals("3.21903", toFixed(hi, 5))
    }

    @Test
    fun paramMappings() {
        assertEquals(1_000_000L, Params.total(6f))
        assertEquals(1000L, Params.total(3f))
        assertEquals(10_000_000L, Params.total(7f))
        assertEquals(1000L, Params.rate(3f))
        assertEquals(10L, Params.rate(1f))
        assertEquals(50_000L, Params.rate(4.69897f))
        assertEquals("1,000,000", fmtI(1_000_000L))
        assertEquals("489", fmtI(489.4))
    }

    @Test
    fun stateMachine() {
        val e = MonteCarloEngine()
        e.total = 1000; e.rate = 600
        e.start(1); assertEquals(SimState.Run, e.state)
        e.onFrame(16.0); e.pause(); assertEquals(SimState.Pause, e.state)
        e.start(0); e.onFrame(32.0); assertEquals(SimState.Done, e.state); assertEquals(1000L, e.n)
        e.start(1); assertEquals(SimState.Done, e.state)
        e.reset(); assertEquals(SimState.Idle, e.state); assertEquals(0L, e.n)
    }
}
