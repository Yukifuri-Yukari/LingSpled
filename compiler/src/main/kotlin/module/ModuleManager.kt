package yukifuri.lang.lingspled.compiler.module

import yukifuri.lang.lingspled.compiler.exception.Diagnostics
import yukifuri.lang.lingspled.compiler.ir.fst.*
import yukifuri.lang.lingspled.compiler.lexer.Lexer
import yukifuri.lang.lingspled.compiler.symbol.ClassSymbol
import yukifuri.lang.lingspled.compiler.symbol.EnumEntrySymbol
import yukifuri.lang.lingspled.compiler.symbol.PackageSymbol
import yukifuri.lang.lingspled.compiler.symbol.ResolutionPass
import yukifuri.lang.lingspled.compiler.symbol.SymbolCollector
import yukifuri.lang.lingspled.compiler.symbol.SymbolProvider
import yukifuri.lang.lingspled.compiler.type.TypeInferencePass
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
    }

    /** 本项目源文件。 */
    val files: MutableSet<File> = mutableSetOf()

    /** 依赖项目（如 stdlib）的源文件——一并 collect/resolve/infer 进同一 SymbolProvider，但不打印 dump。 */
    val dependencyFiles: MutableSet<File> = mutableSetOf()

    val diag = Diagnostics()

    fun init() {
        scanPackage()
        resolveDependencies()
    }

    private fun scanPackage() {
        files.addAll(scan(File(projectPath, "main")))
        println(files)
    }

    /** 把每个依赖项目 main/ 下的 .ling 扫进 [dependencyFiles]；跳过自依赖、去掉与本项目重叠的。 */
    private fun resolveDependencies() {
        for (dep in dependencies) {
            if (dep.absoluteFile == projectPath.absoluteFile) continue // 跳过自依赖
            val entry = File(dep, "main")
            if (entry.isDirectory) dependencyFiles.addAll(scan(entry))
        }
        dependencyFiles.removeAll(files)
    }

    /** 递归扫 [entry] 目录树下所有 `.ling` 文件。 */
    private fun scan(entry: File): Set<File> {
        val out = mutableSetOf<File>()
        val stack = ArrayDeque<File>()
        stack.add(entry)
        while (stack.isNotEmpty()) {
            val dir = stack.removeLast()
            val lings = dir.listFiles { f -> f.isFile && f.extension == "ling" }
                ?: throw IllegalStateException("Not a directory: ${dir.absolutePath}")
            out.addAll(lings)
            dir.listFiles { f -> f.isDirectory }?.forEach { stack.add(it) }
        }
        return out
    }

    fun compile() {
        val lexer = Lexer(diag)
        val parser = Parser(diag)
        val fst = FstGenerator()
        val provider = SymbolProvider()
        val collector = SymbolCollector(provider, diag)
        val resolver = ResolutionPass(provider, diag)
        val infer = TypeInferencePass(provider, diag)

        fun frontend(file: File): LFFile? = try {
            diag.currentFile(file)
            val text = read(file)
            val tokens = lexer.reset(CharStream(text.joinToString("\n"))).lex().ts
            val ast = parser.parse(tokens).ast
            LFFile(file, fst.generate(ast))
        } catch (e: Throwable) {
            println("Error occurred when parsing $file\nStacktrace:")
            e.printStackTrace(System.out)
            null
        }

        // 依赖项目（stdlib 等）先过前端，不打印；本项目随后过前端并打印 FST。
        val depFiles = dependencyFiles.mapNotNull { frontend(it) }
        val projFiles = files.mapNotNull { frontend(it)?.also { m -> println(m) } }
        val files = depFiles + projFiles

        for (file in files) { diag.currentFile(file.file); collector.collect(file.module) }
        for (file in files) { diag.currentFile(file.file); resolver.linkSupertypes(file.module) }
        for (file in files) { diag.currentFile(file.file); resolver.resolve(file.module) }
        for (file in files) { diag.currentFile(file.file); infer.infer(file.module) }

        // 前端 + 语义遍（含 TypeInference 的 lambda 参数无法推断等）的诊断统一在此打印
        if (diag.hasError())
            diag.printAll(System.out)

        println("=== symbols ===")
        fun dumpMembers(cls: ClassSymbol, indent: String) {
            cls.members.forEach { (name, members) ->
                println("$indent.$name -> $members")
                members.filterIsInstance<ClassSymbol>().forEach { dumpMembers(it, "$indent    ") }
            }
        }
        for ((fqn, symbols) in provider.symbols) {
            println("$fqn -> $symbols")
            symbols.filterIsInstance<ClassSymbol>().forEach { dumpMembers(it, "    ") }
        }

        println("=== types ===")
        for (file in projFiles) dumpTypes(file.module, "")
    }

    /** 节点绑定符号的 FQN（类/enum 条目/包给全限定名，其余退回简单名）；供 dump 标注「绑到了谁」。 */
    private fun boundFqn(node: LFExpression): String? {
        val s = when (node) {
            is LFFieldAccessExpr -> node.symbol
            is LFClass -> node.symbol
            is LFFunction -> node.symbol
            is LFVariableDecl -> node.symbol
            else -> null
        }
        return when (s) {
            null -> null
            is ClassSymbol -> s.fqn
            is EnumEntrySymbol -> "${s.enclosing.fqn}.${s.name}"
            is PackageSymbol -> s.fqn
            else -> s.name
        }
    }

    /** 递归打印 FST 节点及其 [LFExpression.inferredType]（TypeInference 回填）+ 绑定符号 FQN，供肉眼核对解析+类型推导。 */
    private fun dumpTypes(node: LFExpression, indent: String) {
        val ty = node.inferredType?.let { " : $it" } ?: ""
        val fqn = boundFqn(node)?.let { " -> $it" } ?: ""
        println("$indent${node::class.simpleName}$ty$fqn")
        val c = "$indent  "
        when (node) {
            is LFModule -> node.statements.forEach { dumpTypes(it, c) }
            is LFExprStatement -> dumpTypes(node.expr, c)
            is LFAssign -> { dumpTypes(node.target, c); dumpTypes(node.value, c) }
            is LFWhile -> { dumpTypes(node.condition, c); dumpTypes(node.body, c) }
            is LFDoWhile -> { dumpTypes(node.body, c); dumpTypes(node.condition, c) }
            is LFFor -> { dumpTypes(node.iterable, c); dumpTypes(node.body, c) }
            is LFFunction.LFReturnStmt -> dumpTypes(node.expr, c)
            is LFClass -> {
                node.attr.forEach { dumpTypes(it, c) }
                node.functions.forEach { dumpTypes(it, c) }
                node.ctors.forEach { dumpTypes(it, c) }
                node.inits.forEach { dumpTypes(it, c) }
                node.deinit?.let { dumpTypes(it, c) }
                node.nested.forEach { dumpTypes(it, c) }
            }
            is LFVariableDecl -> { node.init?.let { dumpTypes(it, c) }; node.delegator?.let { dumpTypes(it, c) } }
            is LFFunction -> node.body?.let { dumpTypes(it, c) }
            is LFIf -> { dumpTypes(node.condition, c); dumpTypes(node.then, c); node.elseBranch?.let { dumpTypes(it, c) } }
            is LFTry -> {
                dumpTypes(node.body, c)
                node.catches.forEach { dumpTypes(it.body, c) }
                node.finallyBlock?.let { dumpTypes(it, c) }
            }
            is LFThrow -> dumpTypes(node.expr, c)
            is LFLambda -> dumpTypes(node.body, c)
            is LFFieldAccessExpr -> node.receiver?.let { dumpTypes(it, c) }
            is LFIndexAccessExpr -> { dumpTypes(node.receiver, c); dumpTypes(node.index, c) }
            is LFUnaryExpr -> dumpTypes(node.expr, c)
            is LFIncDec -> dumpTypes(node.target, c)
            is LFBinaryExpr -> { dumpTypes(node.left, c); dumpTypes(node.right, c) }
            is LFInvokeExpr -> { dumpTypes(node.receiver, c); node.arg.forEach { dumpTypes(it.value, c) } }
            else -> {}
        }
    }
}