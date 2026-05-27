package yukifuri.lang.lingspled.compiler.imports.module

import yukifuri.lang.lingspled.compiler.ast.module.ImportDeclaration
import yukifuri.lang.lingspled.compiler.ast.module.Module
import yukifuri.lang.lingspled.compiler.ast.module.PackageDeclaration
import yukifuri.lang.lingspled.compiler.diagnostics.DiagnosticLevel
import yukifuri.lang.lingspled.compiler.diagnostics.Diagnostics
import yukifuri.lang.lingspled.compiler.imports.declaration.DeclarationCollector
import yukifuri.lang.lingspled.compiler.imports.declaration.ExternalDeclarations
import yukifuri.lang.lingspled.compiler.ir.hir.HirGenerator
import yukifuri.lang.lingspled.compiler.codegen.BytecodeFile
import yukifuri.lang.lingspled.compiler.ir.checker.SemanticChecker
import yukifuri.lang.lingspled.compiler.ir.hir.TypeResolver
import yukifuri.lang.lingspled.compiler.ir.hir.base.LHExpression
import yukifuri.lang.lingspled.compiler.ir.hir.base.LHStatement
import yukifuri.lang.lingspled.compiler.ir.hir.expr.*
import yukifuri.lang.lingspled.compiler.ir.hir.module.LHClass
import yukifuri.lang.lingspled.compiler.ir.hir.module.LHFile
import yukifuri.lang.lingspled.compiler.ir.hir.module.LHFunction
import yukifuri.lang.lingspled.compiler.ir.hir.stmt.*
import yukifuri.lang.lingspled.compiler.ir.mir.bytecode.BytecodeMirGenerator
import yukifuri.lang.lingspled.compiler.ir.sym.SymbolCollector
import yukifuri.lang.lingspled.compiler.ir.sym.SymbolTable
import yukifuri.lang.lingspled.compiler.lexer.Lexer
import yukifuri.lang.lingspled.compiler.parser.Parser
import yukifuri.libs.compilation.stream.CharStream
import java.io.File

class ModuleManager(
    val sourceRoot: File,
    val entry: String,
    packageRoot: String,
    val imports: ExternalDeclarations,
) {

    val root        = Package(packageRoot, sourceRoot, null, mutableListOf())
    val diagnostics = Diagnostics()

    private var current      = root
    private var compileOrder : List<List<FileModule>> = emptyList()  // SCCs in topo order
    private var fileDeps     : Map<FileModule, Set<FileModule>> = emptyMap()

    fun scanPackages() {
        if (!current.path.exists() || !current.path.isDirectory)
            throw IllegalStateException("Package ${current.fullname} does not exist")

        for (file in current.path.listFiles()!!) {
            if (file.isDirectory) {
                current = current.child(file.name)
                scanPackages()
                current = current.parent!!
            }
            if (file.isFile && file.extension == "ling") {
                current.files.add(
                    FileModule(file.name, file, Module(listOf()), "", LHFile(listOf()), listOf(), ExternalDeclarations(setOf()))
                )
            }
        }
    }

    fun validateDependency() {
        val all = root.flat()

        for (file in all) {
            val content = read(file.path.absolutePath)
            diagnostics.setCurrentFile(file.name, content)
            try {
                val ast = Parser(
                    Lexer(CharStream(content.joinToString("\n")), diagnostics).lex().ts,
                    diagnostics
                ).parse().ast

                file.ast = ast
                file.packageName = ast.statements
                    .filterIsInstance<PackageDeclaration>()
                    .firstOrNull()?.name?.joinToString(".") ?: ""
                file.imports = ast.statements.filterIsInstance<ImportDeclaration>()
            } catch (_: Exception) { }
        }

        val byPath: Map<String, FileModule> = all.associateBy { it.path.canonicalPath }
        val deps: Map<FileModule, Set<FileModule>> = all.associateWith { file ->
            file.imports.flatMap { resolveDeps(it, byPath) }.toSet()
        }

        compileOrder = tarjanSCC(all, deps)
        fileDeps     = deps
    }

    /** Tarjan SCC, 返回按拓扑序排列的 SCC 列表 (依赖在前)  */
    private fun tarjanSCC(
        all:  List<FileModule>,
        deps: Map<FileModule, Set<FileModule>>,
    ): List<List<FileModule>> {
        val index   = mutableMapOf<FileModule, Int>()
        val lowlink = mutableMapOf<FileModule, Int>()
        val onStack = mutableSetOf<FileModule>()
        val stack   = ArrayDeque<FileModule>()
        val sccs    = mutableListOf<List<FileModule>>()
        var idx     = 0

        fun visit(v: FileModule) {
            index[v]   = idx
            lowlink[v] = idx++
            stack.addFirst(v)
            onStack.add(v)

            for (w in deps[v] ?: emptySet()) {
                if (w !in index) {
                    visit(w)
                    lowlink[v] = minOf(lowlink[v]!!, lowlink[w]!!)
                } else if (w in onStack) {
                    lowlink[v] = minOf(lowlink[v]!!, index[w]!!)
                }
            }

            if (lowlink[v] == index[v]) {
                val scc = mutableListOf<FileModule>()
                do { val w = stack.removeFirst(); onStack.remove(w); scc.add(w) } while (scc.last() != v)
                sccs.add(scc)
            }
        }

        all.forEach { if (it !in index) visit(it) }
        return sccs  // Tarjan outputs SCCs with dependencies first when edges mean "depends on"
    }

    private fun resolveDeps(decl: ImportDeclaration, byPath: Map<String, FileModule>): List<FileModule> {
        // Imports are symbol-based: the package is all components except the last (symbol name).
        // Wildcard imports use all components as the package path.
        // In both cases, every .ling file in the package directory is a dependency.
        val pkgParts = if (decl.isWildcard) decl.parts else decl.parts.dropLast(1)
        val pkgDir   = File(sourceRoot, pkgParts.joinToString("/"))
        return pkgDir.listFiles { f -> f.extension == "ling" }
            ?.mapNotNull { byPath[it.canonicalPath] } ?: emptyList()
    }

    fun compile() {
        println("[compile] SCCs: ${compileOrder.size}, total files: ${compileOrder.sumOf { it.size }}")
        for (scc in compileOrder) {
            println("[compile] SCC: ${scc.map { it.name }}")
            val sccSet = scc.toSet()
            val externalDeps = scc
                .flatMap { fileDeps[it] ?: emptySet() }
                .filter { it !in sccSet }
                .toSet()

            val symTable = SymbolTable()
            externalDeps.forEach { dep ->
                dep.hir.statements.forEach { it.accept(SymbolCollector(symTable, diagnostics)) }
            }

            // Pass 1: SCC 内所有文件先生成 HIR, 全量注册符号
            val hirMap = mutableMapOf<File, LHFile>()   // key = file.path (stable, not FileModule)
            for (file in scc) {
                diagnostics.setCurrentFile(file.name, read(file.path.absolutePath))
                try {
                    val hir = HirGenerator().generate(file.ast)
                    file.hir = hir
                    hirMap[file.path] = hir
                    println("[Pass1] ${file.name}: HIR ok, ${hir.statements.size} top-level statements")
                    hir.statements.forEach { it.accept(SymbolCollector(symTable, diagnostics)) }
                } catch (e: Exception) {
                    println("[Pass1] ${file.name}: FAILED — ${e::class.simpleName}: ${e.message}")
                    e.printStackTrace()
                    diagnostics.add("Error (HIR) in ${file.name}", DiagnosticLevel.Error, e.message ?: "", 0, 0)
                }
            }

            // Pass 2: 在完整符号表上做类型推断 + 收集 export
            for (file in scc) {
                val hir = hirMap[file.path] ?: run { println("[Pass2] ${file.name}: skipped (no HIR)"); continue }
                diagnostics.setCurrentFile(file.name, read(file.path.absolutePath))
                try {
                    TypeResolver(symTable).inferFile(hir)
                    file.exports = DeclarationCollector(file.packageName).collect(hir)
                    println("[Pass2] ${file.name}: type inference ok")
                } catch (e: Exception) {
                    println("[Pass2] ${file.name}: FAILED — ${e::class.simpleName}: ${e.message}")
                    e.printStackTrace()
                }
            }

            // Pass 3: 语义检查 (未定义符号 / val 重赋值 / 字段方法用法)
            for (file in scc) {
                val hir = hirMap[file.path] ?: run { println("[Pass3] ${file.name}: skipped (no HIR)"); continue }
                println("\n[HIR] ── ${file.name} (typed) ─────────────────────────")
                printTypedHir(hir)
                diagnostics.setCurrentFile(file.name, read(file.path.absolutePath))
                try {
                    SemanticChecker(symTable, diagnostics).check(hir)
                } catch (e: Exception) {
                    println("[Pass3] ${file.name}: FAILED — ${e::class.simpleName}: ${e.message}")
                    e.printStackTrace()
                }
            }

            // Pass 4: 字节码生成 + 汇编 → .lsbc
            for (file in scc) {
                val hir = hirMap[file.path] ?: continue
                diagnostics.setCurrentFile(file.name, read(file.path.absolutePath))
                try {
                    val fileCode = BytecodeMirGenerator(symTable).generate(hir)
                    val bytes    = BytecodeFile().assemble(fileCode)
                    val outFile  = File(file.path.parent, file.name.removeSuffix(".ling") + ".lsbc")
                    outFile.writeBytes(bytes)
                    println("[codegen] wrote ${outFile.path} (${bytes.size} bytes)")
                } catch (e: Exception) {
                    diagnostics.add("Error (Codegen) in ${file.name}", DiagnosticLevel.Error, e.message ?: "", 0, 0)
                }
            }
        }
        diagnostics.print(System.out)
    }

    // ── Typed-HIR debug printer ───────────────────────────────────────────────

    private fun printTypedHir(hir: LHFile) {
        for (stmt in hir.statements) printStmt(stmt, "")
    }

    private fun printStmt(stmt: LHStatement, ind: String) {
        when (stmt) {
            is LHFunction -> {
                val params = stmt.params.joinToString { "${it.name}: ${it.type.typename()}" }
                println("${ind}fun ${stmt.name}($params): ${stmt.returnType.typename()}")
                for (s in stmt.body) printStmt(s, "$ind  ")
            }
            is LHClass -> {
                val kind = if (stmt.isInterface) "interface" else "class"
                println("${ind}$kind ${stmt.name} : ${stmt.superClass.typename()}")
                for (f in stmt.fields)
                    println("$ind  ${if (f.isConstant) "val" else "var"} ${f.name}: ${f.type.typename()}${if (f.init != null) " = ${printExpr(f.init)}" else ""}")
                for (m in stmt.methods) printStmt(m, "$ind  ")
            }
            is LHVarDecl -> {
                val kw   = if (stmt.isConstant) "val" else "var"
                val type = stmt.inferredType?.typename() ?: stmt.declaredType.typename()
                val init = if (stmt.init != null) " = ${printExpr(stmt.init)}" else ""
                println("$ind$kw ${stmt.name}: $type$init")
            }
            is LHAssign -> {
                val tgt = when (val t = stmt.target) {
                    is LHLocalTarget -> t.name
                    is LHFieldTarget -> "${printExpr(t.receiver)}.${t.field}"
                }
                println("$ind$tgt = ${printExpr(stmt.value)}")
            }
            is LHIf -> {
                println("${ind}if (${printExpr(stmt.cond)})")
                for (s in stmt.then) printStmt(s, "$ind  ")
                if (stmt.els != null) {
                    println("${ind}else")
                    for (s in stmt.els) printStmt(s, "$ind  ")
                }
            }
            is LHWhile -> {
                println("${ind}while (${printExpr(stmt.cond)})")
                for (s in stmt.body) printStmt(s, "$ind  ")
            }
            is LHFor -> {
                val initStr = when (val i = stmt.init) {
                    null         -> ""
                    is LHVarDecl -> "${if (i.isConstant) "val" else "var"} ${i.name}: ${i.inferredType?.typename() ?: i.declaredType.typename()}${if (i.init != null) " = ${printExpr(i.init)}" else ""}"
                    else         -> printExpr(i)
                }
                val updateStr = if (stmt.update != null) printExpr(stmt.update) else ""
                println("${ind}for ($initStr; ${printExpr(stmt.cond)}; $updateStr)")
                for (s in stmt.body) printStmt(s, "$ind  ")
            }
            is LHReturn      -> println("${ind}return${if (stmt.value != null) " ${printExpr(stmt.value)}" else ""}")
            is LHExprStmt    -> println("$ind${printExpr(stmt.expr)}")
            is LHDefer       -> println("${ind}defer ${printExpr(stmt.expr)}")
            is LHLoopControl -> println("$ind${if (stmt.isBreak) "break" else "continue"}")
            is LHWhen -> {
                println("${ind}when${if (stmt.subject != null) " (${printExpr(stmt.subject)})" else ""}")
                for (b in stmt.branches) {
                    val head = when (b) {
                        is LHTypeBranch -> "is ${b.typeName}(${b.destructured.joinToString()})"
                        is LHExprBranch -> printExpr(b.cond)
                    }
                    val g     = b.guard
                    val guard = if (g != null) " if ${printExpr(g)}" else ""
                    println("$ind  $head$guard ->")
                    for (s in b.body) printStmt(s, "$ind    ")
                }
                if (stmt.elseBranch != null) {
                    println("$ind  else ->")
                    for (s in stmt.elseBranch) printStmt(s, "$ind    ")
                }
            }
            else -> println("$ind<${stmt::class.simpleName}>")
        }
    }

    private fun printExpr(expr: LHExpression): String {
        val type = expr.inferredType?.typename() ?: "?"
        return when (expr) {
            is LHLiteral  -> "${expr.value} : $type"
            is LHLocalGet -> "${expr.name} : $type"
            is LHThis     -> "this : $type"
            is LHFieldGet -> "${printExpr(expr.receiver)}.${expr.field} : $type"
            is LHCall     -> {
                val recv = if (expr.receiver != null) "${printExpr(expr.receiver)}." else ""
                val args = expr.args.joinToString { printExpr(it) }
                "$recv${expr.name}($args) : $type"
            }
            is LHLambda   -> "{ ${expr.params.joinToString { "${it.name}: ${it.type.typename()}" }} -> ... } : $type"
            is LHExprStmt -> printExpr(expr.expr)
            is LHAssign   -> {
                val tgt = when (val t = expr.target) {
                    is LHLocalTarget -> t.name
                    is LHFieldTarget -> "${printExpr(t.receiver)}.${t.field}"
                }
                "$tgt = ${printExpr(expr.value)}"
            }
            else          -> "<${expr::class.simpleName}> : $type"
        }
    }

    companion object {
        fun read(path: String): List<String> =
            File(path).bufferedReader().use { it.readLines() }

        fun write(path: String, content: String) =
            File(path).bufferedWriter().use { it.write(content) }
    }

    data class FileModule(
        val name: String,
        val path: File,
        var ast: Module,
        var packageName: String,
        var hir: LHFile,
        var imports: List<ImportDeclaration>,
        var exports: ExternalDeclarations,
    )

    data class Package(
        val fullname: String,
        val path: File,
        val parent: Package? = null,
        val children: MutableList<Package>,
        val files: MutableList<FileModule> = mutableListOf(),
    ) {
        fun child(packageName: String): Package {
            val childFullname = if (fullname != "") "$fullname.$packageName" else packageName
            val child = Package(childFullname, File(path, packageName), this, mutableListOf())
            children.add(child)
            return child
        }

        fun flat(): List<FileModule> = files + children.flatMap { it.flat() }

        override fun toString() =
            "Package(name='$fullname', path='${path.name}', parent=${parent?.fullname}, children=$children, files=$files)"
    }
}