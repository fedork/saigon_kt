package net.karpelevitch

import org.apache.commons.math3.fraction.Fraction
import org.apache.commons.math3.linear.*
import org.apache.commons.math3.util.ArithmeticUtils
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource.Monotonic.markNow

/**
 * Calculate exact resistance between source (index 0) and sink (last index)
 * @param conductanceMatrix - adjacency matrix where conductanceMatrix[i][j] is the conductance
 *                          between nodes i and j (0 means no connection, positive/negative
 *                          integers are conductances)
 * @return exact resistance as Fraction, or null if infinite/undefined
 */
fun calculateExactResistance(conductanceMatrix: Array<IntArray>, sink: Int = conductanceMatrix.size - 1): Array<Fraction>? {
    val n = conductanceMatrix.size
    require(n >= 2) { "Need at least 2 nodes" }

    val source = 0

    // Build conductance matrix G (exclude sink node as reference)
    val numNodes = n - 1
    val gData = Array(numNodes) { Array(numNodes) { Fraction.ZERO } }

    for (i in 0 until numNodes) {
        // Map reduced matrix index to original node index
        val originalI = if (i < sink) i else i + 1

        // Calculate diagonal element: sum of all conductances connected to this node

        gData[i][i] = (0 until n)
            .filter { it != originalI }
            .map { Fraction(conductanceMatrix[originalI][it]) }
            .reduce { a, b -> a.add(b) }

        // Calculate off-diagonal elements: negative conductances between nodes
        for (j in 0 until numNodes) {
            if (i != j) {
                val originalJ = if (j < sink) j else j + 1
                if (conductanceMatrix[originalI][originalJ] != 0) {
                    gData[i][j] = Fraction(conductanceMatrix[originalI][originalJ]).negate()
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
    val voltages = if (!decomposition.solver.isNonSingular) {
        null // Singular matrix (infinite resistance)
    } else {
        val vector = decomposition.solver.solve(currentVector)
        val arr = Array<Fraction>(vector.dimension + 1) { i -> if (i < sink) vector.getEntry(i) else if (i > sink) vector.getEntry(i - 1) else Fraction.ZERO }
        arr
    }
    return voltages
}

// Helper function to create conductance matrix from adjacency list format
fun createConductanceMatrix(connections: List<Triple<Int, Int, Int>>, numNodes: Int): Array<IntArray> {
    val matrix = Array(numNodes) { IntArray(numNodes) { 0 } }

    for ((from, to, conductance) in connections) {
        matrix[from][to] = conductance
        matrix[to][from] = conductance // Assume symmetric (undirected graph)
    }

    return matrix
}


fun printConductanceMatrix(matrix: Array<IntArray>) {
    println("------ Conductance Matrix ------")
    for (row in matrix) {
        for (col in row) {
            print("%+3d".format(col))
        }
        println()
    }
    println("-------------------------------")
}

class Matrix(val matrix: Array<IntArray>) {
    val size = matrix.size
    val next by lazy {
        val newMatrix = Array(size + 1) { IntArray(size + 1) { 0 } }
        for (i in 0 until size) {
            for (j in 0 until size) {
                newMatrix[i][j] = matrix[i][j]
            }
        }
        Matrix(newMatrix)
    }

    fun copyOf() = Matrix(matrix.map { it.clone() }.toTypedArray())
    fun set(i: Int, j: Int, it: Int) {
        matrix[i][j] = it
        matrix[j][i] = it
    }

    fun copyOfSize(n: Int): Matrix {
        if (n == size) return copyOf()
        val newMatrix = Array(n) { IntArray(n) { 0 } }
        for (i in 0 until size) {
            for (j in 0 until size) {
                newMatrix[i][j] = matrix[i][j]
            }
        }
        return Matrix(newMatrix)
    }

    fun print() {
        println("------ Matrix ------")
        for (row in matrix) {
            for (col in row) {
                print("%+3d".format(col))
            }
            println()
        }
        println("-------------------------------")
    }

    override fun toString(): String {
        return buildString {
            appendLine("------ Matrix ------")
            for (row in matrix) {
                for (col in row) {
                    append("%+3d".format(col))
                }
                appendLine()
            }
            appendLine("-------------------------------")
        }
    }

    fun rescount(): Int = matrix.mapIndexed { i, row -> (i + 1 until size).sumOf { abs(row[it]) } }.sum()
}

fun generateMatrices(k: Int, base: Matrix = Matrix(arrayOf(IntArray(1, { 0 }))), i: Int = 0, allowNegative: Boolean): Sequence<Matrix> = sequence {
    if (k == 0) {
        yield(base)
    } else if (i == 0 || base.matrix[i].any { it != 0 }) {
        val groups = (i + 1 until base.size).groupBy { base.matrix[it] }.values.sortedWith(compareBy({ it.size }, { it[0] }))
        val maxK = if (i>1 && (0..i-2).all {base.matrix[i-1][it]==base.matrix[i][it]}) {
            min(k,base.matrix[i-1].mapIndexed { i1,c->if (i1>i) abs(c) else c}.sum())
        } else k
        val newGroups = genNewRows(maxK, base.size, allowNegative).groupBy { moveCount(it) }
        genRow(maxK, groups, allowNegative).forEach { row1 ->
            val count: Int = moveCount(row1)
            val m1 = base.copyOf()
            row1.forEach { (j, c) -> m1.set(i, j, c) }
            newGroups.filterKeys { it <= (maxK - count) }.forEach { (countNew, rowsNew) ->
                rowsNew.forEach { row2 ->
                    val m2 = m1.copyOfSize((row2.maxOfOrNull { it.first } ?: (base.size - 1)) + 1)
                    row2.forEach { (j, c) -> m2.set(i, j, c) }
                    if (countNew > 0 || count > 0) {
//                        println("k=$k i=$i row1=$row1 row2=$row2 count=$count countNew=$countNew base=$base")
                        yieldAll(generateMatrices(k - count - countNew, m2, i + 1, allowNegative))
                    }
                }
            }
        }
    }
}

fun genNewRows(k: Int, firstRow: Int, allowNegative: Boolean, maxCount: Int = k, prefix: List<Pair<Int, Int>> = emptyList()): Sequence<List<Pair<Int, Int>>> = sequence {
    yield(prefix)
    (1..min(k, maxCount)).forEach {
        yieldAll(genNewRows(k - it, firstRow + 1, allowNegative, it, prefix + Pair(firstRow, it)))
        if (allowNegative && (prefix.isEmpty() || prefix.last().second != it)) {
            yieldAll(genNewRows(k - it, firstRow + 1, allowNegative, it, prefix + Pair(firstRow, -it)))
        }
    }
}

fun genRow(k: Int, groups: List<List<Int>>, allowNegative: Boolean, prefix: List<Pair<Int, Int>> = emptyList()): Sequence<List<Pair<Int, Int>>> = sequence {
    if (groups.isEmpty()) {
        yield(prefix)
    } else {
        val head = groups[0]
        val tail = groups.drop(1)
        genGroups(k, head, allowNegative = allowNegative).forEach {
            yieldAll(genRow(k - moveCount(it), tail, allowNegative, prefix + it))
        }
    }
}

private fun moveCount(pairs: List<Pair<Int, Int>>): Int = pairs.sumOf { abs(it.second) }


fun genGroups(k: Int, group: List<Int>, prefix: List<Pair<Int, Int>> = emptyList(), maxCount: Int = k, allowNegative: Boolean): Sequence<List<Pair<Int, Int>>> = sequence {
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

class Circuit(val voltages: Array<Fraction>, val connections: List<Conn>) {
    companion object {
        fun fromMatrix(m: Matrix, voltages: Array<Fraction>): Circuit {
            val imap = voltages.withIndex().sortedByDescending { it.value }.mapIndexed { i, v -> v.index to (i+1) }.toMap()

            val conn = (0 until m.size).flatMap { i ->
                val v1 = voltages[i]
                val newi = imap[i]!!
                (i + 1 until m.size).mapNotNull { j ->
                    val c = m.matrix[i][j]
                    if (c == 0) {
                        null
                    } else {
                        val v2 = voltages[j]
                        val newj = imap[j]!!
                        Conn(min(newi, newj), max(newi, newj), c)
                    }
                }
            }.sortedWith(compareBy({ it.from }, { it.to }))
            val vremapped = voltages.mapIndexed { i, v -> imap[i]!! to v }.sortedBy { it.first }.map { it.second }.toTypedArray()
            return Circuit(vremapped, conn)
        }
    }

    override fun toString(): String {
        return connections.joinToString(",") + " voltages=[${voltages.joinToString(","){"${it.numerator}/${it.denominator}"}}]"
    }

    data class Conn (val from: Int, val to: Int, val conductance: Int) {
        override fun toString(): String = if (conductance==1) "[$from,$to]" else "[$from,$to]^$conductance"
    }
}



fun toMoves(m: Matrix, voltages: Array<Fraction>): List<Move> {
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
            val count = m.matrix[i][j]
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
        solveAll(false, ::printResult, { r: Fraction, steps: Int, m: Matrix, voltages: Array<Fraction> ->
            processMatrix({ true }, steps, m, voltages)
        })
    } else {
        solveAll(true, ::printResult, { r: Fraction, steps: Int, m: Matrix, voltages: Array<Fraction> ->
            processMatrix({ res: Fraction -> res.numerator == 1 }, steps, m, voltages)
        }, 1)
    }
}

private fun allBest(startK: Int = 1) {
    var lower = Fraction.ZERO
    var upper = Fraction.TWO
    var lr = mutableMapOf<List<Int>, String>()
    var ur = mutableMapOf<List<Int>, String>()
    solveAll(false, { k ->
        lr.values.forEach {
            println("RESULT k=$k BEST lower $lower $it t=${start.elapsedNow()}")
        }
        ur.values.forEach {
            println("RESULT k=$k BEST upper $upper $it t=${start.elapsedNow()}")
        }
        lr.clear()
        ur.clear()
    }, { r: Fraction, steps: Int, m: Matrix, voltages: Array<Fraction> ->
        if (r!= Fraction.ONE && r >= lower && r <= upper) {
            val moves = toMoves(m, voltages)
            val circuit = Circuit.fromMatrix(m, voltages)
            val rr = "can move ${r.denominator} to ${r.numerator} in $steps ${sig(moves)} / $moves circuit: $circuit"
//            println("$rr t=${start.elapsedNow()} ")
            if (r >= Fraction.ONE) {
                if (r!=upper) ur.clear()
                upper = r
                ur.putIfAbsent(sig(moves), rr)
            } else {
                if (r!=lower) lr.clear()
                lower = r
                lr.putIfAbsent(sig(moves), rr)
            }
        }
    }, startK)
}

private class Solution(val n: Int, val distance: Int, val steps: Int) {
    val moves = mutableMapOf<List<Int>, List<Move>>()
}

private val solved = mutableMapOf<Fraction, Solution>()

private val start = markNow()

private fun solveAll(allowNegative: Boolean, printResult: (Int) -> Unit, pm: (Fraction, Int, Matrix, Array<Fraction>) -> Unit, startK: Int = 1) {

    var nextPrint = markNow() + 10.minutes
    fun solve(m: Matrix, i: Int, steps: Int) {
        calculateExactResistance(m.matrix, i)?.let {
            // no duplicate voltages
            if (it.distinct().size == it.size) {
                if (nextPrint.hasPassedNow()) {
                    println("processing k=$steps ${m.matrix[0].contentToString()} t=${start.elapsedNow()}")
                    nextPrint = markNow() + 10.minutes
                }
                pm(it[0], steps, m, it)
            }
        }
    }

    var k = startK
    while (true) {
        val matrices = generateMatrices(k, allowNegative = allowNegative)
        matrices.forEach { m ->
            require(m.rescount() == k) { "rescount=${m.rescount()} k=$k $m" }
            val singleConnected = (1 until m.size).filter { m.matrix[it].count { it != 0 } == 1 }
            if (singleConnected.isEmpty()) {
                for (i in 1 until m.size) {
                    solve(m, i, k)
                }
            } else if (singleConnected.size == 1) {
                solve(m, singleConnected[0], k)
            }
            // otherwise loose ends
        }
        printResult(k)
        k++
    }
}

private fun printResult(k: Int) {
    println("all solutions for k=$k t=${start.elapsedNow()} :")
    solved.filterValues { it.steps == k }.keys.sorted().forEach { println("RESULT for k=$k ${it.numerator}/${it.denominator}") }
}

private fun processMatrix(filter: (Fraction) -> Boolean, steps: Int, m: Matrix, voltages: Array<Fraction>) {
    val r = voltages[0]
    if (filter(r)) {
        val d = r.denominator
        val sol = solved.getOrPut(r, { Solution(d, r.numerator, steps) })
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
