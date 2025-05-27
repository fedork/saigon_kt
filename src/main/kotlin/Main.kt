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
            printAllSolutions(n)
        }
    } else {
        for (n in 1..20000) {
            printAllSolutions(n)
        }
    }
}

private fun printAllSolutions(n: Int, state: State = State(n)) {
    val start = markNow()
    for (k in 1..n) {
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

data class State(val state: Map<Int, Int>, val moves: List<Move>, val blacklist: Set<Int>) {
    constructor(count: Int) : this(sortedMapOf(Pair(0, count)), emptyList(), setOf(0))

    val stations = moves.map(State.Move::from).distinct().sorted().filterNot { it == 0 }

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
        if (lastMove != null && lastMove.distr && state.getOrDefault(lastMove.from, 0) > 0) {
            val from = lastMove.from
            val coinsLeft = state[from]!!
//            println("lastMove: $lastMove, coinsLeft: $coinsLeft")
            val start = min(lastMove.to, from + coinsLeft)
            return (start downTo from - coinsLeft).asSequence()
                .filter { to -> to !in blacklist }
                .flatMap { to ->
//                    println("to: $to")
                    move(from, to, movesLeft - 1, blacklist)
                }
                .flatMap { it.nextStates(movesLeft - 1) }
        } else { // lastMove == null || state.getOrDefault(lastMove.from, 0) == 0
//            return state.keys.sorted().asSequence().flatMap { from ->
//                val coinsLeft = state[from]!!
//                val newBlacklist = blacklist + from;
//                (from + coinsLeft downTo from - coinsLeft).asSequence()
//                    .filter { to -> to !in newBlacklist }
//                    .flatMap { to ->
//                        move(from, to, movesLeft - 1, newBlacklist)
//                    }
//                    .flatMap { it.nextStates(movesLeft - 1) }
//            }


            val first = state.keys.filter { it != 1 }.min()
            val coinsLeft = state[first]!!
            val newBlacklist = blacklist + first;
            val s1 = (first + coinsLeft downTo first - coinsLeft).asSequence()
                .filter { it !in newBlacklist }
                .flatMap { move(first, it, movesLeft - 1, newBlacklist) }

            val s2 = state.entries.filter { it.key != 1 }.sortedBy { it.key }.asSequence()
                .drop(1) // skip first
                .dropWhile { lastMove!=null && !lastMove.distr && lastMove.from > it.key } // skip left of the prev move if not distr
                .filter { abs(it.key - first) <= it.value } // has enough coins to move to first
                .flatMap { move(it.key, first, movesLeft - 1, blacklist, false) }


            return (s1 + s2).flatMap { it.nextStates(movesLeft - 1) }
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

        return sequenceOf(State(newState.toMap(), moves + Move(from, to, distr), blacklist))
    }
}
