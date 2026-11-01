package ru.rossem.phonebook.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

object Text {
    fun norm(s: String): String = s.lowercase().replace('ё', 'е')

    fun tokens(query: String): List<String> =
        norm(query).split(' ', '\t', ',', ';').map { it.trim() }.filter { it.isNotEmpty() }

    fun digits(s: String): String = s.filter { it in '0'..'9' }

    fun initials(name: String): String {
        val parts = name.replace(Regex("\\(.*?\\)"), " ").split(' ').filter { it.isNotBlank() && it[0].isLetter() }
        return when {
            parts.isEmpty() -> "?"
            parts.size == 1 -> parts[0].take(1).uppercase()
            else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
        }
    }

    fun departmentPath(dep: String): List<String> =
        dep.split('\\').map { it.trim() }.filter { it.isNotEmpty() }

    private val bdFormat = DateTimeFormatter.ofPattern("dd.MM.uuuu").withResolverStyle(ResolverStyle.STRICT)

    fun parseBirthday(s: String): LocalDate? {
        val t = s.trim()
        if (t.isEmpty()) return null
        return try {
            LocalDate.parse(t, bdFormat)
        } catch (_: Exception) {
            // Допускаем формат Д.М.ГГГГ без ведущих нулей
            val p = t.split('.')
            if (p.size == 3) runCatching { LocalDate.of(p[2].toInt(), p[1].toInt(), p[0].toInt()) }.getOrNull() else null
        }
    }

    /** Номер для набора: цифры и ведущий «+». */
    fun dialNumber(s: String): String {
        val t = s.trim()
        val d = digits(t)
        return if (t.startsWith("+")) "+$d" else d
    }

    /** Номер в международном формате (только цифры) для WhatsApp. */
    fun internationalDigits(s: String): String {
        val d = digits(s)
        return if (!s.trim().startsWith("+") && d.length == 11 && d.startsWith("8") && !d.startsWith("880")) "7" + d.drop(1) else d
    }

    private val months = arrayOf(
        "января", "февраля", "марта", "апреля", "мая", "июня",
        "июля", "августа", "сентября", "октября", "ноября", "декабря"
    )

    fun dayMonth(d: LocalDate): String = "${d.dayOfMonth} ${months[d.monthValue - 1]}"

    fun peopleCount(n: Int): String {
        val m10 = n % 10
        val m100 = n % 100
        val w = when {
            m10 == 1 && m100 != 11 -> "человек"
            m10 in 2..4 && m100 !in 12..14 -> "человека"
            else -> "человек"
        }
        return "$n $w"
    }

    fun yearsWord(n: Int): String {
        val m10 = n % 10
        val m100 = n % 100
        return when {
            m10 == 1 && m100 != 11 -> "год"
            m10 in 2..4 && m100 !in 12..14 -> "года"
            else -> "лет"
        }
    }
}
