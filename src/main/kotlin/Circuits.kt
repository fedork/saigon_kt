import org.apache.commons.math3.fraction.Fraction

/**
 * Represents a circuit configuration
 */
data class CircuitConfig(
    val topology: CircuitTopology,
    val resistances: List<Int> // +1 or -1 for each resistor
) {
    fun calculateResistance(): Fraction = topology.calculateResistance(resistances)
}

/**
 * Abstract circuit topology
 */
sealed class CircuitTopology {
    abstract fun calculateResistance(resistances: List<Int>): Fraction
    abstract fun getResistorCount(): Int
}

/**
 * Series connection of components
 */
data class SeriesTopology(val components: List<CircuitTopology>) : CircuitTopology() {
    override fun calculateResistance(resistances: List<Int>): Fraction {
        var resistance = Fraction.ZERO
        var resistorIndex = 0

        for (component in components) {
            val componentResistorCount = component.getResistorCount()
            val componentResistances = resistances.subList(resistorIndex, resistorIndex + componentResistorCount)
            resistance = resistance.add(component.calculateResistance(componentResistances))
            resistorIndex += componentResistorCount
        }

        return resistance
    }

    override fun getResistorCount(): Int = components.sumOf { it.getResistorCount() }
}

/**
 * Parallel connection of components
 */
data class ParallelTopology(val components: List<CircuitTopology>) : CircuitTopology() {
    override fun calculateResistance(resistances: List<Int>): Fraction {
        var conductance = Fraction.ZERO
        var resistorIndex = 0

        for (component in components) {
            val componentResistorCount = component.getResistorCount()
            val componentResistances = resistances.subList(resistorIndex, resistorIndex + componentResistorCount)
            val componentResistance = component.calculateResistance(componentResistances)

            // Handle zero resistance (infinite conductance)
            if (componentResistance.numerator == 0) {
                return Fraction.ZERO
            }

            conductance = conductance.add(Fraction.ONE.divide(componentResistance))
            resistorIndex += componentResistorCount
        }

        // Handle zero conductance (infinite resistance)
        return if (conductance.numerator == 0) {
            null // Return null for infinite resistance - caller should handle
            Fraction.ZERO // Placeholder
        } else {
            Fraction.ONE.divide(conductance)
        }
    }

    override fun getResistorCount(): Int = components.sumOf { it.getResistorCount() }
}

/**
 * Single resistor
 */
object SingleResistorTopology : CircuitTopology() {
    override fun calculateResistance(resistances: List<Int>): Fraction {
        require(resistances.size == 1) { "Single resistor needs exactly one resistance value" }
        return Fraction(resistances[0])
    }

    override fun getResistorCount(): Int = 1
}

/**
 * Generate all integer partitions of n
 */
fun generatePartitions(n: Int): List<List<Int>> {
    if (n == 0) return listOf(emptyList())
    if (n == 1) return listOf(listOf(1))

    val result = mutableListOf<List<Int>>()

    for (i in 1..n) {
        for (partition in generatePartitions(n - i)) {
            if (partition.isEmpty() || i <= partition[0]) {
                result.add(listOf(i) + partition)
            }
        }
    }

    return result
}

/**
 * Generate all series-parallel topologies with k resistors
 */
fun generateSPTopologies(k: Int): Set<CircuitTopology> {
    if (k == 1) return setOf(SingleResistorTopology)

    val result = mutableSetOf<CircuitTopology>()

    // Generate partitions for series combinations
    for (partition in generatePartitions(k)) {
        if (partition.size > 1) { // Only create series if more than one component
            val components = partition.map { generateSPTopologies(it) }
            for (combination in cartesianProduct(components)) {
                result.add(SeriesTopology(combination))
            }
        }
    }

    // Generate partitions for parallel combinations
    for (partition in generatePartitions(k)) {
        if (partition.size > 1) { // Only create parallel if more than one component
            val components = partition.map { generateSPTopologies(it) }
            for (combination in cartesianProduct(components)) {
                result.add(ParallelTopology(combination))
            }
        }
    }

    return result
}

/**
 * Cartesian product of lists of sets
 */
fun <T> cartesianProduct(listOfSets: List<Set<T>>): List<List<T>> {
    if (listOfSets.isEmpty()) return listOf(emptyList())
    if (listOfSets.size == 1) return listOfSets[0].map { listOf(it) }

    val result = mutableListOf<List<T>>()
    val first = listOfSets[0]
    val rest = cartesianProduct(listOfSets.drop(1))

    for (item in first) {
        for (combination in rest) {
            result.add(listOf(item) + combination)
        }
    }

    return result
}

/**
 * Generate all resistance assignments for k resistors
 */
fun generateResistanceAssignments(k: Int): List<List<Int>> {
    if (k == 0) return listOf(emptyList())
    if (k == 1) return listOf(listOf(1), listOf(-1))

    val result = mutableListOf<List<Int>>()
    for (assignment in generateResistanceAssignments(k - 1)) {
        result.add(assignment + 1)
        result.add(assignment + (-1))
    }

    return result
}

/**
 * Find all unique circuit configurations with k resistors
 */
fun findUniqueCircuits(k: Int): Map<Fraction, CircuitConfig> {
    val topologies = generateSPTopologies(k)
    val resistanceAssignments = generateResistanceAssignments(k)
    val uniqueCircuits = mutableMapOf<Fraction, CircuitConfig>()

    println("Generated ${topologies.size} topologies and ${resistanceAssignments.size} resistance assignments")

    for (topology in topologies) {
        for (assignment in resistanceAssignments) {
            try {
                val config = CircuitConfig(topology, assignment)
                val resistance = config.calculateResistance()

                // Only keep first occurrence of each resistance value
                if (!uniqueCircuits.containsKey(resistance)) {
                    uniqueCircuits[resistance] = config
                }
            } catch (e: Exception) {
                // Skip configurations that lead to invalid calculations
                continue
            }
        }
    }

    return uniqueCircuits
}

/**
 * Find minimum moves needed to achieve target resistance
 */
fun findMinimumMoves(targetResistance: Fraction, maxMoves: Int): Pair<Int, CircuitConfig>? {
    for (k in 1..maxMoves) {
        println("Trying $k moves...")
        val circuits = findUniqueCircuits(k)

        if (circuits.containsKey(targetResistance)) {
            return Pair(k, circuits[targetResistance]!!)
        }

        println("  Found ${circuits.size} unique resistances with $k moves")
        if (circuits.size <= 20) {
            println("  Resistances: ${circuits.keys.sorted()}")
        }
    }

    return null
}

// Example usage
fun main() {
    println("=== Circuit Enumeration for Coin Movement ===\n")

    // Example: Move 6 coins to position 4
    val targetResistance = Fraction(4, 6) // = 2/3
    println("Target resistance: $targetResistance")

    val result = findMinimumMoves(targetResistance, 5)

    if (result != null) {
        val (moves, config) = result
        println("\nSolution found!")
        println("Minimum moves: $moves")
        println("Configuration: $config")
        println("Resistance: ${config.calculateResistance()}")
    } else {
        println("\nNo solution found within the move limit")
    }

    // Show what's possible with small numbers of moves
    println("\n=== Analysis of small move counts ===")
    for (k in 1..10) {
        println("\nWith $k moves:")
        val circuits = findUniqueCircuits(k)
        //        println("Unique resistances (${sortedResistances.size}): $sortedResistances")
        println("1/n resistances: ${circuits.keys.filter { it.numerator == 1 }.sorted()}")
    }
}