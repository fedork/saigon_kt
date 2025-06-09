package net.karpelevitch

import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource.Monotonic.markNow
import kotlin.time.measureTime


fun main(args: Array<String>) {
    prinatAll(7)
    for (n in 1..120) {
        prinatAll(n)
    }
}

private fun prinatAll(n: Int) {
    val st = St1(n)
    var solved = false
    val start = markNow()
    val time = measureTime {
        var k = 1
        while (!solved) {
            st.nextStates(k).forEach {
                solved = true
                println("can solve $n in $k with $it t=${start.elapsedNow()}")
            }
            if (!solved) {
                println("Can't solve $n in $k moves t=${start.elapsedNow()}")
                k++
            }
        }
    }
    println("Took $time")
}

private class St1(val st: IntArray, val moves: List<Int> = emptyList(), val moveList: List<Move>) {

    companion object {
        var printNext = markNow() + 1.minutes
    }

    constructor(n: Int) : this(
        IntArray(n + 2) { if (it == 0) n else 0 },
        emptyList(),
        (0..n + 1).flatMap { from ->
            (max(0, from - n)..min(n + 1, from + n))
                .filterNot { it == from }
                .map { to -> Move.get(from, to) }
        })

    override fun toString(): String {
        return "${moves.map { moveList[it] }}"
    }

    fun nextStates(movesLeft: Int): Sequence<St1> {
        if (movesLeft * 2 < st.mapIndexed { i, v -> if (i != 1 && v != 0) 1 else 0 }.sum()) {
            return emptySequence()
        }
        if (movesLeft == 0) {
            return sequenceOf(this)
        }
        val lastMoveIdx = moves.lastOrNull() ?: 0
        val lastMove = moveList[lastMoveIdx]
        if (lastMove.from != 0 && st[0] > 0) {
            return emptySequence()
        }
        return (lastMoveIdx until moveList.size).asSequence().flatMap { idx ->
            val move = moveList[idx]
            val newSt = st.clone()
            newSt[move.from] -= move.count
            newSt[move.to] += move.count
            val newMoves = moves + idx
            val st1 = St1(newSt, newMoves, moveList)
            if (printNext.hasPassedNow()) {
                println("Processing: $st1")
                printNext = markNow() + 1.minutes
            }
            st1.nextStates(movesLeft - 1)
        }
    }
}