package com.hpprint.app.data.scan.core

import android.graphics.Bitmap
import android.graphics.PointF
import android.util.Log
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 证件（身份证/银行卡这类白色卡片）专用的四角检测。
 *
 * 通用文档检测（[DocDetector]）取的是边缘图谱凸包上 x±y 的极值点，
 * 对证件这种小卡片会偏一两个点、残留零点几度的倾斜。这里换一条路：
 *
 * 1. 在缩略图上用 Otsu 把「亮卡片」和「暗背景」分开，取最大亮区；
 * 2. 凸包 → 最小外接矩形定方向 → 把凸包点按四条边分组；
 * 3. 每条边做总体最小二乘直线拟合，相邻直线求交得到四个角（角是「算」出来的，不是「挑」出来的）；
 * 4. 回到原图，沿四条边逐点扫描亮度梯度、吸附到真实边缘，再拟合一次，做两轮（粗 → 细）。
 *
 * 结果是一个四边都落在真实卡片边缘上的四边形，透视矫正后不会歪。
 */
object CardDetector {

    private const val WORK_MAX = 640
    private const val REFINE_MAX = 1200
    private const val MIN_EDGE_FRAC = 0.12f

    /** 身份证 85.6 : 54，评分时作为长宽比先验 */
    private const val ID_ASPECT = 1.585f

    /** 粗检测评分低于这个值就说明「没找到像卡片的东西」，展开分位数扫描再试一轮 */
    private const val WEAK_SCORE = 150f

    /**
     * 检测证件四角；识别不到返回 null。
     *
     * @param workMax 粗检测的工作分辨率长边
     * @param seed    取景框（原图坐标）。白卡放白桌这类几乎零对比的场景，全局阈值会失效，
     *                但用户已经把卡对进框里了，于是从框出发做一次低门限的边缘吸附，
     *                只有比框更贴边才采纳 —— 这就是「有先验就用先验」。
     */
    fun detect(
        bitmap: Bitmap,
        workMax: Int = WORK_MAX,
        refine: Boolean = true,
        seed: Quad? = null
    ): Quad? {
        val bw = bitmap.width
        val bh = bitmap.height
        if (bw < 64 || bh < 64) return null

        val k = workMax.toFloat() / max(bw, bh)
        val w = max(64, (bw * k).toInt())
        val h = max(64, (bh * k).toInt())
        val small = Bitmap.createScaledBitmap(bitmap, w, h, true)
        val gray = grayOf(small, w, h)
        if (small !== bitmap) small.recycle()

        val sx = bw.toFloat() / w
        val sy = bh.toFloat() / h

        var best: Quad? = null
        var bestScore = Float.NEGATIVE_INFINITY
        var from: String = "none"

        fun consider(q: Quad, bonus: Float, tag: String) {
            val s = score(gray, w, h, q) * bonus
            if (s > bestScore) {
                bestScore = s
                best = q
                from = tag
            }
        }

        quadFromGray(gray, w, h, deep = refine)?.let { consider(it, 1f, "global") }

        if (seed != null) {
            val seedWork = seed.scaled(1f / sx, 1f / sy).clampTo(w.toFloat(), h.toFloat())
            // 框本身就是一个候选：用户已经对齐过，吸附只在「更贴边」时才覆盖它
            consider(seedWork, 1f, "seed")
            quadFromSeed(gray, w, h, seedWork)?.let { consider(it, 1.02f, "seeded") }
        }

        val rough = best
        if (rough == null || bestScore <= 0f) {
            Log.d(TAG, "detect=none score=${bestScore.toInt()}")
            return null
        }

        val inSource = rough.scaled(sx, sy).clampTo(bw.toFloat(), bh.toFloat())

        // 之前出现过「上下边被判反」导致输出整张倒过来的情况，最后统一按几何整理一次四角顺序
        val refined = if (refine) refine(bitmap, inSource) else inSource
        if (refine) Log.d(TAG, "detect from=$from score=${bestScore.toInt()}")
        return canonical(refined)
    }

    private const val TAG = "CardDetect"

    /**
     * 把四条边按「外法线朝向」归位成 上/右/下/左。
     * 最小外接矩形的 u/v 轴方向取决于凸包遍历方向，直接用它分边会把上下判反。
     */
    private fun orderLines(raw: Array<FloatArray>, hull: List<PointF>): Array<FloatArray>? {
        var cx = 0f
        var cy = 0f
        for (p in hull) {
            cx += p.x
            cy += p.y
        }
        cx /= hull.size
        cy /= hull.size

        val anchors = floatArrayOf(270f, 0f, 90f, 180f)
        val slots = arrayOfNulls<FloatArray>(4)
        for (l in raw) {
            var nx = -l[3]
            var ny = l[2]
            if ((cx - l[0]) * nx + (cy - l[1]) * ny > 0f) {
                nx = -nx
                ny = -ny
            }
            var ang = Math.toDegrees(atan2(ny, nx).toDouble()).toFloat()
            if (ang < 0f) ang += 360f
            var best = -1
            var bestD = 360f
            for (i in anchors.indices) {
                var d = abs(ang - anchors[i])
                if (d > 180f) d = 360f - d
                if (d < bestD) {
                    bestD = d
                    best = i
                }
            }
            if (best < 0 || bestD > 50f) return null
            if (slots[best] != null) return null
            slots[best] = l
        }
        val out = arrayOfNulls<FloatArray>(4)
        for (i in 0..3) out[i] = slots[i] ?: return null
        @Suppress("UNCHECKED_CAST")
        return out as Array<FloatArray>
    }

    /** 规整成「左上 → 右上 → 右下 → 左下」的顺时针顺序 */
    private fun canonical(q: Quad): Quad {
        val p = q.points()
        var s = 0f
        for (i in 0..3) {
            val a = p[i]
            val b = p[(i + 1) % 4]
            s += a.x * b.y - b.x * a.y
        }
        val list = if (s < 0f) p.reversed() else p.toList()
        var k = 0
        var best = Float.MAX_VALUE
        for (i in 0..3) {
            val v = list[i].x + list[i].y
            if (v < best) {
                best = v
                k = i
            }
        }
        return Quad(list[k], list[(k + 1) % 4], list[(k + 2) % 4], list[(k + 3) % 4])
    }

    // ---------------------------------------------------------------- 第 1 步：亮区分割 + 四边拟合

    private fun quadFromGray(gray: IntArray, w: Int, h: Int, deep: Boolean): Quad? {
        val blurred = boxBlur(gray, w, h, 2)

        // 单一 Otsu 阈值在「浅色桌面 + 白卡片」这种低对比场景会切错，
        // 所以多试几个阈值、两种极性，再按「边的梯度强度 × 内外亮度差」挑最像卡片的那块。
        val otsu = otsu(blurred)
        val border = borderMedian(blurred, w, h)
        val high = percentile(blurred, 0.90f)
        val quick = floatArrayOf(
            otsu.toFloat(),
            (otsu + 12).toFloat(),
            (otsu - 12).toFloat(),
            (border + high) / 2f
        )

        var best = sweep(blurred, w, h, quick)
        // 预览帧只要快；拍下来这一张才值得多扫几遍
        if (deep && (best == null || best.second < WEAK_SCORE)) {
            // 白卡放白桌时卡片和桌面只差几个灰阶，Otsu 会落在卡内文字上。
            // 这时沿直方图整段扫一遍，总有一个阈值正好把卡片切出来。
            val pcts = floatArrayOf(0.08f, 0.16f, 0.25f, 0.36f, 0.48f, 0.60f, 0.72f, 0.84f, 0.93f)
            val wide = FloatArray(pcts.size) { percentile(blurred, pcts[it]) }
            val second = sweep(blurred, w, h, wide)
            if (second != null && (best == null || second.second > best.second)) best = second
        }
        return best?.first
    }

    /** 一组阈值 × 两种极性，各取最大连通域当候选，返回评分最高的一块 */
    private fun sweep(gray: IntArray, w: Int, h: Int, thresholds: FloatArray): Pair<Quad, Float>? {
        var best: Quad? = null
        var bestScore = -1f
        val ws = Workspace(gray.size)
        var lastThr = -1
        for (raw in thresholds) {
            val thr = raw.toInt().coerceIn(1, 254)
            if (thr == lastThr) continue
            lastThr = thr
            for (bright in booleanArrayOf(true, false)) {
                val comp = largestRegion(gray, w, h, thr, bright, ws) ?: continue
                val ratio = comp.count.toFloat() / (w * h)
                if (ratio < 0.08f || ratio > 0.95f) continue

                val hull = convexHull(comp.xs, comp.ys, comp.count)
                if (hull.size < 4) continue
                val quad = quadFromHull(hull) ?: continue
                if (!plausible(quad, w.toFloat(), h.toFloat())) continue

                val s = score(gray, w, h, quad)
                if (s > bestScore) {
                    bestScore = s
                    best = quad
                }
            }
        }
        val q = best ?: return null
        return Pair(q, bestScore)
    }

    /**
     * 从种子框出发做低门限边缘吸附。
     *
     * 用于「白卡放白桌」：全局阈值切不出卡片，但用户已经把卡对进取景框了，
     * 只需在框附近 ±5% 范围内找那圈几乎看不出来的边。吸附后必须比种子框更贴边，
     * 否则不采纳（避免被卡内文字或印刷边框带跑）。
     */
    private fun quadFromSeed(gray: IntArray, w: Int, h: Int, seed: Quad): Quad? {
        val seedScore = score(gray, w, h, seed)
        val short = min(w, h).toFloat()
        val radii = floatArrayOf(short * 0.05f, short * 0.025f, short * 0.012f)
        val gates = floatArrayOf(-2.5f, -3.5f, -6f)

        var q = seed
        var moved = false
        for (i in radii.indices) {
            val lines = Array<FloatArray?>(4) { e -> snapEdge(gray, w, h, q, e, radii[i], gates[i]) }
            if (lines.any { it == null }) continue
            val cand = cornersFromLines(lines.map { it!! }.toTypedArray()) ?: continue
            if (!plausible(cand, w.toFloat(), h.toFloat())) continue
            val before = score(gray, w, h, q)
            val after = score(gray, w, h, cand)
            if (after < before) continue
            if (!nearSeed(cand, seed, short)) continue
            q = cand
            moved = true
        }
        if (!moved) return null
        return if (score(gray, w, h, q) > seedScore) q else null
    }

    /** 吸附结果不能离种子框太远，长度也不能差太多 —— 否则吸到的是卡内文字或背景纹理 */
    private fun nearSeed(q: Quad, seed: Quad, short: Float): Boolean {
        val a = q.points()
        val b = seed.points()
        var ax = 0f
        var ay = 0f
        var bx = 0f
        var by = 0f
        for (i in 0..3) {
            ax += a[i].x
            ay += a[i].y
            bx += b[i].x
            by += b[i].y
        }
        ax /= 4f
        ay /= 4f
        bx /= 4f
        by /= 4f
        if (sqrt((ax - bx) * (ax - bx) + (ay - by) * (ay - by)) > short * 0.35f) return false

        val ea = q.edgeLengths()
        val eb = seed.edgeLengths()
        for (i in 0..3) {
            if (eb[i] < 1f) continue
            val r = ea[i] / eb[i]
            if (r < 0.55f || r > 1.75f) return false
        }
        return true
    }

    /**
     * 候选评分：四条边上梯度强度 × 内亮外暗的亮度差。
     * 卡片必然「里有字、边是亮的，外面是桌面」；把桌面一起框进来的假框，边落在空桌面上，梯度接近 0。
     * 再乘一个长宽比先验（越接近 1.585 越好），低对比时用来在几个弱候选里挑出最像证件的那个。
     */
    private fun score(gray: IntArray, w: Int, h: Int, q: Quad, minContrast: Float = 1.0f): Float {
        val pts = q.points()
        var cx = 0f
        var cy = 0f
        for (p in pts) {
            cx += p.x
            cy += p.y
        }
        cx /= 4f
        cy /= 4f

        var edgeSum = 0f
        var contrastSum = 0f
        for (e in 0..3) {
            val a = pts[e]
            val b = pts[(e + 1) % 4]
            var nx = -(b.y - a.y)
            var ny = b.x - a.x
            val len = sqrt(nx * nx + ny * ny)
            if (len < 1e-3f) return -1f
            nx /= len
            ny /= len
            val mx = (a.x + b.x) / 2f
            val my = (a.y + b.y) / 2f
            if ((mx - cx) * nx + (my - cy) * ny < 0f) {
                nx = -nx
                ny = -ny
            }

            var bestGrad = 0f
            var inside = 0f
            var outside = 0f
            var samples = 0
            for (i in 1..11) {
                val t = i / 12f
                val px = a.x + (b.x - a.x) * t
                val py = a.y + (b.y - a.y) * t
                inside += lumAt(gray, w, h, px - nx * 4f, py - ny * 4f)
                outside += lumAt(gray, w, h, px + nx * 4f, py + ny * 4f)
                samples++
                for (k in -3..3) {
                    val g = abs(
                        lumAt(gray, w, h, px + nx * (k + 1), py + ny * (k + 1)) -
                            lumAt(gray, w, h, px + nx * (k - 1), py + ny * (k - 1))
                    )
                    if (g > bestGrad) bestGrad = g
                }
            }
            if (samples == 0) return -1f
            edgeSum += bestGrad
            contrastSum += (inside - outside) / samples
        }
        val contrast = contrastSum / 4f
        if (contrast < minContrast) return -1f

        val e = q.edgeLengths()
        val ww = (e[0] + e[2]) / 2f
        val hh = (e[1] + e[3]) / 2f
        val ratio = if (ww > hh) ww / hh else hh / ww
        val shape = 1f / (1f + abs(ratio - ID_ASPECT))
        return (edgeSum / 4f) * min(contrast, 90f) * shape
    }

    private fun borderMedian(gray: IntArray, w: Int, h: Int): Float {
        val vals = ArrayList<Int>(2 * (w + h))
        for (x in 0 until w) {
            vals.add(gray[x])
            vals.add(gray[(h - 1) * w + x])
        }
        for (y in 0 until h) {
            vals.add(gray[y * w])
            vals.add(gray[y * w + w - 1])
        }
        vals.sort()
        return vals[vals.size / 2].toFloat()
    }

    private fun percentile(gray: IntArray, p: Float): Float {
        val hist = IntArray(256)
        for (v in gray) hist[v.coerceIn(0, 255)]++
        val target = (gray.size * p).toInt()
        var acc = 0
        for (i in 0..255) {
            acc += hist[i]
            if (acc >= target) return i.toFloat()
        }
        return 255f
    }

    /** 凸包 → 最小外接矩形定向 → 四条边各自拟合 → 相邻求交 */
    private fun quadFromHull(hull: List<PointF>): Quad? {
        val rect = minAreaRect(hull) ?: return null
        val ux = rect[0]
        val uy = rect[1]
        val vx = -uy
        val vy = ux
        val ox = rect[2]
        val oy = rect[3]
        val uMin = rect[4]
        val uMax = rect[5]
        val vMin = rect[6]
        val vMax = rect[7]

        val shortSide = min(uMax - uMin, vMax - vMin)
        val tol = shortSide * 0.20f
        if (shortSide < 8f) return null

        val sideTop = ArrayList<PointF>()
        val sideBottom = ArrayList<PointF>()
        val sideLeft = ArrayList<PointF>()
        val sideRight = ArrayList<PointF>()

        for (p in hull) {
            val dx = p.x - ox
            val dy = p.y - oy
            val u = dx * ux + dy * uy
            val v = dx * vx + dy * vy
            val dTop = abs(v - vMin)
            val dBottom = abs(vMax - v)
            val dLeft = abs(u - uMin)
            val dRight = abs(uMax - u)
            var m = dTop
            var which = 0
            if (dBottom < m) { m = dBottom; which = 1 }
            if (dLeft < m) { m = dLeft; which = 2 }
            if (dRight < m) { m = dRight; which = 3 }
            if (m > tol) continue
            when (which) {
                0 -> sideTop.add(p)
                1 -> sideBottom.add(p)
                2 -> sideLeft.add(p)
                else -> sideRight.add(p)
            }
        }

        val fitted = ArrayList<FloatArray>(4)
        for (set in arrayOf(sideTop, sideRight, sideBottom, sideLeft)) {
            fitted.add(fitLine(set) ?: return null)
        }
        // 按外法线方向重新归位，避免上下边判反
        val ordered = orderLines(fitted.toTypedArray(), hull) ?: return null
        return cornersFromLines(ordered)
    }

    /** 依次传入 top / right / bottom / left 四条直线，求四个角 */
    private fun cornersFromLines(lines: Array<FloatArray>): Quad? {
        val tl = intersect(lines[3], lines[0]) ?: return null
        val tr = intersect(lines[0], lines[1]) ?: return null
        val br = intersect(lines[1], lines[2]) ?: return null
        val bl = intersect(lines[2], lines[3]) ?: return null
        val q = Quad(tl, tr, br, bl)
        return if (q.isConvex()) q else null
    }

    // ---------------------------------------------------------------- 第 2 步：原图边缘吸附

    /** 沿四条边扫描亮度梯度，把边吸到真实卡片边缘上，再拟合、再求交 */
    private fun refine(src: Bitmap, quad: Quad): Quad {
        val bw = src.width
        val bh = src.height
        val k = min(1f, REFINE_MAX.toFloat() / max(bw, bh))
        val w = max(64, (bw * k).toInt())
        val h = max(64, (bh * k).toInt())
        val small = if (w == bw && h == bh) src else Bitmap.createScaledBitmap(src, w, h, true)
        val gray = grayOf(small, w, h)
        if (small !== src) small.recycle()

        var q = quad.scaled(w.toFloat() / bw, h.toFloat() / bh).clampTo(w.toFloat(), h.toFloat())
        var radius = max(5f, min(w, h) * 0.012f)

        repeat(3) { pass ->
            val lines = Array<FloatArray?>(4) { null }
            for (e in 0..3) lines[e] = snapEdge(gray, w, h, q, e, radius)
            if (lines.all { it != null }) {
                cornersFromLines(lines.map { it!! }.toTypedArray())?.let { cand ->
                    if (plausible(cand, w.toFloat(), h.toFloat())) q = cand
                }
            }
            radius = max(2.5f, radius * 0.5f)
        }
        return q.scaled(bw.toFloat() / w, bh.toFloat() / h).clampTo(bw.toFloat(), bh.toFloat())
    }

    /** 对第 e 条边（0 上 1 右 2 下 3 左）做梯度吸附 + 直线拟合；[gate] 为弱到多弱的边也接受 */
    private fun snapEdge(
        gray: IntArray,
        w: Int,
        h: Int,
        q: Quad,
        e: Int,
        radius: Float,
        gate: Float = -10f
    ): FloatArray? {
        val pts = q.points()
        val a = pts[e]
        val b = pts[(e + 1) % 4]

        // 外法线：远离四边形中心的方向
        var cx = 0f
        var cy = 0f
        for (p in pts) {
            cx += p.x
            cy += p.y
        }
        cx /= 4f
        cy /= 4f

        var nx = -(b.y - a.y)
        var ny = b.x - a.x
        val len = sqrt(nx * nx + ny * ny)
        if (len < 1e-3f) return null
        nx /= len
        ny /= len
        val mx = (a.x + b.x) / 2f
        val my = (a.y + b.y) / 2f
        if ((mx - cx) * nx + (my - cy) * ny < 0f) {
            nx = -nx
            ny = -ny
        }

        val hits = ArrayList<PointF>()
        val steps = 28
        val r = radius.toInt().coerceAtLeast(3)
        for (i in 1 until steps) {
            val t = i.toFloat() / steps
            val px = a.x + (b.x - a.x) * t
            val py = a.y + (b.y - a.y) * t

            var bestK = Int.MIN_VALUE
            var bestG = 0f
            for (kk in -r..r) {
                val g = lumAt(gray, w, h, px + nx * (kk + 1), py + ny * (kk + 1)) -
                    lumAt(gray, w, h, px + nx * (kk - 1), py + ny * (kk - 1))
                // 卡片在里、背景在外：沿外法线走亮度应该下降，梯度为负
                if (g < bestG) {
                    bestG = g
                    bestK = kk
                }
            }
            if (bestK == Int.MIN_VALUE || bestG > gate) continue

            // 抛物线求亚像素
            val g0 = lumAt(gray, w, h, px + nx * (bestK + 1), py + ny * (bestK + 1)) -
                lumAt(gray, w, h, px + nx * (bestK - 1), py + ny * (bestK - 1))
            val gp = lumAt(gray, w, h, px + nx * (bestK + 2), py + ny * (bestK + 2)) -
                lumAt(gray, w, h, px + nx * bestK, py + ny * bestK)
            val gm = lumAt(gray, w, h, px + nx * bestK, py + ny * bestK) -
                lumAt(gray, w, h, px + nx * (bestK - 2), py + ny * (bestK - 2))
            val denom = (gm + gp - 2f * g0)
            val sub = if (abs(denom) < 1e-3f) 0f else (-0.5f * (gp - gm) / denom).coerceIn(-0.5f, 0.5f)

            val off = bestK + sub
            hits.add(PointF(px + nx * off, py + ny * off))
        }
        if (hits.size < steps * 0.5f) return null
        return fitLine(hits)
    }

    private fun lumAt(gray: IntArray, w: Int, h: Int, x: Float, y: Float): Float {
        val fx = x.coerceIn(0f, (w - 1).toFloat())
        val fy = y.coerceIn(0f, (h - 1).toFloat())
        val x0 = fx.toInt()
        val y0 = fy.toInt()
        val x1 = min(w - 1, x0 + 1)
        val y1 = min(h - 1, y0 + 1)
        val ax = fx - x0
        val ay = fy - y0
        val p00 = gray[y0 * w + x0].toFloat()
        val p10 = gray[y0 * w + x1].toFloat()
        val p01 = gray[y1 * w + x0].toFloat()
        val p11 = gray[y1 * w + x1].toFloat()
        val top = p00 + (p10 - p00) * ax
        val bot = p01 + (p11 - p01) * ax
        return top + (bot - top) * ay
    }

    // ---------------------------------------------------------------- 基础算子

    private fun grayOf(bmp: Bitmap, w: Int, h: Int): IntArray {
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        val out = IntArray(w * h)
        for (i in px.indices) {
            val c = px[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            out[i] = (r * 299 + g * 587 + b * 114) / 1000
        }
        return out
    }

    private fun otsu(gray: IntArray): Int {
        val hist = IntArray(256)
        for (v in gray) hist[v.coerceIn(0, 255)]++
        val total = gray.size
        var sum = 0.0
        for (i in 0..255) sum += i.toDouble() * hist[i]
        var sumB = 0.0
        var wB = 0
        var best = 0.0
        var thr = 128
        for (t in 0..255) {
            wB += hist[t]
            if (wB == 0) continue
            val wF = total - wB
            if (wF == 0) break
            sumB += t.toDouble() * hist[t]
            val mB = sumB / wB
            val mF = (sum - sumB) / wF
            val between = wB.toDouble() * wF * (mB - mF) * (mB - mF)
            if (between > best) {
                best = between
                thr = t
            }
        }
        return thr
    }

    private fun boxBlur(src: IntArray, w: Int, h: Int, r: Int): IntArray {
        if (r <= 0) return src
        val tmp = IntArray(w * h)
        val out = IntArray(w * h)
        val win = 2 * r + 1
        for (y in 0 until h) {
            val row = y * w
            var sum = 0
            for (i in -r..r) sum += src[row + i.coerceIn(0, w - 1)]
            for (x in 0 until w) {
                tmp[row + x] = sum / win
                sum += src[row + (x + r + 1).coerceIn(0, w - 1)] - src[row + (x - r).coerceIn(0, w - 1)]
            }
        }
        for (x in 0 until w) {
            var sum = 0
            for (i in -r..r) sum += tmp[i.coerceIn(0, h - 1) * w + x]
            for (y in 0 until h) {
                out[y * w + x] = sum / win
                sum += tmp[(y + r + 1).coerceIn(0, h - 1) * w + x] - tmp[(y - r).coerceIn(0, h - 1) * w + x]
            }
        }
        return out
    }

    private class Region(val count: Int, val xs: IntArray, val ys: IntArray)

    /** 连通域扫描的临时缓冲。一次扫描要跑十几个阈值，缓冲复用可省掉几十 MB 的反复分配 */
    private class Workspace(n: Int) {
        val mask = BooleanArray(n)
        val visited = BooleanArray(n)
        val stack = IntArray(n)
        val xs = IntArray(n)
        val ys = IntArray(n)
    }

    private fun largestRegion(
        gray: IntArray,
        w: Int,
        h: Int,
        thr: Int,
        bright: Boolean,
        ws: Workspace
    ): Region? {
        val mask = ws.mask
        val visited = ws.visited
        val stack = ws.stack
        val xsBuf = ws.xs
        val ysBuf = ws.ys
        for (i in gray.indices) {
            mask[i] = if (bright) gray[i] > thr else gray[i] <= thr
            visited[i] = false
        }

        var bestCount = 0
        var bestXs: IntArray? = null
        var bestYs: IntArray? = null

        for (start in mask.indices) {
            if (!mask[start] || visited[start]) continue
            var sp = 0
            stack[sp++] = start
            visited[start] = true
            var count = 0
            while (sp > 0) {
                val idx = stack[--sp]
                val x = idx % w
                val y = idx / w
                xsBuf[count] = x
                ysBuf[count] = y
                count++
                if (x > 0) {
                    val n = idx - 1
                    if (mask[n] && !visited[n]) { visited[n] = true; stack[sp++] = n }
                }
                if (x < w - 1) {
                    val n = idx + 1
                    if (mask[n] && !visited[n]) { visited[n] = true; stack[sp++] = n }
                }
                if (y > 0) {
                    val n = idx - w
                    if (mask[n] && !visited[n]) { visited[n] = true; stack[sp++] = n }
                }
                if (y < h - 1) {
                    val n = idx + w
                    if (mask[n] && !visited[n]) { visited[n] = true; stack[sp++] = n }
                }
            }
            if (count > bestCount) {
                bestCount = count
                bestXs = xsBuf.copyOf(count)
                bestYs = ysBuf.copyOf(count)
            }
        }
        val bx = bestXs ?: return null
        val by = bestYs ?: return null
        return Region(bestCount, bx, by)
    }

    /** Andrew monotone chain */
    private fun convexHull(xs: IntArray, ys: IntArray, n: Int): List<PointF> {
        if (n < 3) return emptyList()
        val idx = (0 until n).sortedWith(compareBy({ xs[it] }, { ys[it] }))
        val pts = ArrayList<PointF>(n)
        for (i in idx) pts.add(PointF(xs[i].toFloat(), ys[i].toFloat()))

        val hull = ArrayList<PointF>(n * 2)
        for (p in pts) {
            while (hull.size >= 2 && cross(hull[hull.size - 2], hull[hull.size - 1], p) <= 0f) {
                hull.removeAt(hull.size - 1)
            }
            hull.add(p)
        }
        val lower = hull.size + 1
        for (i in pts.indices.reversed()) {
            val p = pts[i]
            while (hull.size >= lower && cross(hull[hull.size - 2], hull[hull.size - 1], p) <= 0f) {
                hull.removeAt(hull.size - 1)
            }
            hull.add(p)
        }
        if (hull.isNotEmpty()) hull.removeAt(hull.size - 1)
        return hull
    }

    private fun cross(o: PointF, a: PointF, b: PointF): Float =
        (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)

    /**
     * 最小外接矩形：返回 [ux, uy, ox, oy, uMin, uMax, vMin, vMax]，
     * (ux,uy) 是一条边方向，(ox,oy) 为投影原点。
     */
    private fun minAreaRect(hull: List<PointF>): FloatArray? {
        if (hull.size < 4) return null
        var bestArea = Float.MAX_VALUE
        var best: FloatArray? = null

        for (i in hull.indices) {
            val a = hull[i]
            val b = hull[(i + 1) % hull.size]
            var ux = b.x - a.x
            var uy = b.y - a.y
            val len = sqrt(ux * ux + uy * uy)
            if (len < 1e-3f) continue
            ux /= len
            uy /= len
            val vx = -uy
            val vy = ux

            var uMin = Float.MAX_VALUE
            var uMax = -Float.MAX_VALUE
            var vMin = Float.MAX_VALUE
            var vMax = -Float.MAX_VALUE
            for (p in hull) {
                val u = p.x * ux + p.y * uy
                val v = p.x * vx + p.y * vy
                if (u < uMin) uMin = u
                if (u > uMax) uMax = u
                if (v < vMin) vMin = v
                if (v > vMax) vMax = v
            }
            val area = (uMax - uMin) * (vMax - vMin)
            if (area < bestArea) {
                bestArea = area
                best = floatArrayOf(ux, uy, 0f, 0f, uMin, uMax, vMin, vMax)
            }
        }
        return best
    }

    /** 总体最小二乘拟合直线，返回 [px, py, dx, dy]（过点 + 单位方向） */
    private fun fitLine(pts: List<PointF>): FloatArray? {
        if (pts.size < 4) return null
        var cx = 0f
        var cy = 0f
        for (p in pts) {
            cx += p.x
            cy += p.y
        }
        cx /= pts.size
        cy /= pts.size

        var sxx = 0f
        var syy = 0f
        var sxy = 0f
        for (p in pts) {
            val dx = p.x - cx
            val dy = p.y - cy
            sxx += dx * dx
            syy += dy * dy
            sxy += dx * dy
        }
        if (sxx + syy < 1e-6f) return null
        val theta = 0.5f * atan2(2f * sxy, sxx - syy)
        val dx = cos(theta)
        val dy = sin(theta)
        return floatArrayOf(cx, cy, dx, dy)
    }

    private fun intersect(l1: FloatArray, l2: FloatArray): PointF? {
        val denom = l1[2] * l2[3] - l1[3] * l2[2]
        if (abs(denom) < 1e-4f) return null
        val t = ((l2[0] - l1[0]) * l2[3] - (l2[1] - l1[1]) * l2[2]) / denom
        return PointF(l1[0] + l1[2] * t, l1[1] + l1[3] * t)
    }

    private fun plausible(q: Quad, w: Float, h: Float): Boolean {
        if (!q.isConvex()) return false
        if (q.area() < w * h * 0.05f) return false
        val e = q.edgeLengths()
        if (e.any { it < min(w, h) * MIN_EDGE_FRAC }) return false
        val ww = (e[0] + e[2]) / 2f
        val hh = (e[1] + e[3]) / 2f
        if (ww < 8f || hh < 8f) return false
        val ratio = if (ww > hh) ww / hh else hh / ww
        return ratio in 1.22f..2.10f
    }
}
