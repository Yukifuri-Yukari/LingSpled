package yukifuri.lang.lingspled.compiler.bridge

data class FQN(
    val parts: List<String>
) {
    fun join(separator: String) = parts.joinToString(separator)
}
