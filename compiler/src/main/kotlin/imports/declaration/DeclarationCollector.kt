package yukifuri.lang.lingspled.compiler.imports.declaration

import yukifuri.lang.lingspled.compiler.ast.type.LType
import yukifuri.lang.lingspled.compiler.ir.hir.expr.LHLiteral
import yukifuri.lang.lingspled.compiler.ir.hir.module.LHClass
import yukifuri.lang.lingspled.compiler.ir.hir.module.LHFile
import yukifuri.lang.lingspled.compiler.ir.hir.module.LHFunction
import yukifuri.lang.lingspled.compiler.ir.hir.stmt.LHVarDecl

class DeclarationCollector(
    private val packageName: String
) {

    fun collect(file: LHFile): ExternalDeclarations {
        val decls = file.statements.mapNotNull { stmt ->
            when (stmt) {
                is LHFunction -> stmt.toDeclaration()
                is LHClass    -> stmt.toDeclaration()
                is LHVarDecl  -> stmt.toDeclaration()
                else          -> null
            }
        }
        return ExternalDeclarations(decls.toSet())
    }

    private fun fqn(name: String) =
        if (packageName.isEmpty()) name else "$packageName.$name"

    private fun LHFunction.toDeclaration(): FunctionDeclaration {
        val funcType = LType("->", params.map { it.type } + returnType)
        return FunctionDeclaration(
            modifiers   = modifiers,
            shortenName = name,
            type        = funcType,
            typeParams  = typeParams,
            params      = params,
            fqn         = fqn(name),
        )
    }

    private fun LHClass.toDeclaration(): ClassDeclaration {
        val classType = if (typeParams.isEmpty()) LType(name)
                        else LType(name, typeParams.map { LType(it) })
        val classModifiers = modifiers

        val members = buildList {
            fields.forEach { field ->
                add(VariableDeclaration(
                    modifiers   = field.modifiers,
                    shortenName = field.name,
                    type        = field.type,
                    isConstant  = field.isConstant,
                    fqn         = "${fqn(name)}#${field.name}",
                ))
            }
            methods.filterIsInstance<LHFunction>().forEach { m ->
                val funcType = LType("->", m.params.map { it.type } + m.returnType)
                add(FunctionDeclaration(
                    modifiers   = m.modifiers,
                    shortenName = m.name,
                    type        = funcType,
                    typeParams  = m.typeParams,
                    params      = m.params,
                    fqn         = "${fqn(name)}#${m.name}",
                ))
            }
        }

        return ClassDeclaration(
            modifiers   = classModifiers,
            shortenName = name,
            type        = classType,
            typeParams  = typeParams,
            superClass  = superClass,
            isInterface = isInterface,
            members     = members,
            fqn         = fqn(name),
        )
    }

    private fun LHVarDecl.toDeclaration(): VariableDeclaration =
        VariableDeclaration(
            modifiers   = modifiers,
            shortenName = name,
            type        = inferredType ?: declaredType,
            isConstant  = isConstant,
            fqn         = fqn(name),
        )
}