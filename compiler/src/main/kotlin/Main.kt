package yukifuri.lang.lingspled.compiler

import yukifuri.lang.lingspled.compiler.codegen.bytecode.Bytecodes
import yukifuri.lang.lingspled.compiler.module.ModuleManager
import yukifuri.libs.annotation.NoConstSuggestion
import yukifuri.libs.core.colorama.Fore
import yukifuri.libs.core.logger.LoggerFactory
import java.io.File

val logger = LoggerFactory.getLogger("LingSpled-Compiler")
@NoConstSuggestion val stdlib = "projects/std"

fun printStage(text: String, indent: Int = 10) {
    val a = "=".repeat(indent)
    println("${Fore.LIGHT_CYAN_EX}$a $text $a${Fore.RESET}")
}

lateinit var moduleManager: ModuleManager

fun main(args: Array<String>) {
    Bytecodes.generate()

    val projectName = listOf("std", "LanguageTests")[1]

    moduleManager = ModuleManager(
        name = projectName,
        projectPath = File("projects", projectName),
        dependencies = setOf(File(stdlib))
    )

    moduleManager.init()
    moduleManager.compile()
}
