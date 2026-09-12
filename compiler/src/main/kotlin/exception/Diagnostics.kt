package yukifuri.lang.lingspled.compiler.exception

import yukifuri.lang.lingspled.compiler.bridge.File
import yukifuri.lang.lingspled.compiler.bridge.Position

class Diagnostics {

    val diags = mutableMapOf<File, MutableList<Diagnostic>>()
    private var current = mutableListOf<Diagnostic>()

    /**
     * Set the current file to which diagnostics will be added.
     */
    fun setCurrentFile(file: File) {
        current = diags.computeIfAbsent(file) { mutableListOf() }
    }

    fun add(
        detail: String,
        position: Pair<Position, Position> = (0 to 0) to (0 to 0),
        info: String = "",
        level: Level = Level.Error,
        sidenote: String = ""
    ) {
        current.add(Diagnostic(detail, position, info, level, sidenote))
    }

    fun add(diag: Diagnostic) {
        current.add(diag)
    }

    /**
     * A Diagnostic contains these fields:
     * - Level: the severity of the diagnostic
     * - Info: a short description of the problem (e.g. E1024)
     * - File & Position: where the diagnostic describes
     * - Detail: a more detailed description of the problem
     * - Source: the context around the problem
     * - Sidenote: a note to help the user fix the problem
     *
     * When printing:
     * ```
     * <level> [<info>] (<file>:<position>): <detail>
     * <source>
     * /* there are "^"s to select a sequence which is wrong */
     * <sidenote>
     * ```
     */
    data class Diagnostic(
        val detail: String,
        val position: Pair<Position, Position> = (0 to 0) to (0 to 0),
        val info: String = "",
        val level: Level = Level.Error,
        val sidenote: String = "",
    )

    enum class Level {
        Error, Warning, Note
    }
}