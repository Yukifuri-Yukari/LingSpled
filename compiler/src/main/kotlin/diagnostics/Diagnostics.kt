package yukifuri.lang.lingspled.compiler.diagnostics

import yukifuri.libs.core.colorama.Fore
import java.io.PrintStream

class Diagnostics {
    private val sources  = mutableMapOf<String, List<String>>()
    private val list     = mutableListOf<Diagnostic>()
    private var currentFile = ""

    /** 开始处理新文件时调用：注册源码并切换当前文件上下文 */
    fun setCurrentFile(fileName: String, content: List<String>) {
        currentFile = fileName
        sources[fileName] = content
    }

    fun add(message: String, level: DiagnosticLevel, extraInfo: String, row: Int, col: Int) {
        list.add(Diagnostic(message, level, extraInfo, currentFile, row, col))
    }

    val hasErrors: Boolean get() = list.any { it.level == DiagnosticLevel.Error }

    fun print(s: PrintStream) {
        for (d in list) {
            val color = when (d.level) {
                DiagnosticLevel.Info    -> Fore.CYAN
                DiagnosticLevel.Warning -> Fore.YELLOW
                DiagnosticLevel.Error   -> Fore.RED
            }
            val levelName  = d.level.name
            val extra      = if (d.extraInfo.isNotEmpty()) " ${d.extraInfo}" else ""
            val lineNo     = d.row + 1
            val width      = " ".repeat(lineNo.toString().length)
            val spaces     = " ".repeat(maxOf(0, d.col - 1))
            val sourceLine = sources[d.fileName]?.getOrNull(d.row) ?: ""
            val location   = if (d.fileName.isNotEmpty()) "${d.fileName}:$lineNo" else "$lineNo"

            s.println("""
                $color[$levelName$extra] ($location): ${d.message} ==========
                $lineNo |${Fore.RESET}$sourceLine$color
                $width |$spaces^->
                ====================${Fore.RESET}
            """.trimIndent())
        }
    }

    data class Diagnostic(
        val message:   String,
        val level:     DiagnosticLevel,
        val extraInfo: String,
        val fileName:  String,
        val row:       Int,
        val col:       Int,
    )
}