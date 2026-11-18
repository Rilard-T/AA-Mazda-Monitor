package com.aadash.obd

import com.ezylang.evalex.Expression
import com.ezylang.evalex.config.ExpressionConfiguration
import com.ezylang.evalex.data.EvaluationValue
import com.ezylang.evalex.functions.FunctionIfc
import com.ezylang.evalex.functions.FunctionParameterDefinition
import com.ezylang.evalex.parser.Token
import java.util.AbstractMap.SimpleEntry

/**
 * Вычисление пользовательских PID по формулам Torque Pro.
 *
 * Переменные A..P — байты данных после эха «режим+PID»: для ответа блока «62 04 34 00 05»
 * на запрос «220434» это A = 0x00, B = 0x05. Формула пишется так же, как в Torque:
 * `A*256+B`, `(A*256+B)*0.01`.
 *
 * Отличие от Torque-совместимых вычислителей, которые встречаются в чужих проектах:
 * `SIGNED(x)` здесь **16-битная** (как в нашей [MazdaDecode.s16]), иначе отрицательные
 * значения вроде температуры АКПП превращались бы в 250+.
 */
object UserPidFormula {

    /** Байты ответа раскладываются по A..P: шестнадцати переменных хватает любому PID. */
    private const val MAX_BYTES = 16

    private val config: ExpressionConfiguration by lazy {
        ExpressionConfiguration.defaultConfiguration().withAdditionalFunctions(
            SimpleEntry<String, FunctionIfc>("SIGNED", SignedFunction()),
            SimpleEntry<String, FunctionIfc>("BIT", BitFunction()),
            SimpleEntry<String, FunctionIfc>("INT", IntFunction()),
            SimpleEntry<String, FunctionIfc>("SHL", ShlFunction()),
        )
    }

    /** Значение формулы для байтов ответа; null — формула не сошлась или данных не хватает. */
    fun evaluate(equation: String, bytes: IntArray): Double? = try {
        val e = Expression(prepare(equation), config)
        // Недостающие байты Torque считает нулями: формула не должна пропадать из-за
        // короткого ответа блока.
        for (i in 0 until MAX_BYTES) e.with(('A' + i).toString(), if (i < bytes.size) bytes[i] else 0)
        val v = e.evaluate().numberValue?.toDouble()
        if (v == null || v.isNaN() || v.isInfinite()) null else v
    } catch (t: Throwable) {
        null
    }

    /** Проверка синтаксиса без данных: null — формула корректна, иначе текст ошибки. */
    fun validate(equation: String): String? = try {
        Expression(prepare(equation), config).validate()
        null
    } catch (t: Throwable) {
        t.message ?: "?"
    }

    private fun prepare(equation: String): String =
        equation.trim()
            .replace("[x×]".toRegex(), "*")
            // Torque-синтаксис бита: {A:7} → BIT(A,7)
            .replace(Regex("\\{([A-Pa-p]):(\\d+)\\}")) { "BIT(${it.groupValues[1].uppercase()},${it.groupValues[2]})" }
            // Torque-константа в квадратных скобках: [33] → 33
            .replace(Regex("\\[(\\d+)\\]")) { it.groupValues[1] }
            // Torque-сдвиг влево: A<16 → SHL(A,16)
            .replace(Regex("([A-Pa-p])<(\\d+)")) { "SHL(${it.groupValues[1].uppercase()},${it.groupValues[2]})" }

    /**
     * База для небольших встроенных функций: EvalEx 3.0.5 берёт описание параметров
     * из аннотаций класса, поэтому здесь оно задаётся напрямую.
     */
    private abstract class SimpleFunction(
        private val definitions: List<FunctionParameterDefinition>,
    ) : FunctionIfc {
        override fun getFunctionParameterDefinitions(): List<FunctionParameterDefinition> = definitions

        override fun hasVarArgs(): Boolean = false

        override fun validatePreEvaluation(functionToken: Token, vararg parameters: EvaluationValue) {
            require(parameters.size == definitions.size) {
                "Ожидалось параметров: ${definitions.size}, получено: ${parameters.size}"
            }
        }
    }

    /** SIGNED(x) — знаковое 16-битное значение (так же считает сама Mazda). */
    private class SignedFunction : SimpleFunction(listOf(def("x"))) {
        override fun evaluate(
            expression: Expression,
            functionToken: Token,
            vararg parameters: EvaluationValue,
        ): EvaluationValue {
            val raw = parameters[0].numberValue.toInt() and 0xFFFF
            val signed = if (raw > 0x7FFF) raw - 0x10000 else raw
            return EvaluationValue(signed.toDouble(), expression.configuration.mathContext)
        }
    }

    /** BIT(x, n) — бит n значения x, как в Torque. */
    private class BitFunction : SimpleFunction(listOf(def("x"), def("n"))) {
        override fun evaluate(
            expression: Expression,
            functionToken: Token,
            vararg parameters: EvaluationValue,
        ): EvaluationValue {
            val value = parameters[0].numberValue.toInt()
            val bit = parameters[1].numberValue.toInt()
            return EvaluationValue(((value shr bit) and 1).toDouble(), expression.configuration.mathContext)
        }
    }

    /** INT(x) — отбрасывание дробной части (в EvalEx есть только FLOOR/CEILING). */
    private class IntFunction : SimpleFunction(listOf(def("x"))) {
        override fun evaluate(
            expression: Expression,
            functionToken: Token,
            vararg parameters: EvaluationValue,
        ): EvaluationValue =
            EvaluationValue(parameters[0].numberValue.toInt().toDouble(), expression.configuration.mathContext)
    }

    /** SHL(x,n) — сдвиг влево на n бит (Torque пишет X<n). */
    private class ShlFunction : SimpleFunction(listOf(def("x"), def("n"))) {
        override fun evaluate(
            expression: Expression,
            functionToken: Token,
            vararg parameters: EvaluationValue,
        ): EvaluationValue {
            val x = parameters[0].numberValue.toDouble()
            val n = parameters[1].numberValue.toInt()
            return EvaluationValue(x * (1 shl n).toDouble(), expression.configuration.mathContext)
        }
    }

}

/** Описание одного параметра встроенной функции. */
private fun def(name: String): FunctionParameterDefinition =
    FunctionParameterDefinition.builder().name(name).build()

/**
 * Разбор файлов Torque Pro: `Name,ShortName,ModeAndPID,Equation,Min,Max,Units,Header`.
 * Разделитель — запятая или точка с запятой (какой в строке чаще), кавычки как в CSV.
 */
object UserPidCsv {

    fun parse(text: String): List<UserPid> {
        val out = ArrayList<UserPid>()
        for (raw in text.replace("\r\n", "\n").replace('\r', '\n').split('\n')) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val cols = splitRow(line)
            // markdown-таблица: убираем пустые крайние колонки от ведущих/замыкающих '|'
            val trimmedCols = if (cols.size > 2 && cols.first().trim().isEmpty() && cols.last().trim().isEmpty()) cols.drop(1).dropLast(1) else cols
            if (trimmedCols.size < 4) continue
            // строка-разделитель markdown: | --- | --- |
            if (trimmedCols.all { it.trim().matches(Regex("[-: ]+")) || it.trim().isEmpty() }) continue
            val first = trimmedCols[0].trim().lowercase()
            if (first == "name" || first == "название" || first == "shortname" || first.startsWith("name") || first.startsWith("short")) continue     // строка заголовка
            // дальше работаем с trimmedCols
            val name = trimmedCols[0].trim()
            val pid = UserPidCodec.normalizePid(trimmedCols.getOrNull(2).orEmpty()) ?: continue
            val equation = trimmedCols.getOrNull(3)?.trim().orEmpty()
            if (name.isEmpty() || equation.isEmpty()) continue
            if (UserPidFormula.validate(equation) != null) continue
            val short = trimmedCols.getOrNull(1)?.trim().orEmpty().ifEmpty { name.take(12) }
            val min = trimmedCols.getOrNull(4)?.trim()?.replace(',', '.')?.toDoubleOrNull() ?: 0.0
            val max = trimmedCols.getOrNull(5)?.trim()?.replace(',', '.')?.toDoubleOrNull() ?: 100.0
            val unit = trimmedCols.getOrNull(6)?.trim().orEmpty()
            val header = UserPidCodec.normalizeHeader(trimmedCols.getOrNull(7).orEmpty())
            out.add(
                UserPid(
                    name = name,
                    shortName = short,
                    modeAndPid = pid,
                    equation = equation,
                    min = min,
                    max = if (max > min) max else min + 100.0,
                    unit = unit,
                    header = header,
                )
            )
        }
        return out
    }

    /** Раскладывает строку на поля с учётом кавычек и выбранного разделителя. */
    internal fun splitRow(line: String): List<String> {
        val pipeCount = line.count { it == '|' }
        val semiCount = line.count { it == ';' }
        val commaCount = line.count { it == ',' }
        val delimiter = when {
            pipeCount > semiCount && pipeCount > commaCount -> '|'
            semiCount > commaCount -> ';'
            else -> ','
        }
        val fields = ArrayList<String>()
        val current = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    if (quoted && i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++
                    } else quoted = !quoted
                }
                c == delimiter && !quoted -> {
                    fields.add(current.toString())
                    current.setLength(0)
                }
                else -> current.append(c)
            }
            i++
        }
        fields.add(current.toString())
        return fields
    }
}
