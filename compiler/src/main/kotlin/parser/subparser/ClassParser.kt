package yukifuri.lang.lingspled.compiler.parser.subparser

import yukifuri.lang.lingspled.compiler.ast.LAFieldAccessExpr
import yukifuri.lang.lingspled.compiler.ast.LAInvokeExpr
import yukifuri.lang.lingspled.compiler.ast.LAStatement
import yukifuri.lang.lingspled.compiler.ast.cls.LAAnnotation
import yukifuri.lang.lingspled.compiler.ast.cls.LAClass
import yukifuri.lang.lingspled.compiler.ast.cls.LAClassAttribute
import yukifuri.lang.lingspled.compiler.ast.cls.LAClassConstructor
import yukifuri.lang.lingspled.compiler.ast.cls.LAClassConstructorParameter
import yukifuri.lang.lingspled.compiler.ast.cls.LAConstructorDelegation
import yukifuri.lang.lingspled.compiler.ast.cls.LAPrimaryConstructor
import yukifuri.lang.lingspled.compiler.ast.module.LAFunction
import yukifuri.lang.lingspled.compiler.ast.module.LAModule
import yukifuri.lang.lingspled.compiler.general.LTypeRef
import yukifuri.lang.lingspled.compiler.lexer.Position
import yukifuri.lang.lingspled.compiler.lexer.token.TokenType
import yukifuri.lang.lingspled.compiler.parser.Parser
import yukifuri.lang.lingspled.compiler.util.Modifiers
import yukifuri.lang.lingspled.compiler.util.cast

class ClassParser(parent: Parser) : SubParser(parent) {

    private fun parse(): LAStatement {
        skipWs()
        val annotations = parseAnnotations()
        skipWs()
        val modifiers = parseModifiers()
        skipWs()
        return when {
            peek("fun") -> module.functionDecl(annotations, modifiers)
            peek("val") || peek("var") -> attribute(annotations, modifiers)
            peek("constructor") -> constructor(annotations, modifiers)
            peek("init") -> diagnostic("Init blocks are not supported yet")
            peek("deconstructor") -> diagnostic("Deconstructors are not supported yet")
            peek("class") -> diagnostic("Nested classes are not supported yet")
            else -> diagnostic("Unexpected token in class body: \"${peek().text}\"")
        }.also { skipStmtEnd() }
    }

    private fun attribute(
        annotations: List<LAAnnotation>,
        modifiers: Pair<Modifiers.Access, List<Modifiers.ModifierType>>
    ): LAClassAttribute {
        val pos = peek().position
        val mutable = next().text == "var"
        val name = expectId().text
        val type = if (tryConsume(TokenType.Colon)) parseTypeRef() else null
        val init = if (peek("=", TokenType.Operator)) {
            next()
            expr.parse(inModule = true)
        } else null
        // TODO: getter/setter
        return LAClassAttribute(
            annotations, modifiers.first, modifiers.second.cast(),
            mutable, name, type, init, null, null, pos
        )
    }

    private fun constructor(
        annotations: List<LAAnnotation>,
        modifiers: Pair<Modifiers.Access, List<Modifiers.ModifierType>>
    ): LAClassConstructor {
        val pos = peek().position
        if (modifiers.second.isNotEmpty())
            diagnostic("Constructors only accept access modifiers", start = pos)
        expect(TokenType.Identifier, "constructor")
        val params = parseList(TokenType.LParen, TokenType.RParen) { module.parseParameter() }

        val delegation = if (tryConsume(TokenType.Colon)) {
            skipWs()
            val target = peek()
            if (target.text != "this" && target.text != "super")
                diagnostic("Expected 'this' or 'super' constructor delegation")
            next()
            val args = parseList(TokenType.LParen, TokenType.RParen) { module.parseArgument() }
            if (target.text == "this") LAConstructorDelegation.This(args)
            else LAConstructorDelegation.Super(args)
        } else null

        val body = if (peek(TokenType.LBrace)) module.parseBody() else null
        return LAClassConstructor(annotations, modifiers.first, params, delegation, body, pos)
    }

    fun cls(
        annotations: List<LAAnnotation>,
        modifiers: Pair<Modifiers.Access, List<Modifiers.ModifierType>>
    ): LAClass {
        val pos = expect(TokenType.Keyword, "class").position
        val name = expectId().text
        val tp =
            if (peek("<", TokenType.Operator)) parseTypeParams()
            else emptyList()

        val primary = primaryCtor()

        val supertypes =
            if (tryConsume(TokenType.Colon)) parseSuperDecl()
            else buildAny(pos) to listOf(LTypeRef.any)

        val resolvedTp = parseWhereClause(tp)

        val ctors = mutableListOf<LAClassConstructor>()
        val functions = mutableListOf<LAFunction>()
        val attrs = mutableListOf<LAClassAttribute>()

        if (tryConsume(TokenType.LBrace)) {
            skipWs()
            while (hasNext() && !peek(TokenType.RBrace)) {
                when (val member = parse()) {
                    is LAClassConstructor -> ctors.add(member)
                    is LAClassAttribute -> attrs.add(member)
                    is LAFunction -> functions.add(member)
                    else -> diagnostic("Unexpected member in class body", start = member.position)
                }
                skipWs()
            }
            if (!tryConsume(TokenType.RBrace))
                diagnostic("Expected '}' to close class body", start = pos)
        }

        return LAClass(
            annotations, modifiers.first, modifiers.second.cast(),
            name, resolvedTp, supertypes.first, supertypes.second,
            primary, ctors, functions, attrs, pos
        )
    }

    // (val x: Int) / constructor(...) / private constructor(...)；没有参数列表则返回 null
    private fun primaryCtor(): LAPrimaryConstructor? {
        if (!hasNext()) return null

        val access: Modifiers.Access
        if (!peek(TokenType.LParen)) {
            val s = parent.ts.snapshot()
            val acc = if (peek().text in Modifiers.Access.map) Modifiers.Access.map[next().text] else null
            if (!tryConsume(TokenType.Identifier, "constructor")) {
                parent.ts.restore(s)
                return null
            }
            access = acc ?: Modifiers.Access.Public
        } else access = Modifiers.Access.Public

        val pos = peek().position
        val params = parseList(TokenType.LParen, TokenType.RParen) { ctorParameter() }
        return LAPrimaryConstructor(emptyList(), access, params, pos)
    }

    // [@Anno] [access] [val|var] name: Type [= default]
    private fun ctorParameter(): LAClassConstructorParameter {
        val annotations = parseAnnotations()
        skipWs()
        val pos = peek().position
        val access =
            if (peek().text in Modifiers.Access.map) Modifiers.Access.map[next().text]!!
            else Modifiers.Access.Public
        val mutable = when {
            tryConsume(TokenType.Keyword, "val") -> false
            tryConsume(TokenType.Keyword, "var") -> true
            else -> null
        }
        val name = expectId().text
        expect(TokenType.Colon)
        val type = parseTypeRef()
        val default = if (tryConsume(TokenType.Operator, "=")) expr.parse() else null
        return LAClassConstructorParameter(annotations, access, mutable, name, type, default, pos)
    }

    private fun parseSuperDecl(): Pair<LAInvokeExpr, List<LTypeRef>> {
        val pos = peek().position
        var superclass: LAInvokeExpr? = null
        val supertypes = mutableListOf<LTypeRef>()

        do {
            skipWs()
            val pos = peek().position
            val type = parseTypeRef()

            if (peek(TokenType.LParen)) {
                // 带 (...) 的是超类构造调用，只允许一个
                if (superclass != null)
                    diagnostic("Multiple superclasses are not supported", start = pos)

                val args = parseList(TokenType.LParen, TokenType.RParen) {
                    module.parseArgument()
                }
                superclass = LAInvokeExpr(
                    LAFieldAccessExpr(field = type.name, position = pos),
                    args,
                    pos
                )
                supertypes.add(0, type)   // 超类放在列表首位
            } else {
                supertypes.add(type)      // 无括号的视为接口
            }
            skipWs()
        } while (tryConsume(TokenType.Comma))

        if (superclass == null)
            supertypes.add(0, LTypeRef.any)

        return (superclass ?: buildAny(pos)) to supertypes
    }

    companion object {
        val buildAny = { pos: Position ->
            LAInvokeExpr(LAFieldAccessExpr(field = "Any", position = pos), listOf(), pos) }
    }
}