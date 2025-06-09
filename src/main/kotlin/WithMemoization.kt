package net.karpelevitch

import kotlin.math.abs
import kotlin.math.max
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource.Monotonic.markNow

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
fun main(args: Array<String>) {
    if (args.isNotEmpty()) {
        if (args[0] == "first") {
            var maxK = 3
            for (n in 1..20000) {
                maxK = max(maxK, findSolution(n))
            }
        } else {
            val n = args[0].toInt()
            val startK: Int = args.getOrElse(1) { "1" }.toInt()
            findSolution(n, false)
//            printAllSolutions(n, startK)
        }
    } else {
        var maxK = 3
        for (n in 1..20000) {
            maxK = max(maxK, findSolution(n, false))
        }
    }
}

data class Seen(val steps: Int, val paths: MutableList<Pair<Move, St>> = mutableListOf())

fun pathsTo(from: St, seen: Map<St, Seen>) : Sequence<List<Move>> {
    val paths = seen.get(from)!!.paths
    if (paths.isEmpty()) {
        return sequenceOf(emptyList())
    }
    return paths.asSequence().flatMap { (move, prev) ->
        pathsTo(prev, seen).map {it + move}
    }
}

private fun findSolution(n: Int, firstOnly: Boolean = true): Int {
    val start = markNow()

    var k: Int = 1


    val init = St(mapOf(Pair(0, n)))
    val seen: MutableMap<St, Seen> = mutableMapOf(Pair(init, Seen(0)))
    var current = listOf(init)
    var solved = mutableMapOf<List<Move>, List<Move>>()
    var solutions = mutableSetOf<St>(init.invert())
    var nextPrint = start + 1.minutes
//    println("Finding solution for $n")
    while (true) {
//        println("k=$k")
        val next: List<Triple<St, Move, St>> = current.flatMap { st ->
            st.s.entries.flatMap { e ->
                val removed = st.s.filterNot { it.key == e.key }
                ((1 until e.value).asSequence().flatMap {
                    val s: Map<Int, Int> = st.s + Pair(e.key, e.value - it)
                    sequenceOf(
                        Pair(
                            Move(e.key, e.key - it),
                            St(s + Pair(e.key - it, s.getOrDefault(e.key - it, 0) + it))
                        ), Pair(
                            Move(e.key, e.key + it),
                            St(s + Pair(e.key + it, s.getOrDefault(e.key + it, 0) + it))
                        )
                    )
                } +
                        Pair(
                            Move(e.key, e.key - e.value),
                            St(
                                (removed + Pair(
                                    e.key - e.value,
                                    st.s.getOrDefault(e.key - e.value, 0) + e.value
                                ))
                            )
                        ) +
                        Pair(
                            Move(e.key, e.key + e.value),
                            St(
                                (removed + Pair(
                                    e.key + e.value,
                                    st.s.getOrDefault(e.key + e.value, 0) + e.value
                                ))
                            )
                        )
                        ).map { (move, to) ->
                        Triple(st, move, to)
                    }
            }
        }

//        println("Next states: ${next.size}")
        val nextStates = mutableListOf<St>()
        val solvedPoints = mutableListOf<Triple<St, Move, St>>()
        next.forEach { (from, move, to) ->
//            println("From: $from To: $to Move: $move")
            val s: Seen? = seen.get(to)
            if (s == null) {
                seen[to] = Seen(k+1, mutableListOf(Pair(move, from)))
                nextStates.add(to)
                if (solutions.contains(to)) {
                    solvedPoints.add(Triple(from, move, to))
                    val path = pathsTo(from, seen).first() + move + reverseMoves(pathsTo(to.invert(), seen).first())
                    println("SOLVED $n in $k with $path moves t=${start.elapsedNow()}")
                    if (firstOnly) {
                        return k
                    }
                }
            } else if (s.steps == k+1) {
                s.paths.add(Pair(move, from))
                if (solutions.contains(to)) {
                    solvedPoints.add(Triple(from, move, to))
                    if (firstOnly) {
                        val path = pathsTo(from, seen).first() + move + reverseMoves(pathsTo(to.invert(), seen).first())
                        println("SOLVED $n in $k with $path moves t=${start.elapsedNow()}")
                        return k
                    }
                }
            }
            if (nextPrint.hasPassedNow()) {
                println("Found ${nextStates.size} next states for ${k/2} solved: ${solvedPoints.size}")
                nextPrint = markNow() + 1.minutes
            }
        }
        println("for $k total solved: ${solvedPoints.size}")
        solvedPoints.forEach { (from, move, to) ->
            pathsTo(from, seen).flatMap { head ->
                pathsTo(to.invert(), seen).map(::reverseMoves).map { tail ->
                    head  + move + tail
                }
            }.forEach { path ->
                solved.putIfAbsent(path.sorted(), path)
            }
        }

        if (solved.isEmpty()) {
            k++
            println("Found ${nextStates.size} next states for ${k/2}")
            solutions.clear()
            nextStates.forEach { st1 ->
                solutions.add(st1.invert())
                if (solutions.contains(st1)) {
                    pathsTo(st1, seen).flatMap { head ->
                        pathsTo(st1.invert(), seen).flatMap { tail ->
                            sequenceOf(head + reverseMoves(tail), tail + reverseMoves(head))
                        }
                    }.forEach { path ->
                        println("SOLVED $n in $k with $path moves t=${start.elapsedNow()}")
                        if (firstOnly) {
                            return k
                        }
                        solved.putIfAbsent(path.sorted(), path)
                    }
                }
                if (nextPrint.hasPassedNow()) {
                    println("Processed ${solutions.size}/${nextStates.size} solutions for $n in $k solved: ${solved.size}")
                    nextPrint = markNow() + 1.minutes
                }
            }
        }

        if (solved.isNotEmpty()) {
            solved.values
                .map { Pair(it, it.map{it.to}.filterNot { it == 1 || it == 0 }.distinct().sortedByDescending { abs(it) })}
                .sortedByDescending { it.second.maxBy { abs(it) }}
                .forEachIndexed { i, (path, stations) ->
                    println("Found solution for $n in $k with (${i+1}) ${stations} / $path t=${start.elapsedNow()}")
                }
            return k
        }
        current = nextStates
        k++;
    }
}

private fun reverseMoves(tail: List<Move>): List<Move> = tail.reversed().map { it.reverse() }

data class St(val s: Map<Int, Int>) {
    fun invert(): St = St(s.mapKeys { 1 - it.key })
}
