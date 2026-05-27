package yukifuri.lang.lingspled.compiler

import yukifuri.lang.lingspled.compiler.codegen.bytecode.Bytecodes
import yukifuri.lang.lingspled.compiler.imports.declaration.ExternalDeclarations
import yukifuri.lang.lingspled.compiler.imports.module.ModuleManager
import yukifuri.lang.lingspled.compiler.ir.hir.base.LHStatement
import yukifuri.lang.lingspled.compiler.ir.hir.module.LHClass
import yukifuri.lang.lingspled.compiler.ir.hir.module.LHFile
import yukifuri.lang.lingspled.compiler.ir.hir.module.LHFunction
import yukifuri.lang.lingspled.compiler.ir.hir.stmt.*
import yukifuri.lang.lingspled.compiler.ir.sym.SymbolTable
import yukifuri.libs.core.colorama.Fore
import yukifuri.libs.core.logger.LoggerFactory
import java.io.File

val logger = LoggerFactory.getLogger("LingSpled-Compiler")

fun printStage(text: String, indent: Int = 10) {
    val a = "=".repeat(indent)
    println("${Fore.LIGHT_CYAN_EX}$a $text $a${Fore.RESET}")
}

fun printTypes(hirModule: LHFile, symTable: SymbolTable, postInference: Boolean = false) {
    fun printVarDecls(stmts: List<LHStatement>, indent: String, postInference: Boolean) {
        for (stmt in stmts) when (stmt) {
            is LHVarDecl  -> {
                val kw   = if (stmt.isConstant) "val" else "var"
                val type = if (postInference) stmt.inferredType?.typename() ?: "?"
                else stmt.declaredType.typename()
                println("$indent$kw ${stmt.name}: $type")
            }
            is LHIf       -> {
                printVarDecls(stmt.then, indent, postInference)
                stmt.els?.let { printVarDecls(it, indent, postInference) }
            }
            is LHWhile    -> printVarDecls(stmt.body, indent, postInference)
            is LHFor      -> {
                stmt.init?.let { printVarDecls(listOf(it), indent, postInference) }
                printVarDecls(stmt.body, indent, postInference)
            }
            is LHFunction -> {
                println("$indent  fun ${stmt.name}() → ${stmt.returnType.typename()}")
                printVarDecls(stmt.body, "$indent  ", postInference)
            }
            is LHWhen     -> {
                stmt.branches.forEach { printVarDecls(it.body, indent, postInference) }
                stmt.elseBranch?.let { printVarDecls(it, indent, postInference) }
            }
            else -> {}
        }
    }

    symTable.root.classes.values.forEach { cls ->
        val kind = if (cls.isInterface) "interface" else "class"
        println("  $kind ${cls.name} : ${cls.superClass.typename()}")
        println("    fields:  ${cls.fields.map { "${it.key}: ${it.value.type.typename()}" }}")
        println("    methods: ${cls.methods.map { "${it.key}: ${it.value.returnType.typename()}" }}")
    }

    val label = if (postInference) "Local variable inferences (resolved):"
    else "Local variable declarations (pre-inference):"
    println("  $label")
    hirModule.statements.forEach { module ->
        when (module) {
            is LHFunction -> {
                println("    fun ${module.name}() → ${module.returnType.typename()}")
                printVarDecls(module.body, "      ", postInference)
            }
            is LHClass -> {
                println("    class ${module.name}")
                module.methods.filterIsInstance<LHFunction>().forEach { m ->
                    println("      fun ${m.name}() → ${m.returnType.typename()}")
                    printVarDecls(m.body, "        ", postInference)
                }
            }
            else -> {}
        }
    }
}

lateinit var moduleManager: ModuleManager

fun main(args: Array<String>) {
    Bytecodes.generate()

    val sourceRoot = File("tests")
    val entry = "sqrt.second.Main.main"

    moduleManager = ModuleManager(
        sourceRoot = sourceRoot,
        entry = entry,
        packageRoot = "",
        imports = ExternalDeclarations(setOf()),
    )

    try {
        moduleManager.scanPackages()
        moduleManager.validateDependency()

        moduleManager.compile()
    } catch (e: Exception) {
        e.printStackTrace()
        moduleManager.diagnostics.print(System.out)
    }
}
