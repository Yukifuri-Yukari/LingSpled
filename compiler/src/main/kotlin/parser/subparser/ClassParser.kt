package yukifuri.lang.lingspled.compiler.parser.subparser

import yukifuri.lang.lingspled.compiler.ast.LAFieldAccessExpr
import yukifuri.lang.lingspled.compiler.ast.LAInvokeExpr
import yukifuri.lang.lingspled.compiler.ast.LAParameter
import yukifuri.lang.lingspled.compiler.ast.LAStatement
import yukifuri.lang.lingspled.compiler.ast.cls.LAAnnotation
import yukifuri.lang.lingspled.compiler.ast.cls.LAClass
import yukifuri.lang.lingspled.compiler.ast.cls.LAClassAttribute
import yukifuri.lang.lingspled.compiler.ast.cls.LAClassConstructor
import yukifuri.lang.lingspled.compiler.ast.cls.LAClassConstructorParameter
import yukifuri.lang.lingspled.compiler.ast.cls.LAConstructorDelegation
import yukifuri.lang.lingspled.compiler.ast.cls.LADeinitBlock
import yukifuri.lang.lingspled.compiler.ast.cls.LAInitBlock
import yukifuri.lang.lingspled.compiler.ast.cls.LAPrimaryConstructor
import yukifuri.lang.lingspled.compiler.ast.module.LAFunction
import yukifuri.lang.lingspled.compiler.ast.module.LAModule
import yukifuri.lang.lingspled.compiler.general.LTypeRef
import yukifuri.lang.lingspled.compiler.lexer.Position
import yukifuri.lang.lingspled.compiler.lexer.token.TokenType
import yukifuri.lang.lingspled.compiler.parser.Parser
import yukifuri.lang.lingspled.compiler.util.Modifiers
import yukifuri.lang.lingspled.compiler.util.resolve

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
            peek("init") -> initBlock(annotations, modifiers)
            peek("deinit") -> deinitBlock(annotations, modifiers)
            peek("class") -> cls(annotations, modifiers)
            else -> diagnostic("Unexpected token in class body: \"${peek().text}\"")
        }.also { skipStmtEnd() }
    }

    private fun attribute(
        annotations: List<LAAnnotation>,
        modifiers: Pair<Modifiers.Access, List<String>>
    ): LAClassAttribute {
        val pos = peek().position
        val mutable = next().text == "var"
        val name = expectId().text
        val type = if (tryConsume(TokenType.Colon)) parseTypeRef() else null
        val init = if (peek("=", TokenType.Operator)) {
            next()
            expr.parse(inModule = true)
        } else null
        val delegator = if (peek("by")) {
            next()
            expr.parse(inModule = true)
        } else null

        // getter/setter：可跟在属性后（允许换行缩进），各至多一个、顺序不限，合成为 getXxx/setXxx
        var getter: LAFunction? = null
        var setter: LAFunction? = null
        while (true) {
            val snap = parent.ts.snapshot()
            skipWs()
            val isGet = hasNext() && peek("get")
            val isSet = hasNext() && peek("set")
            if (!isGet && !isSet) {
                parent.ts.restore(snap)
                break
            }
            val accPos = next().position
            if (!peek(TokenType.LParen)) {        // 'get'/'set' 后无 '(' 不是访问器
                parent.ts.restore(snap)
                break
            }
            if (isGet) {
                if (getter != null) diagnostic("Duplicate getter for property '$name'", start = accPos)
                getter = parseGetter(name, type, accPos)
            } else {
                if (setter != null) diagnostic("Duplicate setter for property '$name'", start = accPos)
                setter = parseSetter(name, type, accPos)
            }
        }

        return LAClassAttribute(
            annotations, modifiers.first, modifiers.second.resolve(Modifiers.Property.map, "property"),
            mutable, name, type, init, delegator, getter, setter, pos
        )
    }

    // 属性 foo 的访问器合成为 getFoo / setFoo（首字母大写）
    private fun accessorName(prefix: String, property: String) =
        prefix + property.replaceFirstChar { it.uppercaseChar() }

    // get() [: Type] (= expr | { ... })，返回类型缺省取属性类型
    private fun parseGetter(property: String, propertyType: LTypeRef?, pos: Position): LAFunction {
        expect(TokenType.LParen)
        expect(TokenType.RParen)
        val ret = if (tryConsume(TokenType.Colon)) parseTypeRef() else propertyType ?: LTypeRef.infer
        return LAFunction(
            emptyList(), Modifiers.Access.Local, emptyList(), emptyList(),
            null, accessorName("get", property), emptyList(), ret, parseAccessorBody(), pos
        )
    }

    // set(value[: Type]) (= expr | { ... })，参数类型缺省取属性类型，返回 Unit
    private fun parseSetter(property: String, propertyType: LTypeRef?, pos: Position): LAFunction {
        expect(TokenType.LParen)
        val paramName = expectId().text
        val paramType = if (tryConsume(TokenType.Colon)) parseTypeRef() else propertyType ?: LTypeRef.infer
        expect(TokenType.RParen)
        val param = LAParameter(paramName, paramType)
        return LAFunction(
            emptyList(), Modifiers.Access.Local, emptyList(), emptyList(),
            null, accessorName("set", property), listOf(param), LTypeRef.unit, parseAccessorBody(), pos
        )
    }

    private fun parseAccessorBody(): LAModule = when {
        peek(TokenType.LBrace) -> module.parseBody()
        // 表达式体：get() = expr 脱糖为单条 return
        peek("=", TokenType.Operator) -> {
            val eq = next().position
            LAModule(listOf(LAFunction.LAReturnStmt(expr.parse(inModule = true), eq)), eq)
        }
        else -> diagnostic("Expected accessor body '{ ... }' or '= expr'")
    }

    private fun constructor(
        annotations: List<LAAnnotation>,
        modifiers: Pair<Modifiers.Access, List<String>>
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

    private fun initBlock(
        annotations: List<LAAnnotation>,
        modifiers: Pair<Modifiers.Access, List<String>>
    ): LAInitBlock {
        val pos = peek().position
        if (annotations.isNotEmpty() || modifiers.second.isNotEmpty())
            diagnostic("init blocks accept no annotations or modifiers", start = pos)
        next() // init
        return LAInitBlock(module.parseBody(), pos)
    }

    private fun deinitBlock(
        annotations: List<LAAnnotation>,
        modifiers: Pair<Modifiers.Access, List<String>>
    ): LADeinitBlock {
        val pos = peek().position
        if (annotations.isNotEmpty() || modifiers.second.isNotEmpty())
            diagnostic("deinit blocks accept no annotations or modifiers", start = pos)
        next() // deinit
        return LADeinitBlock(module.parseBody(), pos)
    }

    fun cls(
        annotations: List<LAAnnotation>,
        modifiers: Pair<Modifiers.Access, List<String>>
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
        val inits = mutableListOf<LAInitBlock>()
        var deinit: LADeinitBlock? = null
        val nested = mutableListOf<LAClass>()

        if (tryConsume(TokenType.LBrace)) {
            skipWs()
            while (hasNext() && !peek(TokenType.RBrace)) {
                when (val member = parse()) {
                    is LAClassConstructor -> ctors.add(member)
                    is LAInitBlock -> inits.add(member)
                    is LADeinitBlock -> {
                        if (deinit != null)
                            diagnostic("A class may declare at most one deinit block", start = member.position)
                        deinit = member
                    }
                    is LAClass -> nested.add(member)
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
            annotations, modifiers.first, modifiers.second.resolve(Modifiers.Class.map, "class"),
            name, resolvedTp, supertypes.first, supertypes.second,
            primary, ctors, functions, attrs, inits, deinit, nested, pos
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