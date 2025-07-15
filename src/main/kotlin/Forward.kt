package net.karpelevitch

import com.google.common.collect.Comparators
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource.Monotonic.markNow

fun main(args: Array<String>) {
    var n = args.getOrElse(0) { "1" }.toInt()
    val step = args.getOrElse(1) { "1" }.toInt()
    while (true) {
//        println("n=$n")
        val startTime = markNow()
        val nextN = n + step
        val snn1 = getOptimalSolutions(n, nextN)
        val sn1n = getOptimalSolutions(nextN, n)
        val k1 = snn1[0].size
        snn1.forEachIndexed { i, it ->
            println("NN1 $n move $n to $nextN in $k1 (${i + 1}) ${sig(it)} $it t=${startTime.elapsedNow()}")
        }
        val k2 = sn1n[0].size
        sn1n.forEachIndexed { i, it ->
            println("N1N $n move $nextN to $n in $k2 (${i + 1}) ${sig(it)} $it t=${startTime.elapsedNow()}")
        }

        val isSymmetric = snn1.map(::sig) == sn1n.map(::sig)
        if (!isSymmetric) {
            println("ASYMMETRY $n $k1 $k2 t=${startTime.elapsedNow()}")
        }
        println("RESULT $n $k1 $k2 $isSymmetric t=${startTime.elapsedNow()}")

        n += 1
    }
}

private fun getOptimalSolutions(n: Int, distance: Int): List<List<Move>> {
    for (k in 1..n * (n + 5)) {
        val sol = sort(solve(n, distance, k))
        if (sol.isNotEmpty()) {
            return sol
        }
    }
    throw IllegalStateException("No solution found for $n $distance")
}

public fun sort(s: Sequence<List<Move>>): List<List<Move>> = s.toList().sortedWith(
    compareBy(Comparators.lexicographical(Comparator.naturalOrder()), ::sig)
)

public fun sig(moves: List<Move>) = moves.map { it.to - it.from }.sortedDescending()

private fun solve(n: Int, distance: Int, k: Int): Sequence<List<Move>> =
    sequence {
        val start = markNow()
        // generate all sets ok a1..ak such that a1^2+a2^2+...+ak^2 = n*distance
//        fun moveSets(targetSum: Int, maxA: Int, prefix: List<Int>, k: Int): Sequence<List<Int>> =
//            sequence {
//                if (k == 0) {
//                    if (targetSum == 0) yield(prefix)
//                } else if (k == 1) {
//                    if (targetSum > 0) {
//                        val a: Int = sqrt(targetSum.toFloat() + 0.5).toInt()
//                        if (a <= maxA && targetSum - a * a == 0) yield(prefix + a)
//                    }
//                } else {
//                    (min(maxA, sqrt(targetSum.toFloat() + 0.5).toInt()) downTo 1).asSequence()
//                        .forEach { a ->
//                            val newmaxa =
//                                if (prefix.isEmpty()) min(a, max(n, distance) - a) else a
//                            yieldAll(moveSets(targetSum - a * a, newmaxa, prefix + a, k - 1))
//                        }
//                }
//            }
//
//        val moveSets = moveSets(n * distance, min(n, distance), emptyList(), k).toList()

//        println("moveSets $n $distance $k ${moveSets.size} t=${start.elapsedNow()}")

        var pr = markNow() + 5.minutes

        // for each moveset find first valid set of moves
//        moveSets.forEach { moveset ->
//            println("moveset $n $distance $k $moveset")
            val st = mapOf(0 to n, distance to -n)
            val prefix = emptyList<Move>()
            fun moves(st: Map<Int, Int>, prefix: List<Move>, movesleft: Int, maxA: Int): Sequence<List<Move>> =
                sequence {
                    if (movesleft + 1 >= st.size) {
                        if (movesleft == 0) {
                            yield(prefix)
                        } else if (movesleft == 1) {
                            val from = st.keys.min()
                            val m = st[from]!!
                            if (from + m == distance) {
                                yield(prefix + Move.get(from, distance))
                            }
                        } else {
                            if (pr.hasPassedNow()) {
                                println("processing: $n $distance $k $prefix $movesleft t=${start.elapsedNow()}")
                                pr = markNow() + 5.minutes
                            }
                            val from = st.keys.min()

                            (min(min(maxA,st[from]!!), distance-from) downTo max(1, st[from]!! / movesleft))
                                .asSequence()
                                .forEach {
                                    val m = Move.get(from, from + it)
                                    val newst = move(st, m)
                                    yieldAll(
                                        moves(
                                            newst,
                                            prefix + m,
                                            movesleft - 1,
                                            if (newst.containsKey(from)) it else n
                                        )
                                    )
                                }
                        }
                    }
                }

        yieldAll(moves(st, prefix, k, n).distinctBy { sig(it) })
    }
