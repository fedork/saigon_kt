package net.karpelevitch

import kotlin.math.abs
import kotlin.math.max
import kotlin.time.TimeSource.Monotonic.markNow

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
fun main(args: Array<String>) {
    if (args.isNotEmpty()) {
        if (args[0] == "first") {
            var maxK = 3
            for (n in 1..20000) {
                maxK = max(maxK, printFirstSolution(n, startK = maxK - 2))
            }
        } else {
            val n = args[0].toInt()
            val startK: Int = args.getOrElse(1) { "1" }.toInt()
            printAllSolutions(n, startK)
        }
    } else {
        var maxK = 3
        for (n in 1..20000) {
            maxK = max(maxK, printAllSolutions(n, maxK - 2))
        }
    }
}

private fun printAllSolutions(n: Int, startK: Int = 1): Int {
    val start = markNow()
    val state = State(n)
    for (k in startK..n) {
        var count = 0L;
        val solutions = ArrayList<State>()
        val nextStates = state.nextStates(k)
        nextStates.forEach {
            count++
            println(
                "Can solve $n in $k moves with ($count): /${it.stations}/ ${it.moves} t=${start.elapsedNow()}"
            )
            solutions.add(it)
        }
        if (count > 0L) {
            // verify
            solutions.groupBy {
                if (it.stations.sum() >= 0) it.stations else it.stations.map { 1 - it }.sorted()
            }.filter {
                it.value.size != 2
            }.forEach {
                println("PROBLEM GROUP ${it.key} / ${it.key.map { 1 - it }.sorted()}:")
                it.value.forEach {
                    println("${it.moves}\treversed->\t${it.moves.reversed().map { it.reverse() }}")
                }
            }

            return k
        }
        println("Can't solve $n in $k moves t=${start.elapsedNow()}")
    }
    throw IllegalStateException("Can't solve $n in $n moves")
}


private fun printFirstSolution(n: Int, state: State = State(n), startK: Int): Int {
    val start = markNow()
    for (k in startK..n) {
        val nextStates = state.nextStates(k)
        val solution = nextStates.firstOrNull()
        if (solution != null) {
            println("Can solve $n in $k moves with: ${solution.moves} t=${start.elapsedNow()}")
            require(k == 1 || k > startK)
            return k
        }
//        println("Can't solve $n in $k moves t=${start.elapsedNow()}")
    }
    throw IllegalStateException("Can't solve $n in $n moves")

}

data class Move(val from: Int, val to: Int) : Comparable<Move> {
    val count = abs(from - to)
    override fun toString(): String {
        return "$from->$to(${abs(from - to)})"
    }

    fun reverse() = Move(1 - this.to, 1 - this.from)

    override fun compareTo(other: Move): Int {
        return compareValuesBy(this, other, { it.from }, { it.to })
    }
}

data class State(val state: Map<Int, Int>, val moves: List<Move>, val count: Int) {
    constructor(count: Int) : this(sortedMapOf(Pair(0, count)), emptyList(), count)

    val stations = moves.map(Move::from).distinct().sorted().filterNot { it == 0 || it == 1 }
    val sortedMoves = moves.sorted()

    fun nextStates(movesLeft: Int): Sequence<State> {
        if (movesLeft < state.entries.count { it.key != 1 && it.value > 0 }) {
            return emptySequence() // not enough moves left to move all coins to target
        }
        if (movesLeft == 0) {
            return sequenceOf(this)
        }

        val possibleMoves = state.keys.sortedDescending().flatMap { from ->
            val coinsLeft = state[from]!!
            (from + coinsLeft downTo from - coinsLeft)
                .filterNot { it == from }
                .map { Move(from, it) }
        }

        val moves = possibleMoves.filter { move ->
            val mm = moves + move

            fun permute(l: List<Move>): Sequence<List<Move>> {
                if (l.isEmpty()) return sequenceOf(emptyList())
                return l.indices.asSequence().flatMap { i ->
                    val head = listOf(l[i])
                    val tail = l.subList(0, i) + l.subList(i + 1, l.size)
                    permute(tail).map { head + it }
                }
            }

            permute(mm.sorted()).first {
                val st = mapOf(0 to count).toMutableMap()
                for (m in it) {
                    val fr = st.getOrDefault(m.from, 0) - m.count
                    if (fr < 0) {
                        return@first false
                    }
                    st[m.from] = fr
                    st[m.to] = st.getOrDefault(m.to, 0) + m.count
                }
                true
            }.equals(mm)
        }

        return moves.asSequence()
            .mapNotNull { m ->
                move(m, movesLeft)
            }
            .flatMap { it.nextStates(movesLeft - 1) }
    }

    private fun move(m: Move, movesLeft: Int): State? {
        val newState = state.toMutableMap()
        val coins = m.count
        val destCount = newState.getOrDefault(m.to, 0)

        newState[m.to] = destCount + coins
        val newFromCount = state[m.from]!! - coins
        require(newFromCount >= 0)
        if (newFromCount == 0) {
            newState.remove(m.from)
        } else {
            newState[m.from] = newFromCount
        }
        return if (newState.count { it.key != 1 && it.value > 0 } > movesLeft - 1) {
            null
        } else {
            State(newState.toMap(), this.moves + m, this.count)
        }
    }

}
