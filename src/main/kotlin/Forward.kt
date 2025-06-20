package net.karpelevitch

import com.google.common.collect.Comparators
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource.Monotonic.markNow

fun main(args: Array<String>) {
    var n = args.getOrElse(0) { "1" }.toInt()
    while (true) {
//        println("n=$n")
        val startTime = markNow()
        val snn1 = getOptimalSolutions(n, n + 1)
        val sn1n = getOptimalSolutions(n + 1, n)
        val k1 = snn1[0].size
        snn1.forEachIndexed { i, it ->
            println("NN1 $n move $n to ${n + 1} in $k1 (${i + 1}) ${sig(it)} $it t=${startTime.elapsedNow()}")
        }
        val k2 = sn1n[0].size
        sn1n.forEachIndexed { i, it ->
            println("N1N $n move ${n + 1} to $n in $k2 (${i + 1}) ${sig(it)} $it t=${startTime.elapsedNow()}")
        }

        val isSymmetric = snn1.map(::sig) == sn1n.map(::sig)
        if (!isSymmetric) {
            println("ASYMMETRY $n $k1 $k2 t=${startTime.elapsedNow()}")
        }
        println("RESULT $n $k1 $k2 $isSymmetric t=${startTime.elapsedNow()}")

        n++
    }
}

private fun getOptimalSolutions(n: Int, distance: Int): List<List<Move>> {
    for (k in 1..n * (n + 1)) {
        val sol = sort(solve(n, distance, k))
        if (sol.isNotEmpty()) {
            return sol
        }
    }
    throw IllegalStateException("No solution found for $n $distance")
}

private fun sort(s: Sequence<List<Move>>): List<List<Move>> = s.toList().sortedWith(
    compareBy(Comparators.lexicographical(Comparator.naturalOrder()), ::sig)
)

private fun sig(moves: List<Move>) = moves.map { it.count }.sortedDescending()

private fun solve(n: Int, distance: Int, k: Int): Sequence<List<Move>> =
    sequence {
        val start = markNow()
        // generate all sets ok a1..ak such that a1^2+a2^2+...+ak^2 = n*distance
        fun moveSets(targetSum: Int, maxA: Int, prefix: List<Int>, k: Int): Sequence<List<Int>> =
            sequence {
                if (k == 0) {
                    if (targetSum == 0) yield(prefix)
                } else if (k == 1) {
                    if (targetSum > 0) {
                        val a: Int = sqrt(targetSum.toFloat() + 0.5).toInt()
                        if (a <= maxA && targetSum - a * a == 0) yield(prefix + a)
                    }
                } else {
                    (min(maxA, sqrt(targetSum.toFloat() + 0.5).toInt()) downTo 1).asSequence()
                        .forEach { a ->
                            val newmaxa =
                                if (prefix.isEmpty()) min(a, max(n, distance) - a) else a
                            yieldAll(moveSets(targetSum - a * a, newmaxa, prefix + a, k - 1))
                        }
                }
            }

        val moveSets = moveSets(n * distance, min(n, distance), emptyList(), k).toList()

        println("moveSets $n $distance $k ${moveSets.size} t=${start.elapsedNow()}")

        var pr = markNow() + 5.minutes

        // for each moveset find first valid set of moves
        moveSets.forEach { moveset ->
//            println("moveset $n $distance $k $moveset")
            val st = mapOf(0 to n, distance to -n)
            val prefix = emptyList<Move>()
            fun moves(
                st: Map<Int, Int>,
                prefix: List<Move>,
                movesleft: List<Int>,
                maxA: Int,
            ): Sequence<List<Move>> =
                sequence {
                    if (movesleft.size + 1 >= st.size) {
                        if (movesleft.isEmpty()) {
                            yield(prefix)
                        } else if (movesleft.size == 1) {
                            val from = st.keys.min()
                            val m = movesleft.first()
                            if (from + m == distance && st[from]!! == m) {
                                yield(prefix + Move.get(from, distance))
                            }
                        } else {
                            if (pr.hasPassedNow()) {
                                println("processing: $n $distance $k $moveset $movesleft t=${start.elapsedNow()}")
                                pr = markNow() + 5.minutes
                            }
                            val from = st.keys.min()

                            movesleft.distinct()
                                .filter { it <= distance - from && it <= st[from]!! && it <= maxA }
                                .asSequence()
                                .forEach {
                                    val m = Move.get(from, from + it)
                                    val newst = move(st, m)
                                    yieldAll(
                                        moves(
                                            newst,
                                            prefix + m,
                                            movesleft - it,
                                            if (newst.containsKey(from)) it else n
                                        )
                                    )
                                }
                        }
                    }
                }

            moves(st, prefix, moveset, n).firstOrNull()?.let {
                yield(it)
            }
        }
    }
