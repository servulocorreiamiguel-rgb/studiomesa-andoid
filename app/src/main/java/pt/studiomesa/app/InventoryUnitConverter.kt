package pt.studiomesa.app

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.Normalizer
import java.util.Locale

/**
 * Converts only explicitly compatible stock units.
 * Values must fit the database's three-decimal precision exactly; silently
 * rounding a small gram/ml value into a larger stock unit would distort costs.
 */
internal object InventoryUnitConverter {
    private data class UnitSpec(
        val family: String,
        val factorToBase: BigDecimal
    )

    private val thousand = BigDecimal("1000")
    private val maxValue = BigDecimal("999999999")

    fun compatibleUnits(stockUnit: String): List<String> {
        val stock = stockUnit.trim()
        require(stock.isNotBlank()) { "A unidade de stock não está definida." }
        val spec = resolve(stock)
        val alternative = when (spec.family) {
            "mass" -> if (spec.factorToBase == thousand) "g" else "kg"
            "volume" -> if (spec.factorToBase == thousand) "ml" else "L"
            else -> null
        }
        return listOfNotNull(stock, alternative).distinctBy(::normalize)
    }

    fun convertToStockUnit(
        amount: BigDecimal,
        enteredUnit: String,
        stockUnit: String
    ): BigDecimal {
        require(amount.signum() > 0 && amount <= maxValue) {
            "A quantidade tem de ser positiva e estar dentro do limite suportado."
        }
        val from = resolve(enteredUnit)
        val to = resolve(stockUnit)
        require(from.family == to.family) {
            "Não é possível converter entre estas unidades. Usa a mesma categoria (massa, volume ou unidades)."
        }

        val exact = amount.multiply(from.factorToBase).divide(to.factorToBase)
        val converted = try {
            exact.setScale(3, RoundingMode.UNNECESSARY)
        } catch (_: ArithmeticException) {
            throw IllegalArgumentException(
                "Esta conversão exige mais de três casas decimais na unidade de stock. Ajusta a quantidade para não perder precisão."
            )
        }
        require(converted.signum() > 0 && converted <= maxValue) {
            "A quantidade convertida excede o limite suportado."
        }
        return converted
    }

    private fun resolve(rawUnit: String): UnitSpec {
        val normalized = normalize(rawUnit)
        require(normalized.isNotBlank()) { "Indica uma unidade válida." }
        return when (normalized) {
            "kg", "quilo", "quilos", "quilograma", "quilogramas" ->
                UnitSpec("mass", thousand)
            "g", "gr", "grama", "gramas" ->
                UnitSpec("mass", BigDecimal.ONE)
            "l", "lt", "litro", "litros" ->
                UnitSpec("volume", thousand)
            "ml", "mililitro", "mililitros" ->
                UnitSpec("volume", BigDecimal.ONE)
            "un", "und", "unid", "unidade", "unidades", "unit", "units", "pc", "pcs" ->
                UnitSpec("count", BigDecimal.ONE)
            else -> UnitSpec("custom:$normalized", BigDecimal.ONE)
        }
    }

    private fun normalize(value: String): String =
        Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase(Locale.ROOT)
}
