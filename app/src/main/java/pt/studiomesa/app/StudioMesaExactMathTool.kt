package pt.studiomesa.app

import com.google.ai.edge.litertlm.OpenApiTool
import java.math.BigDecimal
import java.math.RoundingMode
import org.json.JSONArray
import org.json.JSONObject

/**
 * A bounded decimal evaluator for exact arithmetic.
 *
 * It deliberately supports only numbers, parentheses and + - * /. It never
 * evaluates code, invokes reflection, accesses the network or changes app data.
 */
internal object ExactMathExpressionEvaluator {
    data class Evaluation(
        val normalizedExpression: String,
        val result: BigDecimal
    ) {
        fun resultText(): String =
            if (result.compareTo(BigDecimal.ZERO) == 0) "0"
            else result.stripTrailingZeros().toPlainString()
    }

    const val MAX_EXPRESSION_CHARS = 256
    private const val MAX_NUMBER_CHARS = 32
    private const val MAX_DECIMAL_PLACES = 12
    private const val MAX_OPERATIONS = 64
    private val MAX_ABSOLUTE_VALUE = BigDecimal("1000000000000000000")
    private val ZERO = BigDecimal.ZERO

    fun evaluate(expression: String): Evaluation {
        val trimmed = expression.trim()
        require(trimmed.isNotEmpty()) { "Escreve uma expressão matemática." }
        require(trimmed.length <= MAX_EXPRESSION_CHARS) {
            "A expressão é demasiado longa (máximo de $MAX_EXPRESSION_CHARS caracteres)."
        }

        // Portuguese decimal commas are accepted and normalized for parsing.
        val normalized = trimmed.replace(',', '.')
        val result = Parser(normalized).parse()
        return Evaluation(normalized, result)
    }

    private class Parser(private val source: String) {
        private var position = 0
        private var operationCount = 0

        fun parse(): BigDecimal {
            val result = parseExpression()
            skipWhitespace()
            require(position == source.length) {
                "A expressão contém um símbolo ou texto não suportado."
            }
            return checked(result)
        }

        private fun parseExpression(): BigDecimal {
            var value = parseTerm()
            while (true) {
                skipWhitespace()
                value = when {
                    consume('+') -> apply(value.add(parseTerm()))
                    consume('-') -> apply(value.subtract(parseTerm()))
                    else -> return value
                }
            }
        }

        private fun parseTerm(): BigDecimal {
            var value = parseUnary()
            while (true) {
                skipWhitespace()
                value = when {
                    consume('*') -> apply(value.multiply(parseUnary()))
                    consume('/') -> {
                        val divisor = parseUnary()
                        require(divisor.compareTo(ZERO) != 0) {
                            "Não é possível dividir por zero."
                        }
                        apply(value.divide(divisor, MAX_DECIMAL_PLACES, RoundingMode.HALF_UP))
                    }
                    else -> return value
                }
            }
        }

        private fun parseUnary(): BigDecimal {
            skipWhitespace()
            return when {
                consume('+') -> parseUnary()
                consume('-') -> checked(parseUnary().negate())
                else -> parsePrimary()
            }
        }

        private fun parsePrimary(): BigDecimal {
            skipWhitespace()
            if (consume('(')) {
                val nested = parseExpression()
                skipWhitespace()
                require(consume(')')) { "Falta fechar um parêntesis." }
                return nested
            }
            return parseNumber()
        }

        private fun parseNumber(): BigDecimal {
            skipWhitespace()
            val start = position
            var hasDecimalPoint = false
            var digitCount = 0

            while (position < source.length) {
                val character = source[position]
                when {
                    character.isDigit() -> {
                        digitCount++
                        position++
                    }
                    character == '.' && !hasDecimalPoint -> {
                        hasDecimalPoint = true
                        position++
                    }
                    else -> break
                }
                require(position - start <= MAX_NUMBER_CHARS) {
                    "Um dos números ultrapassa o limite permitido."
                }
            }

            require(digitCount > 0) { "Era esperado um número." }
            val token = source.substring(start, position)
            val decimalPlaces = token.substringAfter('.', "").length
            require(decimalPlaces <= MAX_DECIMAL_PLACES) {
                "Usa no máximo $MAX_DECIMAL_PLACES casas decimais."
            }
            return checked(
                runCatching { BigDecimal(token) }.getOrElse {
                    throw IllegalArgumentException("Um dos números não é válido.")
                }
            )
        }

        private fun apply(value: BigDecimal): BigDecimal {
            operationCount++
            require(operationCount <= MAX_OPERATIONS) {
                "A expressão contém demasiadas operações."
            }
            return checked(value)
        }

        private fun checked(value: BigDecimal): BigDecimal {
            val bounded = if (value.scale() > MAX_DECIMAL_PLACES) {
                value.setScale(MAX_DECIMAL_PLACES, RoundingMode.HALF_UP)
            } else {
                value
            }
            require(bounded.abs() <= MAX_ABSOLUTE_VALUE) {
                "O resultado ultrapassa o intervalo suportado."
            }
            return bounded
        }

        private fun skipWhitespace() {
            while (position < source.length && source[position].isWhitespace()) position++
        }

        private fun consume(expected: Char): Boolean {
            if (position >= source.length || source[position] != expected) return false
            position++
            return true
        }
    }
}

/** Read-only exact arithmetic tool available to the local assistant. */
internal class StudioMesaExactMathTool : OpenApiTool {
    override fun getToolDescriptionJsonString(): String {
        val expression = JSONObject()
            .put("type", "string")
            .put(
                "description",
                "Expressão com números decimais, +, -, *, / e parênteses. Usa ponto ou vírgula decimal."
            )
        val parameters = JSONObject()
            .put("type", "object")
            .put("properties", JSONObject().put("expression", expression))
            .put("required", JSONArray().put("expression"))

        return JSONObject()
            .put("name", "calculate_exact_math")
            .put(
                "description",
                "Calcula aritmética exata localmente. Usa sempre para somas, subtrações, multiplicações, divisões e expressões com parênteses; não faças estas contas mentalmente. Aceita apenas números e operadores + - * / e parênteses. A divisão é arredondada a 12 casas decimais. Não executa código e não altera dados."
            )
            .put("parameters", parameters)
            .toString()
    }

    override fun execute(paramsJsonString: String): String = runCatching {
        val params = JSONObject(paramsJsonString)
        val evaluation = ExactMathExpressionEvaluator.evaluate(
            params.optString("expression")
        )
        JSONObject()
            .put("expression", evaluation.normalizedExpression)
            .put("result", evaluation.resultText())
            .put("precision", "até 12 casas decimais; divisão arredondada HALF_UP")
            .put("source", "calculadora decimal local determinística")
            .put("read_only", true)
            .toString()
    }.getOrElse { error ->
        JSONObject()
            .put("error", error.message ?: "Não foi possível calcular esta expressão.")
            .put("read_only", true)
            .toString()
    }
}
