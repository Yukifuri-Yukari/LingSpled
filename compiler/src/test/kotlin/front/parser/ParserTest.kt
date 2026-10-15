package yukifuri.lang.lingspled.compiler.front.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import yukifuri.lang.lingspled.compiler.bridge.File
import yukifuri.lang.lingspled.compiler.exception.Diagnostics
import yukifuri.lang.lingspled.compiler.exception.ParserException
import yukifuri.lang.lingspled.compiler.front.Operator
import yukifuri.lang.lingspled.compiler.front.lexeme.Lexer
import yukifuri.lang.lingspled.compiler.front.struct.*
import yukifuri.libs.compilation.stream.CharStream

/** End-to-end tests for the source lexer/parser and its CST. */
class ParserTest {

    private fun parse(source: String): LCFile {
        val diagnostics = Diagnostics()
        diagnostics.setCurrentFile(File("parser-test.ling"))
        val tokens = Lexer(diagnostics).lex(CharStream(source))
        return Parser(diagnostics).parse(tokens)
    }

    private fun module(source: String): List<LCStatement> = parse(source).module.stmt

    @Test
    fun packageAndImports() {
        val file = parse(
            """
            package demo.lang
            import alpha.beta.Gamma
            import alpha.beta.*
            import alpha.beta.Value as V
            """.trimIndent()
        )

        assertEquals(LCConfirmedName(listOf("demo", "lang")), file.pkg?.fqn)
        assertEquals(3, file.imports.size)
        assertEquals(LCConfirmedName(listOf("alpha", "beta", "Gamma")), file.imports[0].fqn)
        assertNull(file.imports[0].alias)
        assertFalse(file.imports[0].wildcard)
        assertTrue(file.imports[1].wildcard)
        assertEquals("V", file.imports[2].alias)
    }

    @Test
    fun topLevelFunctionsAndVariables() {
        val statements = module(
            """
            public const val answer: Int = 42
            var name = "Ling"
            inline fun <T : Any> id(value: T = answer): T { return value }
            fun one(): Int = 1
            """.trimIndent()
        )

        val answer = assertIs<LCVariableDecl>(statements[0])
        assertEquals("answer", answer.name)
        assertFalse(answer.mutable)
        assertEquals(listOf(Modifier.Public, Modifier.Const), answer.modifiers)
        assertEquals(LTypeRef("Int", emptyList(), LVariance.None), answer.type)
        assertEquals(42, assertIs<LCLiteral.IntLit>(answer.init).value)

        val name = assertIs<LCVariableDecl>(statements[1])
        assertTrue(name.mutable)
        assertNull(name.type)
        assertEquals("Ling", assertIs<LCLiteral.StringLit>(name.init).value)

        val id = assertIs<LCFunction>(statements[2])
        assertEquals("id", id.name)
        assertEquals(listOf(LTypeParameterDecl("T", LTypeRef("Any", emptyList(), LVariance.None), LVariance.None)), id.typeParams)
        assertEquals(LTypeRef("T", emptyList(), LVariance.None), id.ret)
        assertNull(id.params.singleOrNull()?.modifier)
        assertEquals("value", id.params.single().name)
        assertEquals("answer", assertIs<LCExprVariableGet>(id.params.single().default).name)
        assertIs<LCCtrlReturn>(id.module.stmt.single())

        val one = assertIs<LCFunction>(statements[3])
        assertEquals(LTypeRef("Int", emptyList(), LVariance.None), one.ret)
        assertEquals(1, assertIs<LCCtrlReturn>(one.module.stmt.single()).expr?.let { assertIs<LCLiteral.IntLit>(it).value })
    }

    @Test
    fun parameterModifiersDefaultsAndFunctionTypeReferences() {
        val function = assertIs<LCFunction>(module(
            "fun f(crossinline a: (Int, String) -> Boolean, noinline b: *, vararg c: List<out Number>) {}"
        ).single())

        assertEquals(LParameterModifier.Crossinline, function.params[0].modifier)
        assertEquals(
            LFunctionTypeRef(
                listOf(LTypeRef("Int", emptyList(), LVariance.None), LTypeRef("String", emptyList(), LVariance.None)),
                LTypeRef("Boolean", emptyList(), LVariance.None)
            ),
            function.params[0].type
        )
        assertEquals(LParameterModifier.Noinline, function.params[1].modifier)
        assertEquals(LTypeParameterNone, function.params[1].type)
        assertEquals(LParameterModifier.Vararg, function.params[2].modifier)
        assertEquals(
            LTypeRef("List", listOf(LTypeRef("Number", emptyList(), LVariance.Out)), LVariance.None),
            function.params[2].type
        )
    }

    @Test
    fun classesInterfacesConstructorsInheritanceAndMembers() {
        val box = assertIs<LCClass>(module(
            """
            data class Box<T>(val value: T) : Parent<T>(value), Marker {
                init { val initialized = value }
                constructor(other: T) : this(other) { return }
                override fun get(): T = value
                var mutable: T = value
                class Nested<U>
            }
            """.trimIndent()
        ).single())

        assertEquals("Box", box.name)
        assertEquals(listOf(Modifier.Data), box.modifiers)
        assertEquals(listOf(LTypeParameterDecl("T", null, LVariance.None)), box.typeParams)
        assertEquals(
            listOf(
                LTypeRef("Parent", listOf(LTypeRef("T", emptyList(), LVariance.None)), LVariance.None),
                LTypeRef("Marker", emptyList(), LVariance.None)
            ),
            box.supers
        )
        assertEquals(2, box.superConstructorArgs.size)
        assertEquals("value", assertIs<LCExprVariableGet>(box.superConstructorArgs[0].single()).name)
        assertTrue(box.superConstructorArgs[1].isEmpty())

        assertEquals(2, box.body.constructors.size)
        val primary = box.body.constructors[0]
        assertEquals(LParameterModifier.Val, primary.params.single().modifier)
        assertEquals("value", primary.params.single().name)
        val secondary = box.body.constructors[1]
        assertEquals("other", secondary.params.single().name)
        assertIs<LCExprInvoke>(secondary.delegationCall)
        assertEquals(1, secondary.body?.stmt?.size)

        assertIs<LCClass.InitBlock>(box.body.initBlock)
        val get = assertIs<LCFunction>(box.body.members[0])
        assertEquals(listOf(Modifier.Override), get.modifiers)
        assertIs<LCCtrlReturn>(get.module.stmt.single())
        assertIs<LCVariableDecl>(box.body.members[1])
        assertIs<LCClass>(box.body.members[2])

        val iface = assertIs<LCClass>(module("interface Readable<T> : Base<T> { fun read(): T }").single())
        assertEquals("Readable", iface.name)
        assertEquals(LTypeRef("Base", listOf(LTypeRef("T", emptyList(), LVariance.None)), LVariance.None), iface.supers.single())
        val read = assertIs<LCFunction>(iface.body.members.single())
        assertTrue(read.module.stmt.isEmpty())
    }

    @Test
    fun controlFlowStatementsAndElvisControlExpressions() {
        val function = assertIs<LCFunction>(module(
            """
            fun flow(items: Items) {
                for (item in items) { while (item) { if (item) break else continue } }
                return items ?: return
            }
            """.trimIndent()
        ).single())

        val loop = assertIs<LCCtrlFor>(function.module.stmt[0])
        assertEquals("item", loop.name)
        assertEquals("items", assertIs<LCExprVariableGet>(loop.expr).name)
        val whileLoop = assertIs<LCCtrlWhile>(loop.module.stmt.single())
        val conditional = assertIs<LCCtrlIf>(whileLoop.module.stmt.single())
        assertIs<LCCtrlBreak>(conditional.ifBlock.stmt.single())
        assertIs<LCCtrlContinue>(conditional.elseBlock?.stmt?.single())

        val ret = assertIs<LCCtrlReturn>(function.module.stmt[1])
        val elvis = assertIs<LCExprBinary>(ret.expr)
        assertEquals(Operator.Elvis, elvis.operator.operator)
        assertIs<LCCtrlReturn>(elvis.right)
    }

    @Test
    fun literalsUnaryBinaryCallsIndexMemberAccessAndLambdas() {
        val statements = module(
            """
            val expression = -1 + 2 * 3 == 5
            val call = receiver<Int, List<String>>(named = 1) { value: Int -> value++ }
            val access = values[0].field!!.next()?.value!
            val literals = 1L + 2.5F + 3.0 + true + false + null + \"text\" + \"\"\"multi\"\"\" + 'x'
            """.trimIndent()
        )

        val expression = assertIs<LCExprBinary>(assertIs<LCVariableDecl>(statements[0]).init)
        assertEquals(Operator.Eq, expression.operator.operator)
        val sum = assertIs<LCExprBinary>(expression.left)
        assertEquals(Operator.Add, sum.operator.operator)
        assertEquals(Operator.Mul, assertIs<LCExprBinary>(sum.right).operator.operator)
        assertEquals(Operator.Sub, assertIs<LCExprUnary>(sum.left).operator)

        val call = assertIs<LCExprInvoke>(assertIs<LCVariableDecl>(statements[1]).init)
        assertEquals(2, call.typeArgs.size)
        assertEquals("named", call.args.single().name)
        assertEquals("value", call.lambda?.params?.single()?.name)
        assertEquals(Operator.Inc, assertIs<LCExprUnary>(call.lambda?.body?.stmt?.single()).operator)

        val access = assertIs<LCExprMemberAccess>(assertIs<LCVariableDecl>(statements[2]).init)
        assertEquals("value", access.name)
        assertIs<LCExprUnary>(access.recv)

        val literals = assertIs<LCExprBinary>(assertIs<LCVariableDecl>(statements[3]).init)
        val values = buildList {
            fun visit(expr: LCExpression) {
                if (expr is LCExprBinary) { visit(expr.left); visit(expr.right) } else add(expr)
            }
            visit(literals)
        }
        assertEquals(listOf(1L, 2.5F, 3.0, true, false, null, "text", "multi", 'x'), values.map {
            assertIs<LCLiteral<*>>(it).value
        })
    }

    @Test
    fun parenthesizedTypesAsCastsAndGenericComparison() {
        val statements = module(
            """
            val cast = value as? Result<String>
            val compare = a < b && c > d
            val grouped = (a + b) * c
            """.trimIndent()
        )
        val cast = assertIs<LCExprAs>(assertIs<LCVariableDecl>(statements[0]).init)
        assertTrue(cast.isSafe)
        assertEquals(LTypeRef("Result", listOf(LTypeRef("String", emptyList(), LVariance.None)), LVariance.None), cast.type)

        val compare = assertIs<LCExprBinary>(assertIs<LCVariableDecl>(statements[1]).init)
        assertEquals(Operator.And, compare.operator.operator)
        assertEquals(Operator.Lt, assertIs<LCExprBinary>(compare.left).operator.operator)
        assertEquals(Operator.Gt, assertIs<LCExprBinary>(compare.right).operator.operator)

        val grouped = assertIs<LCExprBinary>(assertIs<LCVariableDecl>(statements[2]).init)
        assertEquals(Operator.Mul, grouped.operator.operator)
        assertIs<LCParenGroup>(grouped.left)
    }

    @Test
    fun parserRejectsMalformedTopLevelAndTypes() {
        assertFailsWith<ParserException> { parse("public 123") }
        assertFailsWith<ParserException> { parse("fun broken(a Int) {}") }
        assertFailsWith<ParserException> { parse("fun broken(): Box<String,> {}") }
        assertFailsWith<ParserException> { parse("class Broken : (A) {}") }
    }
}
