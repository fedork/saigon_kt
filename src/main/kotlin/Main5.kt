package net.karpelevitch

import kotlin.math.max
import kotlin.math.min
import kotlin.time.TimeSource.Monotonic.markNow

fun main(args: Array<String>) {

    for (n in 0..120) {
        val start = markNow()
        var k = 0
        var solutions: List<List<Move>>
        while (true) {
            solutions = getSolutions(n, k)
            if (solutions.isNotEmpty()) break
            println("RESULT can't solve $n in $k moves t=${start.elapsedNow()}")
            k++
        }
        getValidSoutions(n, k, solutions).forEachIndexed { i, it ->
            val stations = it.map { it.to }.filterNot { it == 1 || it == 0 }.distinct().sortedByDescending { Math.abs(it) }
            println("can solve $n in $k moves with (${i+1}) $stations / $it")
        }
        println("RESULT can solve $n in $k moves t=${start.elapsedNow()}")
    }
}

private data class Sol(
    var maxTried: Int = 0,
    var canSolve: Int? = null,
    var solutions: List<List<Move>>? = null
)

private val sol = mutableMapOf<Int, Sol>()

fun getSolutions(n: Int, k: Int): List<List<Move>> {
    val s = sol.getOrPut(n, { Sol() })
    val solutions = mutableListOf<List<Move>>()

    if (n == 0) {
        solutions.add(emptyList())
    } else {
        if (s.maxTried >= k) {
            if (k < (s.canSolve ?: Int.MAX_VALUE)) {
                return emptyList()
            }
            return s.solutions!!
        }
        // add trivial solutions
        getSolutions(n - 1, k - 1).forEach {
            solutions.add(listOf(Move(0, 1)) + it)
        }
        getSolutions(n + 1, k - 1).forEach {
            if (it.none { it.count > n }) {
                solutions.add(listOf(Move(1, 0)) + it)
            }
        }
        // add other solutions
        val initState = mapOf(Pair(0, n), Pair(1, -n))

        solutions.addAll(getSolutions(initState, n, k))
    }



    s.maxTried = k
    if (solutions.isNotEmpty()) {
        s.canSolve = k
        s.solutions = solutions
    }

    return solutions
}

private fun getValidSoutions(n: Int, k: Int, solutions: List<List<Move>>): List<List<Move>> {
    fun getValidSeq(st: Map<Int, Int>, head: List<Move>, tail: List<Move>): Sequence<List<Move>> {
        if (tail.isEmpty()) {
            require(st.all { it.value == if (it.key == 1) n else 0 })
            return sequenceOf(head)
        }
        return tail.asSequence()
            .filter { st.getOrDefault(it.from, 0) >= it.count }
            .flatMap { getValidSeq(move(st, it), head + it, tail - it) }
    }

    return solutions.mapNotNull { ss ->
        val validS = getValidSeq(mapOf(Pair(0, n)), listOf(), ss).firstOrNull()
        if (validS == null) {
            println("INVALID solution for $n in $k: $ss")
        }
        validS
    }
}

fun move(st: Map<Int, Int>, move: Move): Map<Int, Int> {
    val mm = st.toMutableMap()
    mm.compute(move.from) { _, v -> ((v?:0) - move.count) }
    mm.compute(move.to) { _, v -> ((v?:0) + move.count) }
    return mm.filterValues { it != 0 }
}

fun getSolutions(
    st: Map<Int, Int>,
    n: Int,
    k: Int,
    prefix: List<Move> = emptyList(),
    blackList: List<Int> = emptyList()
): Sequence<List<Move>> {
    val nonZeros = st.count { it.value != 0 }
    if (nonZeros > k * 2) return emptySequence()
    if (k == 0) {
        return sequenceOf(prefix)
    }
    return if (blackList.isEmpty()) {
        // start with 2 since 1 is trivial (already handled)
        val bl = listOf(0)
        (2..n).asSequence()
            .flatMap { sequenceOf(Move.get(0, it), Move.get(it, 0)) }
            .flatMap { getSolutions(move(st, it), n, k - 1, prefix + it, bl) }
    } else {
        val base = blackList.last()
        val lastMove = prefix.last()
        val target: Int = if (lastMove.from == base) lastMove.to else lastMove.from

        //TODO: BL!!!!
        val s1: Sequence<List<Move>> = // only same dir
            getSolutions(move(st, lastMove), n, k - 1, prefix + lastMove, blackList)
        val s2: Sequence<List<Move>> = (target + 1..min(n + 1, base + n)).asSequence()
            .filterNot { blackList.contains(it) }
            .flatMap { sequenceOf(Move.get(base, it), Move.get(it, base)) }
            .flatMap { getSolutions(move(st, it), n, k - 1, prefix + it, blackList) }
        val s3: Sequence<List<Move>> = if (st.getOrDefault(base, 0) != 0)
            emptySequence()
        else {
            val nextBase = st.filterNot { it.value == 0 }.keys.min()
            val newBlackList = blackList + nextBase
            (max(2, nextBase - n)..min(n + 1, nextBase + n))
                .asSequence()
                .filterNot { newBlackList.contains(it) }
                .flatMap { sequenceOf(Move.get(nextBase, it), Move.get(it, nextBase)) }
                .flatMap { getSolutions(move(st, it), n, k - 1, prefix + it, newBlackList) }
        }
        s1 + s2 + s3

    }
}
