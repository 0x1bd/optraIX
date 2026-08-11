package org.kvxd.optraix

import net.lenni0451.mcstructs.nbt.NbtTag
import net.lenni0451.mcstructs.nbt.tags.CompoundTag
import net.lenni0451.mcstructs.nbt.tags.StringTag
import org.kvxd.optraix.command.server.Calculator
import org.kvxd.optraix.nbt.list
import org.kvxd.optraix.nbt.tag
import org.kvxd.optraix.net.OptraIxServer
import org.kvxd.optraix.player.Player
import java.io.File
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CalculatorTest {

    @Test
    fun evaluatesDecimalExpressionsWithStandardPrecedence() {
        assertEquals(
            Calculator.Value.Number(BigDecimal("7.5")),
            Calculator.evaluate("1.5 + 2 * 3"),
        )
        assertEquals(
            Calculator.Value.Number(BigDecimal("8")),
            Calculator.evaluate("2 ** 3"),
        )
    }

    @Test
    fun calculatesAndFormatsHexAndBinaryBusses() {
        val value = Calculator.evaluate("0xF0 | 0b0011")
        assertEquals(Calculator.Value.Bus(java.math.BigInteger("243"), 8), value)
        assertEquals(
            listOf("= 243", "  hex: 0xF3", "  bin: 0b11110011"),
            Calculator.format(value),
        )
        assertEquals(
            Calculator.Value.Bus(java.math.BigInteger("5"), 4),
            Calculator.evaluate("~b'1010'"),
        )
    }

    @Test
    fun basePrefixesSetTheDefaultForBareLiterals() {
        assertEquals(
            Calculator.Value.Bus(java.math.BigInteger("15"), 5),
            Calculator.evaluate("bin 0101 + 1010"),
        )
        assertEquals(
            Calculator.Value.Bus(java.math.BigInteger("0"), 4),
            Calculator.evaluate("bin 0101 & 1010"),
        )
        assertEquals(
            Calculator.Value.Bus(java.math.BigInteger("255"), 8),
            Calculator.evaluate("hex F0 | 0F"),
        )
        assertEquals(Calculator.Value.Number(BigDecimal("3.5")), Calculator.evaluate("dec 1.5 + 2"))
    }

    @Test
    fun calculatorCompletionsOnlyOfferValidNextSyntax() {
        assertEquals(listOf("bin", "hex", "dec"), Calculator.completions("").candidates)
        assertEquals(listOf("bin", "hex", "dec"), Calculator.completions("b").candidates)
        assertEquals(
            listOf("0b", "0x", "b'", "true", "false", "not", "("),
            Calculator.completions("bin ").candidates,
        )
        assertTrue(Calculator.completions("bin 0101 ").candidates.containsAll(listOf("+", "&", "<<")))
        assertTrue(Calculator.completions("true a").candidates.contains("and"))
    }

    @Test
    fun calcCommandExposesContextAwareTabCompletions() {
        val server = OptraIxServer(ServerConfig(port = 0, runDirectory = File("build/tmp/calc-completion-test")))
        val dispatcher = server.commands.dispatcher

        fun suggestions(input: String): List<String> = dispatcher
            .getCompletionSuggestions(dispatcher.parse(input, null))
            .join()
            .list
            .map { it.text }

        assertEquals(listOf("bin", "dec", "hex"), suggestions("c "))
        assertTrue(suggestions("c bin 0101 ").containsAll(listOf("+", "&", "<<")))
        assertEquals(listOf("and"), suggestions("c true a"))
    }

    @Test
    fun evaluatesBooleanLogicAndComparisons() {
        assertEquals(Calculator.Value.Boolean(true), Calculator.evaluate("true and not false"))
        assertEquals(Calculator.Value.Boolean(true), Calculator.evaluate("0x10 >> 2 == 4"))
    }

    @Test
    fun rejectsInvalidOperationsWithoutUsingAHostScriptEngine() {
        val error = assertFailsWith<Calculator.CalculatorException> {
            Calculator.evaluate("true + 1")
        }
        assertTrue(error.message!!.contains("expected a number"))
    }

    @Test
    fun calcAndCCommandsReplyWithTheResult() {
        val server = OptraIxServer(ServerConfig(port = 0, runDirectory = File("build/tmp/calc-test")))
        val sink = RecordingSink()
        val player = Player(1, UUID.randomUUID(), "Tester", sink)

        server.commands.execute(player, "calc 1.25 * 4")
        server.commands.execute(player, "c 0b1010 & 0b0110")

        val messages = sink.messages.map(::plain)
        assertTrue(messages.contains("= 5"), "decimal result missing: $messages")
        assertTrue(messages.contains("= 2"), "alias result missing: $messages")
        assertTrue(messages.contains("  bin: 0b0010"), "binary result missing: $messages")
    }

    private fun plain(tag: NbtTag): String {
        if (tag !is CompoundTag) return ""
        val head = (tag.tag("text") as? StringTag)?.value ?: ""
        return head + (tag.list("extra")?.joinToString("") { plain(it) } ?: "")
    }
}
