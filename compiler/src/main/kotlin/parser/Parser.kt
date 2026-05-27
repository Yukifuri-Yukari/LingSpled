package yukifuri.lang.lingspled.compiler.parser

import yukifuri.lang.lingspled.compiler.ast.base.Expression
import yukifuri.lang.lingspled.compiler.ast.module.Annotation
import yukifuri.lang.lingspled.compiler.ast.module.Module
import yukifuri.lang.lingspled.compiler.ast.type.LType
import yukifuri.lang.lingspled.compiler.ast.type.TypeParam
import yukifuri.lang.lingspled.compiler.diagnostics.DiagnosticLevel
import yukifuri.lang.lingspled.compiler.diagnostics.Diagnostics
import yukifuri.lang.lingspled.compiler.lexer.token.TokenStream
import yukifuri.lang.lingspled.compiler.lexer.token.TokenType
import yukifuri.lang.lingspled.compiler.parser.subparser.ClassParser
import yukifuri.lang.lingspled.compiler.parser.subparser.ExpressionParser
import yukifuri.lang.lingspled.compiler.parser.subparser.InModuleParser
import yukifuri.lang.lingspled.compiler.parser.subparser.ModuleParser
import yukifuri.lang.lingspled.compiler.parser.subparser.ModuleParser.Companion.accessModifiers
import yukifuri.lang.lingspled.compiler.parser.subparser.ModuleParser.Companion.allModifiers

class Parser(
    var ts: TokenStream,
    var diagnostics: Diagnostics
) {
    var ast = Module(listOf())

    val module = ModuleParser(this)
    val expr = ExpressionParser(this)
    val inModule = InModuleParser(this)
    val classParser = ClassParser(this)

    fun parseAnnotations(): List<Annotation> {
        val annotations = mutableListOf<Annotation>()
        while (ts.hasNext() && ts.peek().type == TokenType.At) {
            ts.next() // consume '@'
            val name = ts.next().text // annotation name
            val arguments = mutableMapOf<String, Expression>()
            if (ts.hasNext() && ts.peek().type == TokenType.LParen) {
                ts.next() // consume '('
                while (ts.hasNext() && ts.peek().type != TokenType.RParen) {
                    val key = ts.next().text // argument name
                    ts.next() // consume '='
                    arguments[key] = expr.parse()
                    if (ts.hasNext() && ts.peek().type == TokenType.Comma) ts.next()
                }
                ts.next() // consume ')'
            }
            annotations.add(Annotation(name, arguments))
            // 注解后可能有换行
            while (ts.hasNext() && ts.peek().type == TokenType.NewLine) ts.next()
        }
        return annotations
    }

    fun parseModifiers(accepted: Set<String> = allModifiers, extra: Set<String> = setOf()): List<String> {
        val modifiers = mutableListOf<String>()
        while (ts.hasNext()) {
            val t = ts.peek()
            if (t.type == TokenType.Keyword && (t.text in accepted || t.text in extra))
                modifiers.add(ts.next().text)
            else break
        }
        if (modifiers.none { it in accessModifiers })
            modifiers.add(0, "public")
        return modifiers
    }

    /**
     * 解析类型标注：[@Identifier]* Identifier[?]
     * 调用前 ts 应正对类型的第一个 token（@ 或 标识符）。
     */
    fun parseType(): LType {
        val typeAnnotations = mutableListOf<Annotation>()
        while (ts.hasNext() && ts.peek().type == TokenType.At) {
            ts.next()
            typeAnnotations.add(Annotation(ts.next().text))
        }
        // 函数类型: (A, B) -> C
        if (ts.hasNext() && ts.peek().type == TokenType.LParen) {
            ts.next() // consume '('
            val paramTypes = mutableListOf<LType>()
            skipWs()
            while (ts.hasNext() && ts.peek().type != TokenType.RParen) {
                paramTypes.add(parseType())
                skipWs()
                if (ts.peek().type == TokenType.Comma) ts.next()
                skipWs()
            }
            ts.next() // consume ')'
            skipWs()
            if (ts.hasNext() && ts.peek().type == TokenType.Operator && ts.peek().text == "->") {
                ts.next() // consume '->'
                skipWs()
                val returnType = parseType()
                return LType("->", paramTypes + returnType, false, typeAnnotations)
            }
        }
        val name = ts.next().text
        val typeArgs = if (ts.hasNext() && ts.peek().type == TokenType.Operator && ts.peek().text == "<") {
            ts.next() // consume '<'
            val args = mutableListOf<LType>()
            while (ts.hasNext()) {
                skipWs()
                if (ts.peek().type == TokenType.Operator && ts.peek().text == ">") { ts.next(); break }
                args.add(parseType())
                skipWs()
                when {
                    ts.peek().type == TokenType.Operator && ts.peek().text == ">" -> { ts.next(); break }
                    ts.peek().type == TokenType.Comma -> ts.next()
                    else -> break
                }
            }
            args
        } else emptyList()
        val nullable = ts.hasNext() && ts.peek().type == TokenType.Question
        if (nullable) ts.next()
        return LType(name, typeArgs, nullable, typeAnnotations)
    }

    /** 解析泛型形参列表: `<T, U : Bound>` 用于声明位置（fun / class）。*/
    fun parseTypeParams(): List<TypeParam> {
        if (!ts.hasNext() || ts.peek().type != TokenType.Operator || ts.peek().text != "<") return emptyList()
        ts.next() // consume '<'
        val params = mutableListOf<TypeParam>()
        while (ts.hasNext()) {
            skipWs()
            if (ts.peek().type == TokenType.Operator && ts.peek().text == ">") { ts.next(); break }
            val name = ts.next().text
            skipWs()
            val bound = if (ts.peek().type == TokenType.Colon) { ts.next(); skipWs(); parseType() } else null
            params.add(TypeParam(name, bound))
            skipWs()
            when {
                ts.peek().type == TokenType.Operator && ts.peek().text == ">" -> { ts.next(); break }
                ts.peek().type == TokenType.Comma -> ts.next()
                else -> break
            }
        }
        return params
    }

    /** 解析调用位置的类型实参: `<Int, String>` 后必须紧跟 `(`，否则回溯返回空列表。*/
    fun parseTypeArgs(): List<LType> {
        if (!ts.hasNext() || ts.peek().type != TokenType.Operator || ts.peek().text != "<") return emptyList()
        val snap = ts.snapshot()
        ts.next() // consume '<'
        val args = mutableListOf<LType>()
        try {
            while (ts.hasNext()) {
                skipWs()
                if (ts.peek().type == TokenType.Operator && ts.peek().text == ">") { ts.next(); break }
                args.add(parseType())
                skipWs()
                when {
                    ts.peek().type == TokenType.Operator && ts.peek().text == ">" -> { ts.next(); break }
                    ts.peek().type == TokenType.Comma -> ts.next()
                    else -> { ts.restore(snap); return emptyList() }
                }
            }
            if (!ts.hasNext() || ts.peek().type != TokenType.LParen) { ts.restore(snap); return emptyList() }
        } catch (_: Exception) {
            ts.restore(snap); return emptyList()
        }
        return args
    }

    fun parseBlock(): Module {
        return if (ts.peek().type == TokenType.LBrace)
            inModule.parseWithBraces(Module.Builder()).build()
        else
            Module(listOf(inModule.once(true)))
    }

    fun skipWs() {
        while (ts.hasNext() && ts.peek().type == TokenType.NewLine) ts.next()
    }

    fun <T> parseList(
        terminator: TokenType = TokenType.RParen,
        separator: TokenType = TokenType.Comma,
        parseElement: () -> T,
    ): List<T> {
        val list = mutableListOf<T>()
        while (true) {
            skipWs()
            if (ts.peek().type == terminator) {
                ts.next()
                break
            }
            list.add(parseElement())
            skipWs()
            if (ts.peek().type == terminator) {
                ts.next()
                break
            }
            if (ts.next().type != separator) {
                diagnostics.add(
                    "Expected '${separator.name}' or '${terminator.name}'", DiagnosticLevel.Error,
                    "", ts.peek().row, ts.peek().col
                )
                throw IllegalStateException()
            }
        }
        return list
    }

    fun parse(): Parser {
        val ast = Module.Builder()
        module.parse(ast)
        this.ast = ast.build()
        return this
    }

    fun reset(ts: TokenStream, diagnostics: Diagnostics) {
        this.ts = ts
        this.diagnostics = diagnostics
        ast = Module(listOf())
    }
}