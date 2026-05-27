package yukifuri.lang.lingspled.compiler.imports

import yukifuri.lang.lingspled.compiler.ast.module.ImportDeclaration
import yukifuri.lang.lingspled.compiler.diagnostics.Diagnostics
import yukifuri.lang.lingspled.compiler.ir.hir.HirGenerator
import yukifuri.lang.lingspled.compiler.ir.hir.TypeResolver
import yukifuri.lang.lingspled.compiler.ir.sym.SymbolCollector
import yukifuri.lang.lingspled.compiler.ir.sym.SymbolTable
import yukifuri.lang.lingspled.compiler.lexer.Lexer
import yukifuri.lang.lingspled.compiler.parser.Parser
import yukifuri.libs.compilation.stream.CharStream
import java.io.File

/**
 * Resolves import declarations by compiling the referenced source files and
 * merging their symbols into the shared [symTable].
 *
 * [sourceRoot] is the directory used as the root of the package hierarchy.
 * e.g. `import lspled.io.Foo` → `{sourceRoot}/lspled/io/Foo.ling`
 */
class ImportResolver(
    private val sourceRoot: String,
    private val symTable: SymbolTable,
    private val diagnostics: Diagnostics,
    private val compiling: MutableSet<String> = mutableSetOf(),
) {
    fun resolve(imports: List<ImportDeclaration>) {
        for (decl in imports) resolveOne(decl)
    }

    private fun resolveOne(decl: ImportDeclaration) {
        for (path in resolvePaths(decl)) {
            if (path in compiling) continue   // 循环依赖守卫
            val file = File(path)
            if (!file.exists()) {
                diagnostics.add(
                    "Cannot resolve import '${decl.qualifiedName}${if (decl.isWildcard) ".*" else ""}'",
                    yukifuri.lang.lingspled.compiler.diagnostics.DiagnosticLevel.Error,
                    "file not found: $path", 0, 0
                )
                continue
            }
            compiling.add(path)
            compileAndMerge(path, file.readLines())
            compiling.remove(path)
        }
    }

    private fun resolvePaths(decl: ImportDeclaration): List<String> {
        val base = File(sourceRoot, decl.parts.joinToString("/"))
        return if (decl.isWildcard) {
            base.listFiles { f -> f.extension == "ling" }
                ?.map { it.canonicalPath }
                ?: emptyList()
        } else {
            listOf("$base.ling")
        }
    }

    private fun compileAndMerge(path: String, content: List<String>) {
        val cs = CharStream(content.joinToString("\n"))

        val lexer = Lexer(cs, diagnostics)
        lexer.lex()

        val parser = Parser(lexer.ts, diagnostics)
        parser.parse()

        // 递归解析被导入文件自身的 import
        val nestedImports = parser.ast.statements.filterIsInstance<ImportDeclaration>()
        resolve(nestedImports)

        val hirFile = HirGenerator().generate(parser.ast)

        val collector = SymbolCollector(symTable, diagnostics)
        hirFile.statements.forEach { it.accept(collector) }

        TypeResolver(symTable).inferFile(hirFile)
    }
}
