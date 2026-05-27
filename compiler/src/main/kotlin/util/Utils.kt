package yukifuri.lang.lingspled.compiler.util

object Utils {
    fun toInt(s: String): Int {
        return when {
            s.startsWith("0b") -> s.substring(2).toInt(2)
            s.startsWith("0x") -> s.substring(2).toInt(16)
            else -> s.toInt()
        }
    }
}