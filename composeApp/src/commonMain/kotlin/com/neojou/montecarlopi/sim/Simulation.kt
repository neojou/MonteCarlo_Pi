package com.neojou.montecarlopi.sim

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

/** JS `Math.round` (half rounds toward +∞), unlike Kotlin's half-even `round`. */
fun jsRound(x: Double): Double = floor(x + 0.5)

/**
 * mulberry32 PRNG, bit-identical to the JS version in the original index.html:
 * `a=a+0x6D2B79F5|0; t=imul(a^a>>>15,1|a); t=t+imul(t^t>>>7,61|t)^t; ((t^t>>>14)>>>0)/2^32`.
 */
class Mulberry32(seed: Int) {
    private var a: Int = seed
    fun next(): Double {
        a += 0x6D2B79F5
        var t = (a xor (a ushr 15)) * (1 or a)
        t = (t + (t xor (t ushr 7)) * (61 or t)) xor t
        return (t xor (t ushr 14)).toUInt().toDouble() / 4294967296.0
    }
}

/** Estimator math: p̂ = inside/N, x = 4p̂, σ = 4·sqrt(p̂(1−p̂)/N), interval x ± 3σ (99.7%). */
object Estimator {
    fun estimate(inside: Long, n: Long): Double = 4.0 * inside / n
    fun sigma(inside: Long, n: Long): Double {
        val p = inside.toDouble() / n
        return 4.0 * sqrt(p * (1 - p) / n)
    }
    fun interval(inside: Long, n: Long): Pair<Double, Double> {
        val x = estimate(inside, n); val s = sigma(inside, n)
        return (x - 3 * s) to (x + 3 * s)
    }
}

/** Slider → parameter mappings, identical to readParams() in the HTML. */
object Params {
    const val TOTAL_MIN = 3f; const val TOTAL_MAX = 7f; const val TOTAL_STEP = 0.001f; const val TOTAL_DEFAULT = 6f
    const val RATE_MIN = 1f; const val RATE_MAX = 4.69897f; const val RATE_STEP = 0.001f; const val RATE_DEFAULT = 3f
    const val SIZE_MIN = 0.5f; const val SIZE_MAX = 4f; const val SIZE_STEP = 0.1f; const val SIZE_DEFAULT = 1.2f
    const val DRAW_MIN = 5000f; const val DRAW_MAX = 200000f; const val DRAW_STEP = 5000f; const val DRAW_DEFAULT = 60000f

    fun total(v: Float): Long {
        if (v >= 7f) return 10_000_000L
        val t = jsRound(10.0.pow(v.toDouble()) / 1000).toLong() * 1000
        return if (t == 0L) 1000L else t
    }
    fun rate(v: Float): Long {
        val r = 10.0.pow(v.toDouble())
        val q = if (r >= 1000) 100.0 else if (r >= 100) 10.0 else 1.0
        return max(10.0, jsRound(r / q) * q).toLong()
    }
}

/** en-US integer grouping (toLocaleString('en-US') of Math.round(n)). */
fun fmtI(n: Double): String {
    val v = jsRound(n).toLong()
    val s = abs(v).toString()
    val sb = StringBuilder()
    for (i in s.indices) {
        if (i > 0 && (s.length - i) % 3 == 0) sb.append(',')
        sb.append(s[i])
    }
    return if (v < 0) "-$sb" else sb.toString()
}
fun fmtI(n: Long): String = fmtI(n.toDouble())

/** Number.prototype.toFixed equivalent for display. */
fun toFixed(x: Double, dec: Int): String {
    if (x.isNaN()) return "NaN"
    val m = 10.0.pow(dec)
    val r = jsRound(abs(x) * m).toLong()
    val neg = x < 0 && r != 0L
    val ip = r / m.toLong(); val fp = r % m.toLong()
    val frac = if (dec > 0) "." + fp.toString().padStart(dec, '0') else ""
    return (if (neg) "-" else "") + ip + frac
}

enum class SimState { Idle, Run, Pause, Done }

data class HistPoint(val n: Long, val x: Double, val sigma: Double)

/**
 * Simulation engine (platform-independent). Mirrors the HTML state machine:
 * idle → run ⇄ pause → done; reset → idle.
 * Drawn points are stored (x, y, inside) up to the drawing cap so the UI can draw
 * them incrementally into a bitmap layer and redraw it on resize / point-size change.
 */
class MonteCarloEngine {
    var state by mutableStateOf(SimState.Idle); private set
    /** Bumped every frame / state change so Compose re-reads the plain fields below. */
    var frame by mutableStateOf(0L); private set
    /** Bumped at most every 100 ms (or on pause/reset/done) to re-render the chart. */
    var chartTick by mutableStateOf(0L); private set

    var n = 0L; private set
    var inside = 0L; private set
    var drawn = 0; private set
    var speed = 0.0; private set
    val hist = ArrayList<HistPoint>()
    private var nextHist = 10L

    // stored drawn points
    var storedX = FloatArray(1024); private set
    var storedY = FloatArray(1024); private set
    var storedIn = BooleanArray(1024); private set
    var storedCount = 0; private set
    /** Incremented on reset so the UI knows to clear its layer. */
    var generation = 0; private set

    private var rnd: () -> Double = { Random.nextDouble() }
    private var spdN = 0L; private var spdT = 0.0; private var lastUI = 0.0

    var total = Params.total(Params.TOTAL_DEFAULT)
    var rate = Params.rate(Params.RATE_DEFAULT)
    var drawCap = Params.DRAW_DEFAULT.toInt()

    private fun store(x: Double, y: Double, inn: Boolean) {
        if (storedCount == storedX.size) {
            val ns = storedX.size * 2
            storedX = storedX.copyOf(ns); storedY = storedY.copyOf(ns); storedIn = storedIn.copyOf(ns)
        }
        storedX[storedCount] = x.toFloat(); storedY[storedCount] = y.toFloat(); storedIn[storedCount] = inn
        storedCount++
    }

    private fun record() {
        hist.add(HistPoint(n, Estimator.estimate(inside, n), Estimator.sigma(inside, n)))
    }

    /** One frame batch: min(rate, total−N) points. */
    fun step() {
        val k = min(rate, total - n)
        if (k <= 0) return
        val canDraw = max(0L, min(k, (drawCap - drawn).toLong())).toInt()
        var inc = 0L
        for (i in 0 until k) {
            val x = rnd(); val y = rnd()
            val inn = x * x + y * y <= 1
            if (inn) inc++
            if (i < canDraw) store(x, y, inn)
            if (n + i + 1 >= nextHist) {
                val nn = n + i + 1; val ins = inside + inc
                hist.add(HistPoint(nn, Estimator.estimate(ins, nn), Estimator.sigma(ins, nn)))
                nextHist = ceil(nextHist * 1.06).toLong()
            }
        }
        drawn += canDraw
        n += k; inside += inc
    }

    /** Animation-frame callback; [nowMs] is a monotonic timestamp in ms. */
    fun onFrame(nowMs: Double) {
        if (state != SimState.Run) return
        step()
        if (spdT == 0.0) { spdT = nowMs; spdN = n }
        if (nowMs - spdT >= 500) { speed = (n - spdN) / ((nowMs - spdT) / 1000); spdT = nowMs; spdN = n }
        if (n >= total) { record(); speed = 0.0; state = SimState.Done }
        frame++
        if (nowMs - lastUI > 100 || state == SimState.Done) { chartTick++; lastUI = nowMs }
    }

    /** [seed] is applied only when starting from idle: 0 → platform random, >0 → mulberry32. */
    fun start(seed: Int) {
        if (state == SimState.Run || state == SimState.Done) return
        if (state == SimState.Idle) {
            rnd = if (seed > 0) Mulberry32(seed).let { m -> { m.next() } } else ({ Random.nextDouble() })
        }
        spdT = 0.0
        state = SimState.Run
        frame++
    }

    fun pause() {
        if (state != SimState.Run) return
        speed = 0.0; state = SimState.Pause; frame++; chartTick++
    }

    fun reset() {
        n = 0; inside = 0; drawn = 0; hist.clear(); nextHist = 10; storedCount = 0; speed = 0.0
        generation++
        state = SimState.Idle; frame++; chartTick++
    }

    fun toggle(seed: Int) = if (state == SimState.Run) pause() else start(seed)

    fun touch() { frame++ }
    fun touchChart() { chartTick++ }

    companion object {
        /** Chart x range upper bound (log10). */
        fun chartXMax(total: Long, n: Long): Double = max(4.0, log10(max(total, n).toDouble()))
    }
}
