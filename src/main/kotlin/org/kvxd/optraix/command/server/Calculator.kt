package org.kvxd.optraix.command.server

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode

object Calculator {

    private val Math = MathContext(34, RoundingMode.HALF_EVEN)

    fun evaluate(expression: String): Value {
        val (input, base) = prefix(expression)
        return Parser(input, base).parse()
    }

    data class Completion(val replaceFrom: Int, val candidates: List<String>)

    fun completions(expression: String): Completion {
        val leading = expression.indexOfFirst { !it.isWhitespace() }.let { if (it < 0) expression.length else it }
        val firstEnd = expression.indexOfFirst(leading) { it.isWhitespace() }.let {
            if (it < 0) expression.length else it
        }
        val first = expression.substring(leading, firstEnd)
        if (firstEnd == expression.length && Prefix.entries.any { it.keyword.startsWith(first, ignoreCase = true) }) {
            return Completion(leading, Prefix.entries.map(Prefix::keyword))
        }

        val bodyStart = Prefix.entries.firstOrNull { it.keyword.equals(first, ignoreCase = true) }
            ?.let { firstEnd }
            ?: leading
        val body = expression.substring(bodyStart)
        if (body.isBlank()) return Completion(expression.length, Values)

        val end = body.indexOfLast { !it.isWhitespace() } + 1
        if (end < body.length) {
            return Completion(bodyStart + body.length, if (endsWithValue(body.substring(0, end))) Operators else Values)
        }

        val last = body[end - 1]
        if (last == '(') return Completion(bodyStart + end, Values)
        if (last == ')') return Completion(bodyStart + end, Operators)
        if (last in OperatorCharacters) {
            val tokenStart = body.lastIndexBefore(end - 1) { it !in OperatorCharacters } + 1
            val before = body.substring(0, tokenStart).trimEnd()
            return if (endsWithValue(before)) {
                Completion(bodyStart + tokenStart, Operators)
            } else {
                Completion(bodyStart + end, Values)
            }
        }

        val tokenStart = body.lastIndexBefore(end - 1) {
            it.isWhitespace() || it in OperatorCharacters || it == '(' || it == ')'
        } + 1
        val token = body.substring(tokenStart, end)
        val before = body.substring(0, tokenStart).trimEnd()
        if (token.matches(Regex("[A-Za-z]+"))) {
            val candidates = if (endsWithValue(before)) LogicalOperators else Values
            if (candidates.any { it.startsWith(token, ignoreCase = true) }) {
                return Completion(bodyStart + tokenStart, candidates)
            }
        }
        return Completion(expression.length, emptyList())
    }

    sealed interface Value {
        data class Number(val value: BigDecimal) : Value
        data class Bus(val value: BigInteger, val width: Int) : Value
        data class Boolean(val value: kotlin.Boolean) : Value
    }

    fun format(value: Value): List<String> = when (value) {
        is Value.Boolean -> listOf("= ${value.value}")
        is Value.Number -> listOf("= ${value.value.stripTrailingZeros().toPlainString()}")
        is Value.Bus -> {
            val width = maxOf(1, value.width)
            val mask = BigInteger.ONE.shiftLeft(width).subtract(BigInteger.ONE)
            val unsigned = value.value.and(mask)
            val hexWidth = (width + 3) / 4
            listOf(
                "= $unsigned",
                "  hex: 0x${unsigned.toString(16).uppercase().padStart(hexWidth, '0')}",
                "  bin: 0b${unsigned.toString(2).padStart(width, '0')}",
            )
        }
    }

    private class Parser(private val input: String, private val defaultBase: Prefix) {
        private var index = 0

        fun parse(): Value {
            if (input.isBlank()) fail("an expression is required")
            val value = or()
            skipSpace()
            if (index != input.length) fail("unexpected '${input[index]}'")
            return value
        }

        private fun or(): Value {
            var value = xor()
            while (true) {
                value = when {
                    take("||") || word("or") -> {
                        val right = bool(xor())
                        Value.Boolean(bool(value) || right)
                    }
                    take("|") -> {
                        val right = xor()
                        Value.Bus(bus(value).or(bus(right)), maxOf(width(value), width(right)))
                    }
                    else -> return value
                }
            }
        }

        private fun xor(): Value {
            var value = and()
            while (true) {
                value = when {
                    take("^^") || word("xor") -> Value.Boolean(bool(value) xor bool(and()))
                    take("^") -> {
                        val right = and()
                        Value.Bus(bus(value).xor(bus(right)), maxOf(width(value), width(right)))
                    }
                    else -> return value
                }
            }
        }

        private fun and(): Value {
            var value = equality()
            while (true) {
                value = when {
                    take("&&") || word("and") -> {
                        val right = bool(equality())
                        Value.Boolean(bool(value) && right)
                    }
                    take("&") -> {
                        val right = equality()
                        Value.Bus(bus(value).and(bus(right)), maxOf(width(value), width(right)))
                    }
                    else -> return value
                }
            }
        }

        private fun equality(): Value {
            var value = comparison()
            while (true) {
                value = when {
                    take("==") -> equal(value, comparison())
                    take("!=") -> !equal(value, comparison())
                    else -> return value
                }.asValue()
            }
        }

        private fun comparison(): Value {
            var value = shift()
            while (true) {
                value = when {
                    take("<=") -> compare(value, shift()) <= 0
                    take(">=") -> compare(value, shift()) >= 0
                    take("<") -> compare(value, shift()) < 0
                    take(">") -> compare(value, shift()) > 0
                    else -> return value
                }.asValue()
            }
        }

        private fun shift(): Value {
            var value = sum()
            while (true) {
                value = when {
                    take("<<") -> shift(value, integer(sum()), true)
                    take(">>") -> shift(value, integer(sum()), false)
                    else -> return value
                }
            }
        }

        private fun sum(): Value {
            var value = product()
            while (true) {
                value = when {
                    take("+") -> add(value, product())
                    take("-") -> subtract(value, product())
                    else -> return value
                }
            }
        }

        private fun product(): Value {
            var value = power()
            while (true) {
                value = when {
                    take("*") -> multiply(value, power())
                    take("/") -> divide(value, power())
                    take("%") -> remainder(value, power())
                    else -> return value
                }
            }
        }

        private fun power(): Value {
            val base = unary()
            return if (take("**")) pow(base, integer(power())) else base
        }

        private fun unary(): Value = when {
            take("+") -> Value.Number(number(unary()))
            take("-") -> Value.Number(number(unary()).negate(Math))
            take("!") || word("not") -> Value.Boolean(!bool(unary()))
            take("~") -> {
                val operand = unary()
                val width = width(operand)
                Value.Bus(bus(operand).not().and(mask(width)), width)
            }
            else -> primary()
        }

        private fun primary(): Value {
            if (take("(")) {
                val value = or()
                if (!take(")")) fail("expected ')'")
                return value
            }
            val start = index
            val token = readToken()
            if (token.isEmpty()) fail("expected a value")
            return when (token.lowercase()) {
                "true", "on" -> Value.Boolean(true)
                "false", "off" -> Value.Boolean(false)
                else -> numberLiteral(token, start)
            }
        }

        private fun numberLiteral(token: String, start: Int): Value {
            val negative = token.startsWith('-')
            val unsigned = token.removePrefix("+").removePrefix("-")
            val sign = if (negative) BigInteger.ONE.negate() else BigInteger.ONE
            return try {
                when {
                    unsigned.startsWith("0x", ignoreCase = true) -> {
                        val digits = unsigned.substring(2).replace("_", "")
                        require(digits.isNotEmpty())
                        Value.Bus(BigInteger(digits, 16) * sign, digits.length * 4)
                    }
                    unsigned.startsWith("0b", ignoreCase = true) -> binary(unsigned.substring(2), sign)
                    unsigned.startsWith("b'") && unsigned.endsWith("'") -> binary(unsigned.substring(2, unsigned.length - 1), sign)
                    unsigned.endsWith("b", ignoreCase = true) -> binary(unsigned.dropLast(1), sign)
                    defaultBase == Prefix.Bin -> binary(unsigned, sign)
                    defaultBase == Prefix.Hex -> {
                        val digits = unsigned.replace("_", "")
                        require(digits.isNotEmpty())
                        Value.Bus(BigInteger(digits, 16) * sign, digits.length * 4)
                    }
                    else -> Value.Number(BigDecimal(token.replace("_", ""), Math))
                }
            } catch (_: NumberFormatException) {
                failAt(start, "invalid number '$token'")
            } catch (_: IllegalArgumentException) {
                failAt(start, "invalid number '$token'")
            }
        }

        private fun binary(digits: String, sign: BigInteger): Value {
            val cleaned = digits.replace("_", "")
            require(cleaned.isNotEmpty() && cleaned.all { it == '0' || it == '1' })
            return Value.Bus(BigInteger(cleaned, 2) * sign, cleaned.length)
        }

        private fun readToken(): String {
            skipSpace()
            val start = index
            while (index < input.length && !input[index].isWhitespace() && input[index] !in "()+-*/%<>=!~&|^") index++
            return input.substring(start, index)
        }

        private fun take(token: String): kotlin.Boolean {
            skipSpace()
            if (!input.startsWith(token, index)) return false
            index += token.length
            return true
        }

        private fun word(token: String): kotlin.Boolean {
            skipSpace()
            if (!input.regionMatches(index, token, 0, token.length, ignoreCase = true)) return false
            val end = index + token.length
            if (end < input.length && (input[end].isLetterOrDigit() || input[end] == '_')) return false
            index = end
            return true
        }

        private fun skipSpace() {
            while (index < input.length && input[index].isWhitespace()) index++
        }

        private fun add(left: Value, right: Value): Value = when {
            left is Value.Bus && right is Value.Bus -> Value.Bus(left.value + right.value, maxOf(left.width, right.width) + 1)
            else -> Value.Number(number(left).add(number(right), Math))
        }

        private fun subtract(left: Value, right: Value): Value = when {
            left is Value.Bus && right is Value.Bus -> Value.Bus(left.value - right.value, maxOf(left.width, right.width) + 1)
            else -> Value.Number(number(left).subtract(number(right), Math))
        }

        private fun multiply(left: Value, right: Value): Value = when {
            left is Value.Bus && right is Value.Bus -> Value.Bus(left.value * right.value, left.width + right.width)
            else -> Value.Number(number(left).multiply(number(right), Math))
        }

        private fun divide(left: Value, right: Value): Value {
            val divisor = number(right)
            if (divisor.compareTo(BigDecimal.ZERO) == 0) fail("division by zero")
            return Value.Number(number(left).divide(divisor, Math))
        }

        private fun remainder(left: Value, right: Value): Value {
            val divisor = number(right)
            if (divisor.compareTo(BigDecimal.ZERO) == 0) fail("division by zero")
            return Value.Number(number(left).remainder(divisor, Math))
        }

        private fun pow(base: Value, exponent: BigInteger): Value {
            if (exponent.signum() < 0 || exponent.bitLength() > 31) fail("exponent must be a non-negative 32-bit integer")
            return Value.Number(number(base).pow(exponent.toInt(), Math))
        }

        private fun shift(value: Value, count: BigInteger, left: kotlin.Boolean): Value {
            if (count.signum() < 0 || count.bitLength() > 31) fail("shift count must be a non-negative 32-bit integer")
            val bus = bus(value)
            val amount = count.toInt()
            return if (left) Value.Bus(bus.shiftLeft(amount), width(value) + amount) else Value.Bus(bus.shiftRight(amount), maxOf(1, width(value) - amount))
        }

        private fun equal(left: Value, right: Value): kotlin.Boolean = when {
            left is Value.Boolean && right is Value.Boolean -> left.value == right.value
            left !is Value.Boolean && right !is Value.Boolean -> number(left).compareTo(number(right)) == 0
            else -> fail("cannot compare a boolean with a number")
        }

        private fun compare(left: Value, right: Value): Int = number(left).compareTo(number(right))

        private fun number(value: Value): BigDecimal = when (value) {
            is Value.Number -> value.value
            is Value.Bus -> value.value.toBigDecimal()
            is Value.Boolean -> fail("expected a number, got a boolean")
        }

        private fun bool(value: Value): kotlin.Boolean = when (value) {
            is Value.Boolean -> value.value
            else -> fail("expected a boolean, got a number")
        }

        private fun integer(value: Value): BigInteger = try {
            number(value).toBigIntegerExact()
        } catch (_: ArithmeticException) {
            fail("expected an integer")
        }

        private fun bus(value: Value): BigInteger = integer(value)

        private fun width(value: Value): Int = (value as? Value.Bus)?.width ?: maxOf(1, integer(value).abs().bitLength())

        private fun mask(width: Int): BigInteger = BigInteger.ONE.shiftLeft(width).subtract(BigInteger.ONE)

        private fun kotlin.Boolean.asValue(): Value = Value.Boolean(this)

        private fun BigInteger.asValue(): Value = Value.Bus(this, maxOf(1, bitLength()))

        private fun fail(message: String): Nothing = throw CalculatorException("$message at column ${index + 1}")

        private fun failAt(position: Int, message: String): Nothing = throw CalculatorException("$message at column ${position + 1}")
    }

    class CalculatorException(message: String) : IllegalArgumentException(message)

    private fun prefix(expression: String): Pair<String, Prefix> {
        val start = expression.indexOfFirst { !it.isWhitespace() }
        if (start < 0) return expression to Prefix.Dec
        val end = expression.indexOfFirst(start) { it.isWhitespace() }.let { if (it < 0) expression.length else it }
        val prefix = Prefix.entries.firstOrNull { it.keyword.equals(expression.substring(start, end), ignoreCase = true) }
            ?: return expression to Prefix.Dec
        return expression.substring(end) to prefix
    }

    private fun String.indexOfFirst(start: Int, predicate: (Char) -> kotlin.Boolean): Int {
        for (index in start until length) if (predicate(this[index])) return index
        return -1
    }

    private fun String.lastIndexBefore(start: Int, predicate: (Char) -> kotlin.Boolean): Int {
        for (index in start downTo 0) if (predicate(this[index])) return index
        return -1
    }

    private fun endsWithValue(text: String): kotlin.Boolean {
        val last = text.lastOrNull { !it.isWhitespace() } ?: return false
        return last == ')' || last !in OperatorCharacters && last != '('
    }

    private enum class Prefix(val keyword: String) {
        Bin("bin"),
        Hex("hex"),
        Dec("dec"),
    }

    private const val OperatorCharacters = "+-*/%<>=!~&|^"
    private val Values = listOf("0b", "0x", "b'", "true", "false", "not", "(")
    private val LogicalOperators = listOf("and", "or", "xor", "&&", "||", "^^")
    private val Operators = listOf(
        "+", "-", "*", "/", "%", "**", "<<", ">>", "&", "|", "^",
        "==", "!=", "<", "<=", ">", ">=", "and", "or", "xor", "&&", "||", "^^", ")",
    )
}
