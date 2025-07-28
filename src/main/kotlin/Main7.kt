package net.karpelevitch

import org.apache.commons.math3.fraction.Fraction
import org.apache.commons.math3.linear.*
import org.apache.commons.math3.util.ArithmeticUtils
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource.Monotonic.markNow

private fun calculateResistance(getConductance: Function2<Int, Int, Int>, size: Int, sink: Int = size - 1): Array<Fraction>? {
    require(size >= 2) { "Need at least 2 nodes" }

    val source = 0

    // Build conductance matrix G (exclude sink node as reference)
    val numNodes = size - 1
    val gData = Array(numNodes) { Array(numNodes) { Fraction.ZERO } }


    for (i in 0 until numNodes) {
        // Map reduced matrix index to original node index
        val originalI = if (i < sink) i else i + 1

        // Calculate diagonal element: sum of all conductances connected to this node
        gData[i][i] = (0 until size)
            .filter { it != originalI }
            .map { Fraction(getConductance(originalI, it)) }
            .reduce { a, b -> a.add(b) }

        // Calculate off-diagonal elements: negative conductances between nodes
        for (j in 0 until numNodes) {
            if (i != j) {
                val originalJ = if (j < sink) j else j + 1
                val c = getConductance(originalI, originalJ)
                if (c != 0) {
                    gData[i][j] = Fraction(c).negate()
                }
            }
        }
    }

    // Create conductance matrix
    val conductanceFieldMatrix: FieldMatrix<Fraction> = Array2DRowFieldMatrix(gData)

    // Build current injection vector
    val currentData = Array(numNodes) { Fraction.ZERO }
    val sourceIndex = if (source < sink) source else source - 1
    currentData[sourceIndex] = Fraction.ONE
    val currentVector: FieldVector<Fraction> = ArrayFieldVector(currentData)

    // Solve G × V = I using LU decomposition
    val decomposition = FieldLUDecomposition(conductanceFieldMatrix)
    return if (!decomposition.solver.isNonSingular) {
        null // Singular matrix (infinite resistance)
    } else {
        val vector = decomposition.solver.solve(currentVector)
        Array(vector.dimension + 1) { i ->
            if (i < sink)
                vector.getEntry(i)
            else if (i > sink)
                vector.getEntry(i - 1)
            else Fraction.ZERO
        }
    }
}

class Matrix1 {
    val matrix: IntArray
    val size: Int
    constructor(size: Int) {
        this.matrix = IntArray(size * size) { 0 }
        this.size = size
    }

    fun set(i: Int, j: Int, it: Int) {
        matrix[i*size+j] = it
        matrix[j*size+i] = it
    }

    fun get(i: Int, j: Int) = matrix[i* size +j]
    fun getRow(i: Int) = matrix.copyOfRange(i*size, (i+1)*size)

    override fun toString(): String {
        return buildString {
            appendLine("------ Matrix ------")
            for (row in (0 until size)) {
                for (col in (0 until size)) {
                    append("%3d".format(get(row, col)))
                }
                appendLine()
            }
            appendLine("-------------------------------")
        }
    }
}

fun genMatrices7(k: Int): Sequence<Matrix1> {
    return (2..k + 1).asSequence()
        .flatMap { size -> genM(size, k) }
        .map { links ->
            val size = 1 + links.last().to
            Matrix1(size).also { m -> links.forEach { m.set(it.from, it.to, it.count) } }
        }
}

data class Link(val from: Int, val to: Int, val count: Int) {
    override fun toString(): String = if (count==1) "[$from,$to]" else "[$from,$to]^$count"
}


private fun genM(size: Int, k: Int, row: Int = 0, prefix: List<Link> = emptyList()): Sequence<List<Link>> {
    val sinkGroup = listOf(listOf(size - 1))
    if (row == size - 2) {
        if (k > 0 && (row == 0 || prefix.any { it.to == row })) {
            return sequenceOf(prefix + Link(row, size - 1, k))
        }
    } else if (row == 0 || prefix.any { it.to == row }) {
        val groups: List<List<Int>> = (row + 1 until size - 1).groupBy { frow -> prefix.filter { it.to == frow }.map { it.from }.sorted() }.values.map { it.sorted() } + sinkGroup
        return genM2(size, k, row, prefix, groups)
    }
    return emptySequence()
}

private fun genM2(size: Int, k: Int, row: Int, prefix: List<Link>, groups: List<List<Int>>): Sequence<List<Link>> {
    if (k > 0) {
        val group = groups.first()
        val tail = groups.drop(1)
        val maxToUse = k - (size - row - 2)
        return (0..maxToUse).asSequence().flatMap { toUse ->
            partitions(toUse, group.size).flatMap { pa ->
                val nextprefix: List<Link> = prefix + pa.mapIndexed { i, v -> Link(row, group[i], v) }
                if (tail.isEmpty()) {
                    if (nextprefix.lastOrNull()?.from == row) {
                        genM(size, k - toUse, row + 1, nextprefix)
                    } else {
                        emptySequence()
                    }
                } else {
                    genM2(size, k - toUse, row, nextprefix, tail)
                }
            }
        }
    } else {
        return emptySequence()
    }
}

fun partitions(n: Int, maxPartitions: Int, prefix: List<Int> = emptyList(), maxPartitionSize: Int = n): Sequence<List<Int>> {
    if (n == 0) {
        return sequenceOf(prefix)
    } else if (n > maxPartitions * maxPartitionSize) { // won't fit
        return emptySequence()
    } else if (maxPartitions == 1) {
        return sequenceOf(prefix + n)
    } else {
        val minN = (n + 1) / (maxPartitions) - 1
        return (minN..min(n,maxPartitionSize)).asSequence().flatMap {
            partitions(n - it, maxPartitions - 1, prefix + it, it)
        }
    }
}


private fun genGroups(k: Int, group: List<Int>, prefix: List<Pair<Int, Int>> = emptyList(), maxCount: Int = k, allowNegative: Boolean): Sequence<List<Pair<Int, Int>>> = sequence {
    yield(prefix)
    if (!group.isEmpty()) {
        val head = group[0]
        val tail = group.drop(1)
        (1..min(k, maxCount)).forEach {
            yieldAll(genGroups(k - it, tail, prefix + Pair(head, it), it, allowNegative))
            if (allowNegative && (prefix.isEmpty() || prefix.last().second != it)) {
                yieldAll(genGroups(k - it, tail, prefix + Pair(head, -it), it, allowNegative))
            }
        }
    }
}

private class Circuit1(val voltages: Array<Fraction>, val connections: List<Link>) {
    companion object {
        fun fromMatrix(m: Matrix1, voltages: Array<Fraction>): Circuit1 {
            val imap = voltages.withIndex().sortedByDescending { it.value }.mapIndexed { i, v -> v.index to (i + 1) }.toMap()

            val conn = (0 until m.size).flatMap { i ->
                val newi = imap[i]!!
                (i + 1 until m.size).mapNotNull { j ->
                    val c = m.get(i, j)
                    if (c == 0) {
                        null
                    } else {
                        val newj = imap[j]!!
                        Link(min(newi, newj), max(newi, newj), c)
                    }
                }
            }.sortedWith(compareBy({ it.from }, { it.to }))
            val vremapped = voltages.mapIndexed { i, v -> imap[i]!! to v }.sortedBy { it.first }.map { it.second }.toTypedArray()
            return Circuit1(vremapped, conn)
        }
    }

    override fun toString(): String {
        return connections.joinToString(",") + " voltages=[${voltages.joinToString(",") { "${it.numerator}/${it.denominator}" }}]"
    }
}


private fun toMoves(m: Matrix1, voltages: Array<Fraction>): List<Move> {
    // Calculate LCM of all denominators
    val lcm = voltages.map { it.denominator }.reduce { a, b -> ArithmeticUtils.lcm(a, b) }
    require(lcm > 0) { "lcm=$lcm" }

    val c = voltages.map {
        val c = it.multiply(lcm)
        require(c.denominator == 1) { "c=$c lcm=$lcm voltages=${voltages}" }
        c.numerator
    }

    return (0 until m.size).flatMap { i ->
        val c1 = c[i]
        (i + 1 until m.size).flatMap { j ->
            val count = m.get(i, j)
            if (count != 0) {
                val c2 = c[j]
                val move = if (count * (c1 - c2) < 0) {
                    Move.get(c1, c2)
                } else {
                    Move.get(c2, c1)
                }
                List(abs(count), { move })
            } else {
                emptyList()
            }
        }
    }.sorted()
}

// Example usage and testing
fun main(args: Array<String>) {
    if (args.getOrNull(0) == "best") {
        allBest(startK = args.getOrNull(1)?.toInt() ?: 1)
    } else if (args.getOrNull(0) == "fwd") {
        solveAll(::printResult, { r: Fraction, steps: Int, m: Matrix1, voltages: Array<Fraction> ->
            processMatrix({ true }, steps, m, voltages)
        })
    } else {
        solveAll(::printResult, { r: Fraction, steps: Int, m: Matrix1, voltages: Array<Fraction> ->
            processMatrix({ res: Fraction -> res.numerator == 1 }, steps, m, voltages)
        }, 1)
    }
}

private fun allBest(startK: Int = 1) {
    var lower = Fraction.ZERO
    var upper = Fraction.TWO
    val lr = mutableMapOf<List<Int>, String>()
    val ur = mutableMapOf<List<Int>, String>()
    solveAll({ k ->
        lr.values.forEach {
            println("RESULT k=$k BEST lower $lower $it t=${start.elapsedNow()}")
        }
        ur.values.forEach {
            println("RESULT k=$k BEST upper $upper $it t=${start.elapsedNow()}")
        }
        lr.clear()
        ur.clear()
    }, { r: Fraction, steps: Int, m: Matrix1, voltages: Array<Fraction> ->
        if (r != Fraction.ONE && r >= lower && r <= upper) {
            val moves = toMoves(m, voltages)
            val circuit = Circuit1.fromMatrix(m, voltages)
            val rr = "can move ${r.denominator} to ${r.numerator} in $steps ${sig(moves)} / $moves circuit: $circuit"
//            println("$rr t=${start.elapsedNow()} ")
            if (r >= Fraction.ONE) {
                if (r != upper) ur.clear()
                upper = r
                ur.putIfAbsent(sig(moves), rr)
            } else {
                if (r != lower) lr.clear()
                lower = r
                lr.putIfAbsent(sig(moves), rr)
            }
        }
    }, startK)
}

private class Solution1(val steps: Int) {
    val moves = mutableMapOf<List<Int>, List<Move>>()
}

private val solved = mutableMapOf<Fraction, Solution1>()

private val start = markNow()

private fun solveAll(printResult: (Int) -> Unit, pm: (Fraction, Int, Matrix1, Array<Fraction>) -> Unit, startK: Int = 1) {

    var nextPrint = markNow() + 10.minutes
    fun solve(m: Matrix1, i: Int, steps: Int) {
        calculateResistance({ i: Int, j: Int -> m.get(i, j) }, m.size, i)?.let {
            // no duplicate voltages
            if (it.distinct().size == it.size) {
                if (nextPrint.hasPassedNow()) {
                    println("processing k=$steps ${m.getRow(0).contentToString()} t=${start.elapsedNow()}")
                    nextPrint = markNow() + 10.minutes
                }
                pm(it[0], steps, m, it)
            }
        }
    }

    var k = startK
    while (true) {
        val matrices = genMatrices7(k)
        matrices.forEach { m ->
            solve(m, m.size - 1, k)
        }
        printResult(k)
        k++
    }
}

private fun printResult(k: Int) {
    println("all solutions for k=$k t=${start.elapsedNow()} :")
    solved.filterValues { it.steps == k }.keys.sorted().forEach { println("RESULT for k=$k ${it.numerator}/${it.denominator}") }
}

private fun processMatrix(filter: (Fraction) -> Boolean, steps: Int, m: Matrix1, voltages: Array<Fraction>) {
    val r = voltages[0]
    if (filter(r)) {
        val d = r.denominator
        val sol = solved.getOrPut(r, { Solution1(steps) })
        if (sol.steps == steps) {
            validOrNull(d, steps, toMoves(m, voltages))?.let {
                val key = sig(it)
                if (sol.moves.putIfAbsent(key, it) == null) {
                    println("can move $d to ${r.numerator} in $steps steps with $key / $it t=${start.elapsedNow()}")
                }
            }
        }
    }
}
