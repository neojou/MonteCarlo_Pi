package com.neojou.montecarlopi

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.neojou.montecarlopi.sim.*
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

// ---- palette (identical to the HTML :root variables) ----
object Pal {
    val bg = Color(0xFFFBF8FF); val surface = Color(0xFFF3EDF7); val card = Color.White
    val primary = Color(0xFF6750A4); val pc = Color(0xFFEADDFF); val onPc = Color(0xFF21005D)
    val onS = Color(0xFF1D1B20); val onSv = Color(0xFF49454F); val outline = Color(0xFFCAC4D0)
    val outlineV = Color(0xFFE7E0EC); val rose = Color(0xFFD0679D); val ok = Color(0xFF1E7F4F); val bad = Color(0xFFB3261E)
    val muted = Color(0xFF79747E); val dotIdle = Color(0xFF938F99)
    val pauseBg = Color(0xFFFFD8E8); val pauseFg = Color(0xFF5D1139)
    val inPt = Color(103, 80, 164, (0.85f * 255).roundToInt())
    val outPt = Color(208, 103, 157, (0.62f * 255).roundToInt())
}

private const val TRUE_PI = "真實 π = 3.14159265…"
private val Tnum = "tnum"

@Composable
private fun T(
    text: String, size: TextUnit, color: Color = Pal.onS, weight: FontWeight = FontWeight.Normal,
    modifier: Modifier = Modifier, num: Boolean = false, spacing: TextUnit = TextUnit.Unspecified,
    align: TextAlign? = null, maxLines: Int = Int.MAX_VALUE,
) = Text(
    text, modifier, color = color, fontSize = size, fontWeight = weight, letterSpacing = spacing,
    textAlign = align, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
    style = if (num) androidx.compose.material3.LocalTextStyle.current.copy(fontFeatureSettings = Tnum) else androidx.compose.material3.LocalTextStyle.current,
)

/**
 * The Monte Carlo π dashboard (port of index.html).
 * @param demo when true, seed = 42 and the simulation auto-starts (like `?demo=1`).
 */
@Composable
fun Dashboard(demo: Boolean = false, modifier: Modifier = Modifier, demoPauseAt: Long = 0L) {
    val engine = remember { MonteCarloEngine() }
    var sTotal by remember { mutableStateOf(Params.TOTAL_DEFAULT) }
    var sRate by remember { mutableStateOf(Params.RATE_DEFAULT) }
    var sSize by remember { mutableStateOf(Params.SIZE_DEFAULT) }
    var sizeCommitted by remember { mutableStateOf(Params.SIZE_DEFAULT) }
    var sDraw by remember { mutableStateOf(Params.DRAW_DEFAULT) }
    var seedText by remember { mutableStateOf(if (demo) "42" else "0") }
    var seedFocused by remember { mutableStateOf(false) }
    val rootFocus = remember { FocusRequester() }

    engine.total = Params.total(sTotal)
    engine.rate = Params.rate(sRate)
    engine.drawCap = sDraw.toInt()
    val seed = { seedText.toIntOrNull() ?: 0 }

    // per-frame batches
    val state = engine.state
    LaunchedEffect(state) {
        if (state == SimState.Run) {
            while (engine.state == SimState.Run) {
                withFrameNanos { t -> engine.onFrame(t / 1_000_000.0) }
                // screenshot helper only (desktop env MONTECARLO_DEMO_PAUSE_AT); 0 = off
                if (demoPauseAt > 0 && engine.n >= demoPauseAt) engine.pause()
            }
        }
    }
    LaunchedEffect(Unit) {
        rootFocus.requestFocus()
        if (demo) engine.start(42)
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Pal.bg)
            .focusRequester(rootFocus)
            .focusable()
            .onPreviewKeyEvent { e ->
                if (seedFocused || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.Spacebar -> { engine.toggle(seed()); true }
                    Key.R -> { engine.reset(); true }
                    else -> false
                }
            }
            .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(pass = androidx.compose.ui.input.pointer.PointerEventPass.Final); rootFocus.requestFocus() } }
    ) {
        val narrow = maxWidth <= 1000.dp
        val tiny = maxWidth <= 560.dp
        val viewportH = maxHeight
        Column(Modifier.fillMaxSize()) {
            Header(engine.state, narrow, tiny)
            Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                val pad = if (narrow) PaddingValues(16.dp) else PaddingValues(start = 28.dp, end = 28.dp, top = 16.dp, bottom = 24.dp)
                Box(Modifier.fillMaxWidth().padding(pad), contentAlignment = Alignment.TopCenter) {
                    val left: @Composable (Modifier) -> Unit = { m ->
                        LeftColumn(engine, sizeCommitted, narrow, tiny, viewportH, m)
                    }
                    val right: @Composable (Modifier) -> Unit = { m ->
                        Column(m, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            ParamsCard(
                                engine, sTotal, { sTotal = it; engine.touch(); engine.touchChart() },
                                sRate, { sRate = it },
                                sSize, { sSize = it }, { sizeCommitted = sSize },
                                sDraw, { sDraw = it; engine.touch() },
                                seedText, { seedText = it }, { seedFocused = it }, seed,
                            )
                            Kpis(engine, tiny)
                            ChartCard(engine)
                        }
                    }
                    if (narrow) {
                        Column(Modifier.widthIn(max = 1600.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            left(Modifier.fillMaxWidth()); right(Modifier.fillMaxWidth())
                        }
                    } else {
                        Row(Modifier.widthIn(max = 1600.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            left(Modifier.weight(62f)); right(Modifier.weight(38f))
                        }
                    }
                }
            }
        }
    }
}

// ---------------- header ----------------
@Composable
private fun Header(state: SimState, narrow: Boolean, tiny: Boolean) {
    Row(
        Modifier.fillMaxWidth().background(Pal.bg.copy(alpha = 0.86f))
            .drawBehind1px(Pal.outlineV)
            .padding(horizontal = if (narrow) 16.dp else 28.dp, vertical = if (narrow) 12.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Pal.pc), contentAlignment = Alignment.Center) {
            T("π", 22.sp, Pal.onPc, FontWeight.Bold)
        }
        Column(Modifier.weight(1f)) {
            T("蒙地卡羅 π 估算", 20.sp, Pal.onS, FontWeight.SemiBold, spacing = 0.02.em)
            if (!tiny) T("隨機投點於單位正方形，以落入四分之一圓的比例估算 π · 99.7% 信賴區間（±3σ）", 12.5.sp, Pal.onSv, modifier = Modifier.padding(top = 2.dp))
        }
        StatusPill(state)
    }
}

private fun Modifier.drawBehind1px(c: Color) = this.drawBehind {
    drawLine(c, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1.dp.toPx())
}

@Composable
private fun StatusPill(state: SimState) {
    val (bg, fg, border, dot) = when (state) {
        SimState.Run -> listOf(Pal.pc, Pal.onPc, Color.Transparent, Pal.primary)
        SimState.Pause -> listOf(Pal.pauseBg, Pal.pauseFg, Color.Transparent, Pal.rose)
        else -> listOf(Pal.card, Pal.onSv, Pal.outline, Pal.dotIdle)
    }
    val label = when (state) { SimState.Run -> "執行中"; SimState.Pause -> "已暫停"; SimState.Done -> "已完成"; else -> "待命" }
    val pulse = rememberInfiniteTransition()
    val f by pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart))
    Row(
        Modifier.clip(CircleShape).background(bg).border(1.dp, border, CircleShape).padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Canvas(Modifier.size(8.dp)) {
            if (state == SimState.Run) {
                val g = min(f / 0.7f, 1f)
                drawCircle(Color(103, 80, 164).copy(alpha = 0.55f * (1 - g)), radius = size.minDimension / 2 + 8.dp.toPx() * g)
            }
            drawCircle(dot)
        }
        T(label, 13.sp, fg, FontWeight.SemiBold)
    }
}

// ---------------- cards / common ----------------
private val CardShape = RoundedCornerShape(20.dp)

@Composable
private fun CardBox(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.shadow(3.dp, CardShape, ambientColor = Pal.primary.copy(alpha = 0.06f), spotColor = Pal.primary.copy(alpha = 0.10f))
            .clip(CardShape).background(Pal.card).border(1.dp, Pal.outlineV, CardShape).padding(18.dp),
        content = content,
    )
}

@Composable
private fun CardTitle(title: String, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        T(title, 13.sp, Pal.onSv, FontWeight.SemiBold, spacing = 0.06.em, modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically, content = trailing)
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        T(label, 12.sp, Pal.onSv, FontWeight.Medium)
    }
}

// ---------------- left column ----------------
@Composable
private fun LeftColumn(engine: MonteCarloEngine, pointSize: Float, narrow: Boolean, tiny: Boolean, viewportH: Dp, modifier: Modifier) {
    engine.frame // subscribe
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        CardBox(Modifier.fillMaxWidth()) {
            CardTitle("投點視覺化") {
                LegendItem(Pal.primary, "圓內")
                LegendItem(Pal.rose.copy(alpha = 0.6f), "圓外")
                T("已繪 ${fmtI(engine.drawn.toLong())}${if (engine.drawn >= engine.drawCap) "（達上限）" else ""}", 12.sp, Pal.onSv, FontWeight.Medium, num = true)
            }
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val cap = if (narrow) 640.dp else viewportH - 390.dp
                val w = max(280f, min(maxWidth.value, cap.value)).dp
                FieldCanvas(engine, pointSize, Modifier.size(w))
            }
            Box(Modifier.padding(top = 12.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Pal.pc)) {
                val frac = min(1f, engine.n.toFloat() / engine.total)
                Box(Modifier.fillMaxHeight().fillMaxWidth(frac).background(Pal.primary))
            }
        }
        ResultBox(engine, tiny)
    }
}

/** Accumulated point layer (ImageBitmap) + background/grid/quarter circle. */
private class PointLayer {
    var bitmap: ImageBitmap? = null
    var canvas: androidx.compose.ui.graphics.Canvas? = null
    var drawnUpTo = 0
    var generation = -1
    var sizeKey = -1f
    val inPaint = Paint().apply { color = Pal.inPt; isAntiAlias = true }
    val outPaint = Paint().apply { color = Pal.outPt; isAntiAlias = true }
}

@Composable
private fun FieldCanvas(engine: MonteCarloEngine, pointSize: Float, modifier: Modifier) {
    val layer = remember { PointLayer() }
    val dpr = LocalDensity.current.density
    Canvas(modifier.clip(RoundedCornerShape(14.dp)).background(Pal.bg)) {
        engine.frame // redraw each frame
        val fw = size.width.roundToInt().coerceAtLeast(1)
        val p = (14 * dpr).roundToInt().toFloat()
        val l = fw - 2 * p
        val s = max(0.5f, pointSize * dpr)
        var bmp = layer.bitmap
        if (bmp == null || bmp.width != fw || layer.generation != engine.generation || layer.sizeKey != s || engine.storedCount < layer.drawnUpTo) {
            bmp = ImageBitmap(fw, fw)
            layer.bitmap = bmp; layer.canvas = Canvas(bmp)
            layer.drawnUpTo = 0; layer.generation = engine.generation; layer.sizeKey = s
        }
        val c = layer.canvas!!
        val from = layer.drawnUpTo; val to = engine.storedCount
        if (to > from) {
            // out first, then in (same order as the HTML batch fill)
            for (pass in 0..1) {
                val paint = if (pass == 1) layer.inPaint else layer.outPaint
                for (i in from until to) {
                    if (engine.storedIn[i] != (pass == 1)) continue
                    val x = p + engine.storedX[i] * l - s / 2
                    val y = p + (1 - engine.storedY[i]) * l - s / 2
                    c.drawRect(x, y, x + s, y + s, paint)
                }
            }
            layer.drawnUpTo = to
        }
        // background square + grid
        drawRect(Color.White, Offset(p, p), Size(l, l))
        val gc = Color(202, 196, 208).copy(alpha = 0.45f)
        for (i in 1 until 10) {
            val g = p + l * i / 10
            drawLine(gc, Offset(g, p), Offset(g, p + l), dpr)
            drawLine(gc, Offset(p, g), Offset(p + l, g), dpr)
        }
        drawArc(Pal.pc.copy(alpha = 0.35f), -90f, 90f, true, Offset(p - l, p), Size(2 * l, 2 * l))
        drawImage(bmp)
        drawArc(Pal.primary, -90f, 90f, false, Offset(p - l, p), Size(2 * l, 2 * l), style = Stroke(2 * dpr))
        drawRect(Pal.muted, Offset(p, p), Size(l, l), style = Stroke(1.5f * dpr))
    }
}

@Composable
private fun ResultBox(engine: MonteCarloEngine, tiny: Boolean) {
    engine.frame // subscribe
    val n = engine.n
    val shape = RoundedCornerShape(20.dp)
    var lo = "—"; var est = "—"; var hi = "—"
    val hint: AnnotatedString
    if (n == 0L) {
        hint = AnnotatedString("$TRUE_PI 按「開始」或空白鍵開始投點")
    } else {
        val x = Estimator.estimate(engine.inside, n)
        val (l, h) = Estimator.interval(engine.inside, n)
        val dec = if (n < 10_000) 5 else 6
        est = toFixed(x, dec); lo = toFixed(l, dec); hi = toFixed(h, dec)
        val ok = PI >= l && PI <= h
        hint = buildAnnotatedString {
            append("$TRUE_PI 是否落在區間內 ")
            withStyle(SpanStyle(color = if (ok) Pal.ok else Pal.bad, fontWeight = FontWeight.Bold)) { append(if (ok) "✓ 是" else "✗ 否") }
        }
    }
    Column(
        Modifier.fillMaxWidth().clip(shape).background(Brush.verticalGradient(listOf(Color(0xFFF7F2FC), Pal.surface)))
            .border(1.dp, Pal.outlineV, shape).padding(start = 12.dp, end = 12.dp, top = 20.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val bSize = if (tiny) 17.sp else 22.sp
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally), itemVerticalAlignment = Alignment.Bottom) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                T(lo, bSize, Pal.onSv, FontWeight.Medium, Modifier.alignByBaseline(), num = true)
                T("≤", 22.sp, Pal.outline, modifier = Modifier.alignByBaseline())
                T(est, if (tiny) 34.sp else 44.sp, Pal.primary, FontWeight.Bold, Modifier.alignByBaseline(), num = true, spacing = (-0.01).em)
                T("≤", 22.sp, Pal.outline, modifier = Modifier.alignByBaseline())
                T(hi, bSize, Pal.onSv, FontWeight.Medium, Modifier.alignByBaseline(), num = true)
            }
            Row(Modifier.align(Alignment.Bottom).padding(bottom = if (tiny) 4.dp else 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    T("，機率 ", 18.sp, Pal.onSv, FontWeight.SemiBold, maxLines = 1)
                    Box(Modifier.clip(CircleShape).background(Pal.pc).padding(horizontal = 10.dp, vertical = 2.dp)) {
                        T("99.7%", 18.sp, Pal.onPc, FontWeight.SemiBold, maxLines = 1)
                    }
            }
        }
        Text(hint, Modifier.padding(top = 8.dp), color = Pal.onSv, fontSize = 13.sp,
            style = androidx.compose.material3.LocalTextStyle.current.copy(fontFeatureSettings = Tnum))
    }
}

// ---------------- right column ----------------
@Composable
private fun Ctl(label: String, value: String, ticks: Pair<String, String>, slider: @Composable () -> Unit) {
    Column(Modifier.padding(bottom = 8.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            T(label, 13.5.sp, Pal.onS, FontWeight.SemiBold, Modifier.weight(1f))
            Box(Modifier.clip(RoundedCornerShape(8.dp)).background(Pal.pc).padding(horizontal = 10.dp, vertical = 1.dp)) {
                T(value, 13.sp, Pal.primary, FontWeight.SemiBold, num = true)
            }
        }
        slider()
        Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
            T(ticks.first, 11.sp, Pal.muted, num = true, modifier = Modifier.weight(1f))
            T(ticks.second, 11.sp, Pal.muted, num = true)
        }
    }
}

/** Range input lookalike: 6dp track (primary up to value, pc after), 18dp primary thumb with 3dp white ring. */
@Composable
private fun LavSlider(value: Float, min: Float, max: Float, step: Float, onChange: (Float) -> Unit, onFinish: () -> Unit = {}) {
    val cur by rememberUpdatedState(value)
    val change by rememberUpdatedState(onChange)
    val finish by rememberUpdatedState(onFinish)
    val density = LocalDensity.current
    Canvas(
        Modifier.fillMaxWidth().height(22.dp).pointerInput(min, max, step) {
            val r = with(density) { 9.dp.toPx() }
            fun valueAt(x: Float): Float {
                val f = ((x - r) / (size.width - 2 * r)).coerceIn(0f, 1f)
                val raw = min + f * (max - min)
                val snapped = min + ((raw - min) / step).roundToInt() * step
                return snapped.coerceIn(min, max)
            }
            awaitEachGesture {
                val down = awaitFirstDown()
                var v = valueAt(down.position.x); if (v != cur) change(v)
                down.consume()
                while (true) {
                    val ev = awaitPointerEvent()
                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                    if (!ch.pressed) break
                    if (ch.positionChange() != Offset.Zero) { v = valueAt(ch.position.x); if (v != cur) change(v); ch.consume() }
                }
                finish()
            }
        }
    ) {
        val frac = ((value - min) / (max - min)).coerceIn(0f, 1f)
        val th = 6.dp.toPx(); val cy = size.height / 2
        val split = size.width * frac
        drawRoundRect(Pal.pc, Offset(0f, cy - th / 2), Size(size.width, th), androidx.compose.ui.geometry.CornerRadius(th / 2))
        drawRoundRect(Pal.primary, Offset(0f, cy - th / 2), Size(split, th), androidx.compose.ui.geometry.CornerRadius(th / 2))
        val r = 9.dp.toPx()
        val cx = r + frac * (size.width - 2 * r)
        drawCircle(Color.Black.copy(alpha = 0.18f), r + 0.8f, Offset(cx, cy + 1.dp.toPx()))
        drawCircle(Color.White, r, Offset(cx, cy))
        drawCircle(Pal.primary, r - 3.dp.toPx(), Offset(cx, cy))
    }
}

@Composable
private fun PillButton(text: String, enabled: Boolean, kind: Int, onClick: () -> Unit, modifier: Modifier) {
    val (bg, fg) = when (kind) { 0 -> Pal.primary to Color.White; 1 -> Pal.pc to Pal.onPc; else -> Color.Transparent to Pal.primary }
    Box(
        modifier.height(42.dp).alpha(if (enabled) 1f else 0.38f)
            .then(if (kind == 0 && enabled) Modifier.shadow(2.dp, CircleShape, spotColor = Pal.primary.copy(alpha = 0.35f)) else Modifier)
            .clip(CircleShape).background(bg)
            .then(if (kind == 2) Modifier.border(1.dp, Pal.outline, CircleShape) else Modifier)
            .clickable(enabled = enabled, interactionSource = remember { MutableInteractionSource() }, indication = androidx.compose.material3.ripple(), onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { T(text, 14.sp, fg, FontWeight.SemiBold) }
}

@Composable
private fun Kbd(text: String) {
    Box(Modifier.clip(RoundedCornerShape(5.dp)).background(Pal.bg).border(1.dp, Pal.outline, RoundedCornerShape(5.dp)).padding(horizontal = 5.dp)) {
        T(text, 11.5.sp, Pal.muted)
    }
}

@Composable
private fun ParamsCard(
    engine: MonteCarloEngine,
    sTotal: Float, onTotal: (Float) -> Unit,
    sRate: Float, onRate: (Float) -> Unit,
    sSize: Float, onSize: (Float) -> Unit, onSizeDone: () -> Unit,
    sDraw: Float, onDraw: (Float) -> Unit,
    seedText: String, onSeed: (String) -> Unit, onSeedFocus: (Boolean) -> Unit, seed: () -> Int,
) {
    val state = engine.state
    CardBox(Modifier.fillMaxWidth()) {
        CardTitle("參數設定")
        Ctl("總點數上限", fmtI(Params.total(sTotal)), "1,000" to "10,000,000") {
            LavSlider(sTotal, Params.TOTAL_MIN, Params.TOTAL_MAX, Params.TOTAL_STEP, onTotal)
        }
        Ctl("每幀點數 / 速度", fmtI(Params.rate(sRate)) + " /幀", "10" to "50,000") {
            LavSlider(sRate, Params.RATE_MIN, Params.RATE_MAX, Params.RATE_STEP, onRate)
        }
        Ctl("點大小", toFixed(sSize.toDouble(), 1) + " px", "0.5" to "4") {
            LavSlider(sSize, Params.SIZE_MIN, Params.SIZE_MAX, Params.SIZE_STEP, onSize, onSizeDone)
        }
        Ctl("繪製上限", fmtI(sDraw.toLong()), "5,000" to "200,000") {
            LavSlider(sDraw, Params.DRAW_MIN, Params.DRAW_MAX, Params.DRAW_STEP, onDraw)
        }
        Column {
            Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                T("亂數種子", 13.5.sp, Pal.onS, FontWeight.SemiBold, Modifier.weight(1f))
                T("0 = Math.random · 重設後生效", 11.5.sp, Pal.muted)
            }
            var focused by remember { mutableStateOf(false) }
            val shape = RoundedCornerShape(10.dp)
            BasicTextField(
                seedText,
                { t -> onSeed(t.filter { it.isDigit() }.take(9)) },
                Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused; onSeedFocus(it.isFocused) }
                    .clip(shape).background(Pal.bg)
                    .border(if (focused) 2.dp else 1.dp, if (focused) Pal.primary else Pal.outline, shape)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = androidx.compose.material3.LocalTextStyle.current.copy(fontSize = 16.sp, color = Pal.onS, fontFeatureSettings = Tnum),
                cursorBrush = SolidColor(Pal.primary),
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton(if (state == SimState.Pause) "繼續 Start" else "開始 Start", state != SimState.Run && state != SimState.Done, 0,
                { engine.start(seed()) }, Modifier.weight(1f))
            PillButton("暫停 Pause", state == SimState.Run, 1, { engine.pause() }, Modifier.weight(1f))
            PillButton("重設 Reset", state != SimState.Idle, 2, { engine.reset() }, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Kbd("Space"); T(" 開始／暫停　", 11.5.sp, Pal.muted); Kbd("R"); T(" 重設", 11.5.sp, Pal.muted)
        }
    }
}

@Composable
private fun Kpis(engine: MonteCarloEngine, tiny: Boolean) {
    engine.frame
    val n = engine.n
    val items = if (n == 0L) listOf("估計值" to "—", "已投點數 N" to "0", "圓內點數" to "0", "σ" to "—", "誤差 |x−π|" to "—", "速度 points/s" to fmtI(engine.speed))
    else {
        val x = Estimator.estimate(engine.inside, n)
        listOf(
            "估計值" to toFixed(x, 6), "已投點數 N" to fmtI(n), "圓內點數" to fmtI(engine.inside),
            "σ" to toFixed(Estimator.sigma(engine.inside, n), 6), "誤差 |x−π|" to toFixed(abs(x - PI), 6),
            "速度 points/s" to fmtI(engine.speed),
        )
    }
    val cols = if (tiny) 2 else 3
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.chunked(cols).forEachIndexed { ri, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEachIndexed { ci, (k, v) ->
                    val hl = ri == 0 && ci == 0
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(if (hl) Pal.pc else Pal.surface).padding(horizontal = 12.dp, vertical = 10.dp)) {
                        T(k, 11.5.sp, Pal.onSv, FontWeight.Medium, maxLines = 1)
                        T(v, 17.sp, if (hl) Pal.onPc else Pal.onS, FontWeight.Bold, Modifier.padding(top = 3.dp), num = true, maxLines = 1)
                    }
                }
                repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

// ---------------- convergence chart ----------------
@Composable
private fun ChartCard(engine: MonteCarloEngine) {
    CardBox(Modifier.fillMaxWidth()) {
        CardTitle("收斂走勢（估計值 vs N，對數軸）") {
            LegendItem(Pal.primary, "估計"); LegendItem(Pal.pc, "±3σ")
        }
        val tm = rememberTextMeasurer()
        val style = androidx.compose.material3.LocalTextStyle.current.copy(fontSize = 11.sp, color = Pal.muted, fontFeatureSettings = Tnum)
        val piStyle = style.copy(color = Pal.rose)
        Canvas(Modifier.fillMaxWidth().height(180.dp).clipToBounds()) {
            engine.chartTick
            drawChart(engine, tm, style, piStyle)
        }
    }
}

private val SUP = arrayOf("⁰", "¹", "²", "³", "⁴", "⁵", "⁶", "⁷", "⁸")

private fun DrawScope.drawChart(engine: MonteCarloEngine, tm: TextMeasurer, style: TextStyle, piStyle: TextStyle) {
    val d = density
    val w = size.width; val h = size.height
    val l = 52 * d; val rr = 10 * d; val t = 10 * d; val b = 24 * d
    val pw = w - l - rr; val ph = h - t - b
    val xmax = MonteCarloEngine.chartXMax(engine.total, engine.n); val xmin = 1.0
    var ymin = PI - 0.25; var ymax = PI + 0.25
    val hist = engine.hist
    val vis = hist.filter { it.n >= 100 }
    if (vis.isNotEmpty()) {
        var s = 0.0
        for (hp in vis) s = max(s, abs(hp.x - PI) + hp.sigma * 3)
        s = min(max(s * 1.1, 0.002), 1.2); ymin = PI - s; ymax = PI + s
    }
    fun X(n: Double) = (l + (log10(n) - xmin) / (xmax - xmin) * pw).toFloat()
    fun Y(v: Double) = (t + (1 - (v - ymin) / (ymax - ymin)) * ph).toFloat()
    val grid = Color(202, 196, 208).copy(alpha = 0.5f)
    for (i in 0..4) {
        val v = ymin + (ymax - ymin) * i / 4; val y = Y(v)
        drawLine(grid, Offset(l, y), Offset(l + pw, y), d)
        val lay = tm.measure(toFixed(v, if (ymax - ymin < 0.02) 4 else 3), style)
        drawText(lay, topLeft = Offset(l - 6 * d - lay.size.width, y - lay.size.height / 2f))
    }
    for (e in 1..floor(xmax).toInt()) {
        val x = X(10.0.pow(e))
        drawLine(grid, Offset(x, t), Offset(x, t + ph), d)
        val lay = tm.measure("10" + SUP.getOrElse(e) { "" }, style)
        drawText(lay, topLeft = Offset(x - lay.size.width / 2f, t + ph + 6 * d))
    }
    val hs = hist.filter { it.n >= 10 }
    clipRect(l, t, l + pw, t + ph) {
        if (hs.size > 1) {
            val band = Path()
            hs.forEachIndexed { i, hp -> val x = X(hp.n.toDouble()); val y = Y(hp.x + 3 * hp.sigma); if (i == 0) band.moveTo(x, y) else band.lineTo(x, y) }
            for (i in hs.indices.reversed()) { val hp = hs[i]; band.lineTo(X(hp.n.toDouble()), Y(hp.x - 3 * hp.sigma)) }
            band.close()
            drawPath(band, Pal.pc.copy(alpha = 0.75f))
        }
        drawLine(Pal.rose, Offset(l, Y(PI)), Offset(l + pw, Y(PI)), 1.3f * d, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5 * d, 4 * d)))
        if (hs.size > 1) {
            val line = Path()
            hs.forEachIndexed { i, hp -> val x = X(hp.n.toDouble()); val y = Y(hp.x); if (i == 0) line.moveTo(x, y) else line.lineTo(x, y) }
            drawPath(line, Pal.primary, style = Stroke(1.8f * d, join = StrokeJoin.Round))
            val last = hs.last()
            drawCircle(Pal.primary, 3.5f * d, Offset(X(last.n.toDouble()), Y(last.x)))
        }
    }
    val pl = tm.measure("π", piStyle)
    drawText(pl, topLeft = Offset(l + pw - 4 * d - pl.size.width, Y(PI) - 3 * d - pl.size.height))
}
