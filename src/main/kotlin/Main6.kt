import org.apache.commons.math3.fraction.Fraction
import org.apache.commons.math3.linear.*

/**
 * Calculate exact resistance between source (index 0) and sink (last index)
 * @param resistanceMatrix - adjacency matrix where resistanceMatrix[i][j] is the resistance
 *                          between nodes i and j (0 means no connection, positive/negative
 *                          integers are resistances)
 * @return exact resistance as Fraction, or null if infinite/undefined
 */
fun calculateExactResistance(resistanceMatrix: Array<IntArray>): Fraction? {
    val n = resistanceMatrix.size
    require(n >= 2) { "Need at least 2 nodes" }

    val source = 0
    val sink = n - 1

    // Handle trivial case: direct connection
    if (n == 2) {
        val resistance = resistanceMatrix[source][sink]
        return if (resistance != 0) Fraction(resistance) else null
    }

    // Build conductance matrix G (exclude sink node as reference)
    val numNodes = n - 1
    val gData = Array(numNodes) { Array(numNodes) { Fraction.ZERO } }

    for (i in 0 until numNodes) {
        // Map reduced matrix index to original node index
        val originalI = if (i < sink) i else i + 1

        // Calculate diagonal element: sum of all conductances connected to this node
        var diagonalSum = Fraction.ZERO
        for (j in 0 until n) {
            if (j != originalI && resistanceMatrix[originalI][j] != 0) {
                val resistance = Fraction(resistanceMatrix[originalI][j])
                val conductance = Fraction.ONE.divide(resistance)
                diagonalSum = diagonalSum.add(conductance)
            }
        }
        gData[i][i] = diagonalSum

        // Calculate off-diagonal elements: negative conductances between nodes
        for (j in 0 until numNodes) {
            if (i != j) {
                val originalJ = if (j < sink) j else j + 1
                if (resistanceMatrix[originalI][originalJ] != 0) {
                    val resistance = Fraction(resistanceMatrix[originalI][originalJ])
                    val conductance = Fraction.ONE.divide(resistance)
                    gData[i][j] = conductance.negate()
                }
            }
        }
    }

    // Create conductance matrix
    val conductanceMatrix: FieldMatrix<Fraction> = Array2DRowFieldMatrix(gData)

    // Build current injection vector
    val currentData = Array(numNodes) { Fraction.ZERO }
    val sourceIndex = if (source < sink) source else source - 1
    currentData[sourceIndex] = Fraction.ONE
    val currentVector: FieldVector<Fraction> = ArrayFieldVector(currentData)

    // Solve G × V = I using LU decomposition
    return try {
        val decomposition = FieldLUDecomposition(conductanceMatrix)
        if (!decomposition.solver.isNonSingular) {
            null // Singular matrix (infinite resistance)
        } else {
            val voltages = decomposition.solver.solve(currentVector)
            // Resistance = (V_source - V_sink) / I_injected
            // Since sink is reference (0V) and current is 1A:
            voltages.getEntry(sourceIndex)
        }
    } catch (e: SingularMatrixException) {
        null // Matrix is singular
    }
}

// Helper function to create resistance matrix from adjacency list format
fun createResistanceMatrix(connections: List<Triple<Int, Int, Int>>, numNodes: Int): Array<IntArray> {
    val matrix = Array(numNodes) { IntArray(numNodes) { 0 } }

    for ((from, to, resistance) in connections) {
        matrix[from][to] = resistance
        matrix[to][from] = resistance // Assume symmetric (undirected graph)
    }

    return matrix
}

// Example usage and testing
fun main() {
    println("=== Exact Resistance Calculator (Apache Commons Math) ===\n")

    // Example 1: Simple series circuit A-B-C
    println("Example 1: Series circuit A-B-C (resistances 2Ω, 3Ω)")
    val seriesCircuit = arrayOf(
        intArrayOf(0, 2, 0),  // A connects to B with 2Ω
        intArrayOf(2, 0, 3),  // B connects to A with 2Ω, C with 3Ω
        intArrayOf(0, 3, 0)   // C connects to B with 3Ω
    )
    val seriesResult = calculateExactResistance(seriesCircuit)
    println("Resistance A to C: $seriesResult")
    println("As decimal: ${seriesResult?.toDouble()}")
    println()

    // Example 2: Parallel circuit using helper function
    println("Example 2: Parallel paths A to C")
    val parallelConnections = listOf(
        Triple(0, 1, 1),  // A to B: 1Ω
        Triple(0, 2, 2),  // A to C: 2Ω (direct path)
        Triple(1, 2, 3)   // B to C: 3Ω
    )
    val parallelCircuit = createResistanceMatrix(parallelConnections, 3)
    val parallelResult = calculateExactResistance(parallelCircuit)
    println("Resistance A to C: $parallelResult")
    println("As decimal: ${parallelResult?.toDouble()}")
    println()

    // Example 3: Circuit with negative resistance
    println("Example 3: Circuit with negative resistance")
    val negativeConnections = listOf(
        Triple(0, 1, 1),   // A to B: +1Ω
        Triple(0, 3, -2),  // A to D: -2Ω (negative resistance)
        Triple(1, 2, 1),   // B to C: +1Ω
        Triple(2, 3, 1)    // C to D: +1Ω
    )
    val negativeCircuit = createResistanceMatrix(negativeConnections, 4)
    val negativeResult = calculateExactResistance(negativeCircuit)
    println("Resistance A to D: $negativeResult")
    println("As decimal: ${negativeResult?.toDouble()}")
    println()

    // Example 4: Your original diamond circuit
    println("Example 4: Diamond circuit (all 1Ω resistors)")
    val diamondConnections = listOf(
        Triple(0, 1, 1),  // A to B: 1Ω
        Triple(0, 3, 1),  // A to D: 1Ω
        Triple(1, 2, 1),  // B to C: 1Ω
        Triple(2, 3, 1)   // C to D: 1Ω
    )
    val diamondCircuit = createResistanceMatrix(diamondConnections, 4)
    val diamondResult = calculateExactResistance(diamondCircuit)
    println("Resistance A to D: $diamondResult")
    println("As decimal: ${diamondResult?.toDouble()}")
    println()

    // Example 5: Test with coin movement scenario
    println("Example 5: Coin movement test")
    println("Target: Move 6 coins to position 4 → need R = 4/6 = 2/3")
    val targetResistance = Fraction(4, 6)
    println("Target resistance: $targetResistance")

    // Test if we can achieve 2/3 with some simple circuits
    // Try: two 1Ω resistors in parallel, then in series with one more
    // R = 1/(1/1 + 1/1) + 1 = 1/2 + 1 = 3/2 (not 2/3)
    println("Need to find circuit topologies that give exactly $targetResistance")
}

/*
Gradle dependency:
implementation 'org.apache.commons:commons-math3:3.6.1'

Maven dependency:
<dependency>
    <groupId>org.apache.commons</groupId>
    <artifactId>commons-math3</artifactId>
    <version>3.6.1</version>
</dependency>
*/