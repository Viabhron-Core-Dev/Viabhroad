import kotlin.math.ln

fun main() {
    val alpha = 0.05
    val freq = 1000
    val word = "receive"
    val lengthNormDist = 1.0 / word.length
    val spatialPenaltyValue = 2.0
    val safeFreq = maxOf(1, freq).toDouble()
    val totalPenalty = (lengthNormDist + spatialPenaltyValue) - (alpha * ln(safeFreq))
    println("Total penalty: $totalPenalty")
}
