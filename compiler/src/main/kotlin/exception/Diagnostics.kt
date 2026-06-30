package yukifuri.lang.lingspled.compiler.exception

import yukifuri.libs.core.colorama.Fore
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
        for ((file, list) in diags) {
            if (list.isEmpty()) continue
            val lines = runCatching { file.readLines() }.getOrDefault(emptyList())
            for (d in list.sortedWith(compareBy({ it.start.first }, { it.start.second }))) {
                val color = when (d.level) {
                    Level.Note -> Fore.LIGHT_BLUE_EX
                    Level.Warning -> Fore.YELLOW
                    Level.Error -> Fore.RED
                    Level.Fatal -> Fore.LIGHT_RED_EX
                }
                val (row, col) = d.start
                // row/col 均 0-based：row 直接索引源行，显示行号 +1，col 个空格定位插入符
                out.println("$color[${d.info}]${Fore.LIGHT_CYAN_EX} [${file.path}:${row + 1}]: $color${d.detail}${Fore.RESET}")

                val src = lines.getOrNull(row)
                if (src != null) {
                    out.println(src)
                    // 保留源行的 tab 缩进，使插入符对齐；同行有跨度则连画 '^'
                    val safe = col.coerceIn(0, src.length)
                    val indent = buildString {
                        for (i in 0 until safe) append(if (src[i] == '\t') '\t' else ' ')
                        repeat(col - safe) { append(' ') }
                    }
                    val width = if (d.end.first == row && d.end.second > col) d.end.second - col else 1
                    out.println("$indent$color${"^".repeat(width)}${Fore.RESET}")
                }

                if (d.sidenote.isNotEmpty()) out.println("Tip: ${d.sidenote}")
            }
        }
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