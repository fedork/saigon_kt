package net.karpelevitch

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource.Monotonic.markNow

private var start = markNow()

fun main(args: Array<String>) {
    var startN = 0
    var startK = 0
    if (args.isNotEmpty()) {
        if (args[0] == "first") {
            firstOnly = true
        } else if (args[0] == "strict") {
            strict = true
        } else if (args[0] == "only") {
            onlyThis = true
        }

        if (args.size > 1) {
            startN = args[1].toInt()
        }

        if (args.size > 2) {
            startK = args[2].toInt()
        }
    }
    for (n in startN..1000) {
        start = markNow()
        var k = startK
        var solutions: List<List<Move>>
        if (!onlyThis) {
            while (true) {
                solutions = getSolutions(n, k)
                if (solutions.isNotEmpty()) break
                if (!firstOnly) {
                    println("RESULT can't solve $n in $k moves t=${start.elapsedNow()}")
                }
                k++
            }
        } else {
            solutions = getSolutions(n, startK)
        }
        getValidSoutions(n, k, solutions).mapIndexed { i, it ->
            val stations = it.map { it.to }.filterNot { it == 1 || it == 0 }.distinct()
                .sortedByDescending { Math.abs(it) }
            Pair(it, stations)
        }.sortedByDescending { it.second.maxByOrNull { Math.abs(it) } ?: 1 }
            .forEachIndexed { i, (path, stations) ->
                println("can solve $n in $k moves with (${i + 1}) $stations / $path t=${start.elapsedNow()}")
            }
        if (!firstOnly) println("RESULT can solve $n in $k moves t=${start.elapsedNow()}")
        if (onlyThis) break
    }
}

private data class Sol(
    var maxTried: Int = 0,
    var canSolve: Int? = null,
    var solutions: List<List<Move>>? = null
)

private var firstOnly = false
private var strict = false
private var onlyThis = false

private val sol = mutableMapOf<Int, Sol>()

private fun getSolutions(n: Int, k: Int): List<List<Move>> {
    val s = sol.getOrPut(n, { Sol() })
    val solutions = mutableListOf<List<Move>>()

    if (n == 0) {
        solutions.add(emptyList())
    } else if (onlyThis) {
        solutions.addAll(getSolutions(mapOf(Pair(0, n), Pair(1, -n)), n, k))
    } else {
        if (s.maxTried >= k) {
            if (k < (s.canSolve ?: Int.MAX_VALUE)) {
                return emptyList()
            }
            return s.solutions!!
        }
        // add trivial solutions
        val s = getSolutions(n - 1, k - 1).asSequence()
            .map { listOf(Move(0, 1)) + it } +
                getSolutions(n + 1, k - 1).asSequence()
                    .mapNotNull {
                        if (it.none { it.count > n }) listOf(
                            Move(
                                1,
                                0
                            )
                        ) + it else null
                    } +
                getSolutions(mapOf(Pair(0, n), Pair(1, -n)), n, k)

        if (firstOnly) {
            val first = s
                .mapNotNull { validOrNull(n, k, it) }
                .firstOrNull()
            if (first != null) {
                solutions.add(first)
            }
        } else {
            solutions.addAll(s)
        }
    }
    s.maxTried = k
    if (solutions.isNotEmpty()) {
        s.canSolve = k
        s.solutions = solutions
    }

    return solutions
}

public fun getValidSoutions(n: Int, k: Int, solutions: List<List<Move>>): List<List<Move>> =
    solutions.mapNotNull { validOrNull(n, k, it.sorted()) }

public fun validOrNull(n: Int, k: Int, ss: List<Move>): List<Move>? {
    fun getValidSeq(st: Map<Int, Int>, head: List<Move>, tail: List<Move>): Sequence<List<Move>> {
        if (tail.isEmpty()) {
//            require(st.all { it.value == if (it.key == 1) n else 0 })
            return sequenceOf(head)
        }
        return tail.asSequence()
            .filter { st.getOrDefault(it.from, 0) >= it.count }
            .flatMap { getValidSeq(move(st, it), head + it, tail - it) }
    }

    val validS = getValidSeq(mapOf(Pair(0, n)), listOf(), ss).firstOrNull()
    if (validS == null) {
        println("INVALID solution for $n in $k: $ss")
    }
    return validS
}

fun move(st: Map<Int, Int>, move: Move): Map<Int, Int> {
    val mm = st.toMutableMap()
    mm.compute(move.from) { _, v -> ((v ?: 0) - move.count) }
    mm.compute(move.to) { _, v -> ((v ?: 0) + move.count) }
    return mm.filterValues { it != 0 }
}

var nextPrint = markNow() + 5.minutes
fun getSolutions(
    st: Map<Int, Int>,
    n: Int,
    k: Int,
    prefix: List<Move> = emptyList(),
    blackList: List<Int> = emptyList()
): Sequence<List<Move>> {
    val nonZeros = st.count { it.value != 0 }
    val slack = k*2 - nonZeros
    if (slack < 0) return emptySequence() // not solvable
    if (slack == 0) {
        // endgame
        var st1 = st
        var moves = prefix
        while (st1.isNotEmpty()) {
            val k1 = st1.keys.min()
            val v1 = st1[k1]!!
            val k2 = k1 + abs(v1)
            val v2 = st1.getOrDefault(k2, 0)
            if (v1+v2 != 0) {
                return emptySequence() // not solvable
            }
            val m = if (v1 > 0) Move.get(k1, k2) else Move.get(k2, k1)
            st1 = move(st1, m)
            moves = moves + m
        }
        return sequenceOf(moves)
    }
    if (k == 0) {
        return sequenceOf(prefix)
    }
    if (nextPrint.hasPassedNow()) {
        println("processing $n in ${k+ prefix.size}:  $prefix t=${start.elapsedNow()}")
        nextPrint = markNow() + 5.minutes
    }
    return if (blackList.isEmpty()) {
        // start with 2 since 1 is trivial (already handled)
        val bl = listOf(0)
        getZeroRange(n).asSequence()
            .flatMap { sequenceOf(Move.get(0, it), Move.get(it, 0)) }
            .flatMap { getSolutions(move(st, it), n, k - 1, prefix + it, bl) }
    } else {
        val base = blackList.last()
        val lastMove = prefix.last()
        val target: Int = if (lastMove.from == base) lastMove.to else lastMove.from

        //TODO: BL!!!!
        val s1: Sequence<List<Move>> = // only same dir
            getSolutions(move(st, lastMove), n, k - 1, prefix + lastMove, blackList)
        val s2: Sequence<List<Move>> = getSameBaseRange(target, n, base)
            .filterNot { blackList.contains(it) }
            .flatMap { sequenceOf(Move.get(base, it), Move.get(it, base)) }
            .flatMap { getSolutions(move(st, it), n, k - 1, prefix + it, blackList) }
        val s3: Sequence<List<Move>> = if (st.getOrDefault(base, 0) != 0)
            emptySequence()
        else {
            val nextBase = st.filterNot { it.value == 0 }.keys.min()
            val newBlackList = blackList + nextBase
            nextBaseRange(nextBase, n)
                .asSequence()
                .filterNot { newBlackList.contains(it) }
                .flatMap { sequenceOf(Move.get(nextBase, it), Move.get(it, nextBase)) }
                .flatMap { getSolutions(move(st, it), n, k - 1, prefix + it, newBlackList) }
        }
        s1 + s2 + s3

    }
}

private fun nextBaseRange(nextBase: Int, n: Int): IntRange {
    if (strict) {
        return ((nextBase - n)..(nextBase + n))
    } else {
        return (max(2, nextBase - n)..min(n + 1, nextBase + n))
    }
}

private fun getSameBaseRange(target: Int, n: Int, base: Int): Sequence<Int> {
    if (strict) {
        if (base == 0) {
            // exclude 1 as a target for 0 (triviality)
            return (target + 1..(base + n)).asSequence().filterNot { it == 1 }
        } else {
            return (target + 1..(base + n)).asSequence()
        }
    } else {
        return (target + 1..min(n + 1, base + n)).asSequence()
    }
}

private fun getZeroRange(n: Int): Sequence<Int> {
    if (strict) {
        return (-n..n).asSequence().filterNot { it == 0 || it == 1 }
    } else {
        return (2..n).asSequence()
    }
}
