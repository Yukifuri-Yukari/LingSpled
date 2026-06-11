package yukifuri.lang.lingspled.compiler.parser

import yukifuri.lang.lingspled.compiler.ast.module.LAModule
import yukifuri.lang.lingspled.compiler.exception.Diagnostics
import yukifuri.lang.lingspled.compiler.lexer.token.TokenStream
import yukifuri.lang.lingspled.compiler.parser.subparser.*

class Parser(
    val diag: Diagnostics
) {

    var ast = LAModule(listOf(), 0 to 0)
    var ts = TokenStream(listOf())

    val toplevel = TopLevelParser(this)
    val module = ModuleParser(this)
    val expr = ExpressionParser(this)
    val cls = ClassParser(this)

    fun parse(ts: TokenStream): Parser {
        this.ts = ts
        val builder = LAModule.Builder(0 to 0)

        while (ts.hasNext()) {
            builder.add(toplevel.parse())
            toplevel.skipWs()
        }

        ast = builder.build()

        return this
    }
}