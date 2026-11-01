package ru.rossem.phonebook.data

import java.time.LocalDate

/** Сотрудник в памяти с заранее подготовленными полями для поиска и сортировки. */
class Employee(val e: EmployeeEntity) {
    val id get() = e.id
    val name get() = e.name
    val initials: String = Text.initials(e.name)
    val letter: Char = e.name.trim().firstOrNull()?.uppercaseChar()?.let { if (it == 'Ё') 'Е' else it } ?: '#'
    val departmentPath: List<String> = Text.departmentPath(e.department)
    val hasMobile: Boolean = Text.digits(e.mobile).length >= 5
    val birthDate: LocalDate? = Text.parseBirthday(e.birthday)

    /** Нормализованный текст для поиска. */
    val haystack: String = Text.norm(
        listOf(e.name, e.role, e.department.replace('\\', ' '), e.organization, e.office, e.ext, e.mobile, e.email)
            .joinToString(" ")
    )
    /** Только цифры всех номеров — для поиска по номеру в любом формате. */
    val digitsHay: String = Text.digits(e.ext) + " " + Text.digits(e.mobile)

    fun matches(tokens: List<String>): Boolean = tokens.all { t ->
        if (haystack.contains(t)) return@all true
        val d = Text.digits(t)
        d.isNotEmpty() && t.none { it.isLetter() } && digitsHay.contains(d)
    }
}
