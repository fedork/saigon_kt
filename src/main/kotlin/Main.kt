package org.example

import kotlin.math.abs
import kotlin.math.min

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
    for (k in 1..n) {
        val startTime = System.currentTimeMillis()
        val nextStates = state.nextStates(k)
        var count = 0L;
        nextStates.forEach {
            count++
            println("Can solve $n in $k moves with: ${it.moves} t=${System.currentTimeMillis() - startTime}ms")
        }
        if (count > 0L) break
        println("Can't solve $state in $k moves t=${System.currentTimeMillis() - startTime}ms")
    }
}

private fun printFirstSolution(n: Int, state: State = State(n)) {

    for (k in 1..n) {
        val startTime = System.currentTimeMillis()

        val nextStates = state.nextStates(k)
        val solution = nextStates.firstOrNull()
        if (solution != null) {
            println("Can solve $n in $k moves with: ${solution.moves} t=${System.currentTimeMillis() - startTime}ms")
            break
        }
        println("Can't solve $state in $k moves t=${System.currentTimeMillis() - startTime}ms")

    }
}

data class State(val state: Map<Int, Int>, val moves: List<Move>, val blacklist: Set<Int>) {
    constructor(count: Int) : this(sortedMapOf(Pair(0, count)), emptyList(), setOf(0))

    data class Move(val from: Int, val to: Int) {
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
        if (lastMove != null && state.getOrDefault(lastMove.from, 0) > 0) {
            val from = lastMove.from
            val coinsLeft = state[from]!!
//            println("lastMove: $lastMove, coinsLeft: $coinsLeft")
            return (min(lastMove.to, from + coinsLeft) downTo from - coinsLeft).asSequence()
                .filter { to -> to !in blacklist }
                .flatMap { to ->
//                    println("to: $to")
                    move(from, to, movesLeft - 1, blacklist)
                }
                .flatMap { it.nextStates(movesLeft - 1) }
        } else { // lastMove == null || state.getOrDefault(lastMove.from, 0) == 0
            return state.keys.sorted().asSequence().flatMap { from ->
                val coinsLeft = state[from]!!
                val newBlacklist = blacklist + from;
//                println("from: $from, coinsLeft: $coinsLeft, newBlacklist: $newBlacklist")
                (from + coinsLeft downTo from - coinsLeft).asSequence()
                    .filter { to -> to !in newBlacklist }
                    .flatMap { to ->
//                        println("to: $to")
                        move(from, to, movesLeft - 1, newBlacklist)
                    }
                    .flatMap { it.nextStates(movesLeft - 1) }
            }
        }
    }

    private fun move(from: Int, to: Int, movesLeft: Int, blacklist: Set<Int>): Sequence<State> {
        require(from != to)
//        println("move: $from -> $to, movesLeft: $movesLeft")
        val newState = state.toMutableMap()
        val coins = abs(from - to)
        val newFromCount = state[from]!! - coins
        require(newFromCount >= 0)
        if (newFromCount == 0) {
            newState.remove(from)
        } else {
            newState[from] = newFromCount
        }
        newState[to] = newState.getOrDefault(to, 0) + coins
        if (newState.count { it.key != 1 && it.value > 0 } > movesLeft) {
            return emptySequence()
        }

        return sequenceOf(State(newState.toMap(), moves + Move(from, to), blacklist))
    }
}
