package yukifuri.lang.lingspled.compiler.module

import yukifuri.lang.lingspled.compiler.bridge.File
import yukifuri.lang.lingspled.compiler.exception.Diagnostics
import yukifuri.lang.lingspled.compiler.front.astprocess.AstGenerator
import yukifuri.lang.lingspled.compiler.front.lexeme.Lexer
import yukifuri.lang.lingspled.compiler.front.lexeme.token.TokenType
import yukifuri.lang.lingspled.compiler.front.parser.Parser
import yukifuri.libs.compilation.stream.CharStream

class ModuleManager(
    val name: String,
    val projectPath: File,
    val dependencies: Set<File>
) {

    companion object {
        fun read(file: File) = file.readText()
    }

    val diagnostics = Diagnostics()

    private fun files(directory: File): List<File> {
        return buildList {
            directory.listFiles()!!.forEach {
                if (it.isDirectory) {
                    addAll(files(it))
                } else {
                    add(it)
                }
            }
        }
    }

    fun compile() {
        val files = files(projectPath)

        val lexer = Lexer(diagnostics)
        val parser = Parser(diagnostics)
        val astGen = AstGenerator()

        for (file in files) {
            diagnostics.setCurrentFile(file)

            val ts = lexer.lex(CharStream(read(file)))
            println(ts.list().filter { it.type != TokenType.Whitespace })
            val cst = parser.parse(ts)
            println(cst)
            val pt = astGen.process(cst)
        }
    }
}