package net.karpelevitch

import kotlin.math.abs
import kotlin.math.min
import kotlin.time.TimeSource.Monotonic.markNow

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
fun main(args: Array<String>) {
    if (args.isNotEmpty()) {
        if (args[0] == "first") {
            for (n in 1..20000) {
                printFirstSolution(n)
            }
        } else {
            val n = args[0].toInt()
            val startK: Int = args.getOrElse(1) { "1" }.toInt()
            printAllSolutions(n, startK)
        }
    } else {
        for (n in 1..20000) {
            printAllSolutions(n)
        }
    }
}

private fun printAllSolutions(n: Int, startK: Int = 1) {
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
                it.value.forEach { println(it.moves) }
            }

            break
        }
        println("Can't solve $n in $k moves t=${start.elapsedNow()}")
    }
}

private fun printFirstSolution(n: Int, state: State = State(n)) {
    val start = markNow()
    for (k in 1..n) {
        val nextStates = state.nextStates(k)
        val solution = nextStates.firstOrNull()
        if (solution != null) {
            println("Can solve $n in $k moves with: ${solution.moves} t=${start.elapsedNow()}")
            break
        }
//        println("Can't solve $n in $k moves t=${start.elapsedNow()}")
    }
}

data class State(val state: Map<Int, Int>, val moves: List<Move>) {
    constructor(count: Int) : this(sortedMapOf(Pair(0, count)), emptyList())

    val stations = moves.map(Move::from).distinct().sorted().filterNot { it == 0 }

    data class Move(val from: Int, val to: Int, val distr: Boolean = true) {
        override fun toString(): String {
            return "$from->$to(${abs(from - to)})"
        }
    }

    fun nextStates(movesLeft: Int): Sequence<State> {
//        println("nextStates for $this in $movesLeft")
        if (movesLeft < state.entries.count { it.key != 1 && it.value > 0 }) {
            return emptySequence() // not enough moves left to move all coins to target
        }
        if (movesLeft == 0) {
            return sequenceOf(this)
        }
        val lastMove = moves.lastOrNull()

        return state.keys.sortedDescending().asSequence().flatMap { from ->
            val coinsLeft = state[from]!!
            val start = min(
                (from + coinsLeft),
                moves.filter { it.from == from }.minOfOrNull { it.to } ?: Int.MAX_VALUE)
            (start downTo from - coinsLeft).asSequence()
                .filter { it != from }
                .flatMap { to -> move(from, to, movesLeft - 1, emptySet()) }
                .flatMap { it.nextStates(movesLeft - 1) }
        }

    }

    private fun move(
        from: Int,
        to: Int,
        movesLeft: Int,
        blacklist: Set<Int>,
        distr: Boolean = true
    ): Sequence<State> {
        require(from != to)
//        println("move: $from -> $to, movesLeft: $movesLeft")
        val newState = state.toMutableMap()
        val coins = abs(from - to)

        val destCount = newState.getOrDefault(to, 0)
//        if (destCount > 0 && moves.last { it.to == to }.from > from) {
//            return emptySequence()
//        }
        newState[to] = destCount + coins

        val newFromCount = state[from]!! - coins
        require(newFromCount >= 0)
        if (newFromCount == 0) {
            newState.remove(from)
        } else {
            newState[from] = newFromCount
        }

        if (newState.count { it.key != 1 && it.value > 0 } > movesLeft) {
            return emptySequence()
        }

        return sequenceOf(State(newState.toMap(), moves + Move(from, to, distr)))
    }
}
