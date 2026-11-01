package ru.rossem.phonebook.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream

object Importer {
    class ImportException(message: String) : Exception(message)

    fun parse(input: InputStream): List<EmployeeEntity> {
        val text = input.bufferedReader(Charsets.UTF_8).use { it.readText() }.trimStart('﻿', ' ', '\n', '\r', '\t')
        val arr: JSONArray = try {
            if (text.startsWith("{")) {
                // На случай выгрузки вида {"employees":[...]} или {"items":[...]}
                val o = JSONObject(text)
                val key = o.keys().asSequence().firstOrNull { o.optJSONArray(it) != null }
                    ?: throw ImportException("В файле не найден список сотрудников")
                o.getJSONArray(key)
            } else JSONArray(text)
        } catch (e: ImportException) {
            throw e
        } catch (e: Exception) {
            throw ImportException("Файл не похож на employees.json (ошибка чтения JSON)")
        }

        val result = LinkedHashMap<String, EmployeeEntity>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val name = o.str("name")
            if (name.isBlank()) continue
            var id = o.str("id").ifBlank { "row_$i" }
            if (result.containsKey(id)) id = "${id}_$i"
            result[id] = EmployeeEntity(
                id = id,
                name = name.trim(),
                role = o.str("role").trim(),
                department = o.str("department").trim(),
                organization = o.str("organization").trim(),
                office = o.str("office").trim(),
                ext = o.str("ext").trim(),
                mobile = o.str("mobile").trim(),
                email = o.str("email").trim(),
                birthday = o.str("birthday").trim(),
                isLead = o.bool("isLead"),
                managerId = o.str("managerId").trim(),
            )
        }
        if (result.isEmpty()) throw ImportException("В файле не найдено ни одного сотрудника")
        return result.values.toList()
    }

    private fun JSONObject.str(key: String): String {
        if (!has(key) || isNull(key)) return ""
        return when (val v = opt(key)) {
            null -> ""
            is String -> v
            is Number, is Boolean -> v.toString()
            else -> ""
        }
    }

    private fun JSONObject.bool(key: String): Boolean {
        if (!has(key) || isNull(key)) return false
        return when (val v = opt(key)) {
            is Boolean -> v
            is Number -> v.toInt() != 0
            is String -> v.equals("true", true) || v == "1" || v.equals("да", true)
            else -> false
        }
    }
}
