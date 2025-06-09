package net.karpelevitch

import java.io.File
import kotlin.math.abs

fun main(args: Array<String>) {
    val n = 20

    val digits = (n + 1).toString().length
    print("mv${" ".repeat(digits*2)}")
    for (i in 0 until n + 2) {
        print(",${String.format("%${digits}d", i)}")
    }
    println()
    File("out_full4.txt").forEachLine { line ->
        if (line.startsWith("Found solution for $n in") && !line.contains("->-")) {
//            val k = line.substring(2).toInt()
            val state = IntArray(n + 2) { 0 }
            state[0] = n
            val lastOpenBracket = line.lastIndexOf('[')
            val lastCloseBracket = line.lastIndexOf(']')
            if (lastOpenBracket != -1 && lastCloseBracket != -1) {
                //                println(moves)
                val moves = line.substring(lastOpenBracket + 1, lastCloseBracket).split(", ")
                moves.forEach { move ->

                    val parts = move.split("->", "(")
                    val from = parts[0].toInt()
                    val to = parts[1].toInt()
                    print("${String.format("%${digits}d", from)}->${String.format("%${digits}d", to)}")
                    val count = abs(from - to)
                    state[to] += count
                    state[from] -= count
                    for (c in state) {
                        print(",${String.format("%${digits}d", c)}")
                    }
                    println()
                }
                println()
            }
        }
    }
}