package yukifuri.lang.lingspled.compiler.parser.subparser

import yukifuri.lang.lingspled.compiler.ast.base.Argument
import yukifuri.lang.lingspled.compiler.ast.base.Expression
import yukifuri.lang.lingspled.compiler.ast.base.Statement
import yukifuri.lang.lingspled.compiler.ast.clazz.LClass
import yukifuri.lang.lingspled.compiler.ast.clazz.LClassAttribute
import yukifuri.lang.lingspled.compiler.ast.clazz.LClassConstructor
import yukifuri.lang.lingspled.compiler.ast.function.LFunction
import yukifuri.lang.lingspled.compiler.ast.module.Annotation
import yukifuri.lang.lingspled.compiler.ast.module.Module
import yukifuri.lang.lingspled.compiler.ast.type.LType
import yukifuri.lang.lingspled.compiler.lexer.token.TokenType
import yukifuri.lang.lingspled.compiler.parser.Parser

class ClassParser(parent: Parser) : SubParser(parent) {
    fun parse(modifier: List<String>, annotations: List<Annotation> = emptyList()): LClass {
        if (peek().type != TokenType.Keyword)
            diagnostic("Expected keyword", throws = true)
        return when (next().text) {
            "class"     -> parseClass(modifier, annotations)
            "interface" -> parseInterface(modifier, annotations)
            else -> TODO()
        }
    }

    /**
     * Parse an interface declaration.
     * Rules that differ from a class:
     *  - No primary or secondary constructors.
     *  - Fields must carry the `private` modifier; any other visibility is a diagnostic error.
     *  - Methods without a body `{ }` are automatically marked `abstract`.
     *  - Multiple super-interfaces are allowed (all entries in `:` list are interfaces).
     *
     * 解析接口声明。
     * 与类的区别：
     *  - 无主构造函数和次构造函数。
     *  - 字段必须携带 `private` 修饰符，否则报错。
     *  - 没有 `{ }` 体的方法自动标记为 `abstract`。
     *  - 允许多个父接口（`:` 列表中所有条目均为接口）。
     */
    private fun parseInterface(modifier: List<String>, annotations: List<Annotation>): LClass {
        val name = next(TokenType.Identifier)
        val typeParams = parent.parseTypeParams()

        // All entries after `:` are super-interfaces, not a super-class.
        // `:` 之后的所有条目均为父接口，而非父类。
        val superInterfaces = if (peek().type == TokenType.Colon) {
            next(); skipWs()
            val types = mutableListOf(parent.parseType())
            while (hasNext() && peek().type == TokenType.Comma) {
                next(); skipWs()
                if (!hasNext() || peek().type == TokenType.LBrace) break
                types.add(parent.parseType())
            }
            types
        } else emptyList()

        val fields   = mutableListOf<LClassAttribute>()
        val methods  = mutableListOf<LFunction>()

        if (hasNext() && peek().type == TokenType.LBrace) {
            next() // consume '{'
            while (hasNext() && peek().type != TokenType.RBrace) {
                skipWs()
                if (!hasNext() || peek().type == TokenType.RBrace) break
                if (peek().type == TokenType.NewLine) { next(); continue }

                val memberAnnotations = parent.parseAnnotations()
                val memberModifiers   = parent.parseModifiers(extra = setOf("native", "override"))

                when (peek().text) {
                    "val", "var" -> {
                        // Enforce private-only fields in interfaces.
                        // 接口中的字段只能是 private。
                        if ("private" !in memberModifiers)
                            diagnostic("Interface fields must be private; use abstract class for shared state.")
                        fields.add(parseClassAttributeDecl(memberModifiers))
                    }
                    "fun" -> methods.add(parseInterfaceMethod(memberModifiers, memberAnnotations))
                    else  -> { diagnostic("Unexpected member in interface: '${peek().text}'", throws = true) }
                }
                skipWs()
            }
            next(TokenType.RBrace)
        }

        return LClass(
            name.text, typeParams, superInterfaces, modifier,
            fields, methods, annotations, isInterface = true
        ).also { it.at(name.row, name.col) }
    }

    /**
     * Parse one method inside an interface body.
     * If the method has no `{ }` body it is abstract — `"abstract"` is prepended to its modifiers.
     * If it has a body it is a default method.
     *
     * 解析接口体内的一个方法。
     * 若方法没有 `{ }` 体则为抽象方法，自动将 `"abstract"` 添加到修饰符首位。
     * 若有体则为默认方法。
     */
    private fun parseInterfaceMethod(modifiers: List<String>, annotations: List<Annotation>): LFunction {
        val funTok    = next(TokenType.Keyword, "fun")
        val typeParams = parent.parseTypeParams()
        val name      = next(TokenType.Identifier)
        next(TokenType.LParen)
        val arguments = parent.parseList { parseFunctionArgument() }
        val returnType = if (peek().type == TokenType.Colon) { next(); parent.parseType() }
                         else LType("Unit")
        return if (hasNext() && peek().type == TokenType.LBrace) {
            // Default method — has a body / 默认方法，有体
            next(TokenType.LBrace)
            val body = inModule.parse(Module.Builder()).build()
            next(TokenType.RBrace)
            LFunction(modifiers, name.text, typeParams, arguments, returnType, body, annotations)
                .also { it.at(funTok.row, funTok.col) }
        } else {
            // Abstract method — no body; prepend "abstract" so later passes can identify it.
            // 抽象方法，无体；在修饰符首位加 "abstract" 以便后续阶段识别。
            val abstractMods = if ("abstract" in modifiers) modifiers else listOf("abstract") + modifiers
            LFunction(abstractMods, name.text, typeParams, arguments, returnType, Module(emptyList()), annotations)
                .also { it.at(funTok.row, funTok.col) }
        }
    }

    private fun parseClass(modifier: List<String>, annotations: List<Annotation>): LClass {
        val name = next(TokenType.Identifier)

        // 泛型类型参数: class Foo<T, U : Bound>
        val typeParams = parent.parseTypeParams()

        val defaultCtorModifiers = parent.parseModifiers()
        val primaryCtorAttrs = mutableListOf<LClassAttribute>()
        val defaultCtor: LClassConstructor? = if (hasNext() && (
                    peek().type == TokenType.LParen ||
                            (peek().type == TokenType.Keyword && peek().text == "constructor")
                    )
        ) {
            val ctorStartTok = peek()
            if (peek().type == TokenType.Keyword && peek().text == "constructor") next()
            val args = parsePrimaryCtorParams(primaryCtorAttrs)
            LClassConstructor(defaultCtorModifiers, args, Module(emptyList())).also { it.at(ctorStartTok.row, ctorStartTok.col) }
        } else null

        // superCtorArgs holds the constructor arguments for the first (primary) parent class:
        //   class Dog(val name: String) : Animal(name)  →  superCtorArgs = [VariableGet("name")]
        // Interface entries never have constructor calls so their arg lists remain empty.
        var superCtorArgs: List<Expression> = emptyList()

        val inheritances = if (peek().type == TokenType.Colon) {
            next()
            skipWs()
            val types = mutableListOf(parent.parseType())
            // Capture super-constructor args for the first parent type.
            if (hasNext() && peek().type == TokenType.LParen) {
                next(); superCtorArgs = parent.parseList { parent.expr.parse() }
            }
            while (hasNext() && peek().type == TokenType.Comma) {
                next(); skipWs()
                if (!hasNext() || peek().type == TokenType.LBrace) break
                types.add(parent.parseType())
                // Subsequent parents (interfaces) — consume args but discard.
                if (hasNext() && peek().type == TokenType.LParen) {
                    next(); parent.parseList { parent.expr.parse() }
                }
            }
            types
        } else listOf(LType("Any"))

        val module = if (hasNext() && peek().type == TokenType.LBrace) {
            next()
            val m = parse(Module.Builder()).build()
            next(TokenType.RBrace)
            m
        } else Module(emptyList())

        val functions = module.statements.filterIsInstance<LFunction>().toMutableList()
        if (defaultCtor != null) functions.add(0, defaultCtor)

        val allAttrs = primaryCtorAttrs + module.statements.filterIsInstance<LClassAttribute>()

        return LClass(name.text, typeParams, inheritances, modifier, allAttrs, functions, annotations,
            superCtorArgs = superCtorArgs).also { it.at(name.row, name.col) }
    }

    private fun parsePrimaryCtorParams(
        primaryCtorAttrs: MutableList<LClassAttribute>
    ): List<Argument> {
        next(TokenType.LParen)
        return parent.parseList {
            val propKind = if (peek().type == TokenType.Keyword &&
                peek().text in setOf("val", "var")
            ) next().text else null

            val paramName = next(TokenType.Identifier) {
                "Expected parameter name, got '${it.text}'"
            }.text
            next(TokenType.Colon)
            val paramType = parent.parseType()

            val defaultVal = if (peek().type == TokenType.Operator && peek().text == "=") {
                next()
                expr.parse()
            } else null

            if (propKind != null) {
                primaryCtorAttrs.add(
                    LClassAttribute(emptyList(), paramName, paramType, defaultVal, propKind == "val")
                )
            }

            Argument(paramName, paramType, defaultVal)
        }
    }

    private fun parse(ast: Module.Builder): Module.Builder {
        while (hasNext() && peek().type != TokenType.RBrace) {
            if (peek().type == TokenType.NewLine) {
                next()
                continue
            }
            if (peek().type == TokenType.Semicolon)
                diagnostic("Unexpected statement terminator")
            ast.add(once())
            skipStmtEnd()
        }
        return ast
    }

    private fun once(): Statement {
        val annotations = parent.parseAnnotations()
        val modifiers = parent.parseModifiers(extra = setOf("native"))
        if (peek().type != TokenType.Keyword) {
            diagnostic("${peek().text}: Expected Keyword", throws = true)
        }
        return when (peek().text) {
            in setOf("val", "var") -> parseClassAttributeDecl(modifiers)
            "fun" -> inModule.parseFunctionDecl(modifiers, annotations)
            "constructor" -> parseClassConstructor(modifiers)
            else -> TODO()
        }
    }

    private fun parseClassAttributeDecl(modifiers: List<String>): LClassAttribute {
        val kwTok = next()
        val isConstant = kwTok.text == "val"
        val name = next(TokenType.Identifier) { "Expected attribute name, actually \"${it.text}\"" }.text
        val type = if (peek().type == TokenType.Colon) {
            next()
            parent.parseType()
        } else
            LType.INFER
        val initExpr = if (peek().type == TokenType.Operator && peek().text == "=") {
            next()
            expr.parse()
        } else null
        return LClassAttribute(modifiers, name, type, initExpr, isConstant).also { it.at(kwTok.row, kwTok.col) }
    }

    private fun parseClassConstructor(modifiers: List<String>): LClassConstructor {
        val ctorTok = next(TokenType.Keyword, "constructor")
        next(TokenType.LParen)
        val arguments = parent.parseList { parseFunctionArgument() }
        next(TokenType.LBrace)
        val module = inModule.parse(Module.Builder()).build()
        next(TokenType.RBrace)
        val ctor = LClassConstructor(modifiers, arguments, module)
        return ctor.also { it.at(ctorTok.row, ctorTok.col) }
    }
}