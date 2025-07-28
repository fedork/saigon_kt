import net.karpelevitch.genMatrices7
import net.karpelevitch.partitions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class Main7Test {
    @Test
    fun partitionsTest() {
        val expected = listOf(
            listOf(1, 2, 3, 4),
            listOf(1, 2, 7),
            listOf(1, 3, 6),
            listOf(1, 4, 5),
            listOf(2, 3, 5),
            listOf(2, 8),
            listOf(3, 7),
            listOf(4, 6),
            listOf(10)
        )

        val result = partitions(10, 3)
        assertEquals(expected.toSet(), result.toSet())
    }

    @Test
    fun genMatricesTest() {
        val matrices7 = genMatrices7(5)
        var count = 0
        matrices7.forEach {
            println(it)
            count++
        }
        assertEquals(49, count)
    }
}