package yukifuri.lang.lingspled.compiler.exception

import java.io.File
import java.io.PrintStream

class Diagnostics {

    private val diags = mutableMapOf<File, MutableList<Diagnostic>>()
    private var curr: MutableList<Diagnostic> = mutableListOf()

    fun currentFile(file: File) {
        diags.computeIfAbsent(file, { mutableListOf() })
        curr = diags[file]!!
    }

    fun add(
        info: String,
        detail: String,
        start: Pair<Int, Int>,
        end: Pair<Int, Int>,
        sidenote: String = "",
        level: Level = Level.Error,
    ) {
        curr.add(Diagnostic(info, detail, start, end, sidenote, level))
    }

    fun hasError() = diags.any { it.value.any { it1 -> it1.level == Level.Error || it1.level == Level.Fatal } }

    fun printAll(out: PrintStream) {
        TODO()
    }

    data class Diagnostic(
        val info: String,
        val detail: String,
        val start: Pair<Int, Int>,
        val end: Pair<Int, Int>,
        val sidenote: String,
        val level: Level,
    )

    enum class Level {
        Note,
        Warning,
        Error,
        Fatal
    }
}