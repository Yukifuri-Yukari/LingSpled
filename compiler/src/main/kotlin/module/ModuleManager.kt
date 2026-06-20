package yukifuri.lang.lingspled.compiler.module

import yukifuri.lang.lingspled.compiler.exception.Diagnostics
import yukifuri.lang.lingspled.compiler.ir.fst.FstGenerator
import yukifuri.lang.lingspled.compiler.lexer.Lexer
import yukifuri.lang.lingspled.compiler.symbol.ClassSymbol
import yukifuri.lang.lingspled.compiler.symbol.SymbolCollector
import yukifuri.lang.lingspled.compiler.symbol.SymbolProvider
import yukifuri.lang.lingspled.compiler.parser.Parser
import yukifuri.libs.compilation.stream.CharStream
import java.io.File

class ModuleManager(
    val name: String,
    val projectPath: File,
    val dependencies: Set<File>
) {

    companion object {
        fun read(path: File) = path.readLines()
        fun write(path: File, content: List<String>) = path.writeText(content.joinToString("\n"))
    }

    val files: MutableSet<File> = mutableSetOf()

    val diag = Diagnostics()

    fun init() {
        scanPackage()
        resolveDependencies()
    }

    private fun scanPackage() {
        val entry = File(projectPath, "main")
        val stack = ArrayDeque<File>()
        stack.add(entry)

        while (stack.isNotEmpty()) {
            val file = stack.removeLast()
            val files = file
                .listFiles { it.isFile && it.extension == "ling" }
                ?.toSet() ?: throw IllegalStateException("Not a directory: ${file.absolutePath}")

            if (file.isDirectory)
                for (dir in file
                    .listFiles { it.isDirectory }!!
                ) stack.add(dir)

            this.files.addAll(files)
        }

        println(files)
    }

    private fun resolveDependencies() {
    }

    fun compile() {
        val lexer = Lexer(diag)
        val parser = Parser(diag)
        val fst = FstGenerator()
        val provider = SymbolProvider()
        val collector = SymbolCollector(provider, diag)

        for (file in files) {
            diag.currentFile(file)
            val text = read(file)

            val tokens = lexer.reset(CharStream(text.joinToString("\n"))).lex().ts
            val ast = parser.parse(tokens).ast

            val fst = fst.generate(ast)
            println(fst)

            collector.collect(fst)
        }

        println("=== symbols ===")
        fun dumpMembers(cls: ClassSymbol, indent: String) {
            cls.members.forEach { (name, members) ->
                println("$indent.$name -> $members")
                members.filterIsInstance<ClassSymbol>().forEach { dumpMembers(it, "$indent    ") }
            }
        }
        for ((fqn, syms) in provider.symbols) {
            println("$fqn -> $syms")
            syms.filterIsInstance<ClassSymbol>().forEach { dumpMembers(it, "    ") }
        }
    }
}