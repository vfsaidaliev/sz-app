package ru.rossem.phonebook.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.rossem.phonebook.data.AppDb
import ru.rossem.phonebook.data.Employee
import ru.rossem.phonebook.data.EmployeeEntity
import ru.rossem.phonebook.data.FavoriteEntity
import ru.rossem.phonebook.data.Importer
import ru.rossem.phonebook.data.Text
import java.text.Collator
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Узел дерева отделов. path — полный путь «Отдел\Подотдел», пустой для узла организации. */
class DeptNode(
    val organization: String,
    val title: String,
    val path: String,
    val level: Int,
    var count: Int = 0,
    val children: MutableList<DeptNode> = mutableListOf(),
)

class Directory(
    val all: List<Employee>,
    val byId: Map<String, Employee>,
    private val byIdLower: Map<String, Employee>,
    private val byName: Map<String, Employee>,
    val organizations: List<String>,
    val tree: List<DeptNode>,
) {
    /** Возвращает найденного руководителя или текст для показа, если в справочнике его нет. */
    fun manager(e: Employee): Pair<Employee?, String?> {
        val m = e.e.managerId.trim()
        if (m.isEmpty()) return null to null
        byId[m]?.let { return it to null }
        byIdLower[m.lowercase()]?.let { return it to null }
        val cn = Regex("^CN=([^,]+)", RegexOption.IGNORE_CASE).find(m)?.groupValues?.get(1)?.trim()
        if (cn != null) {
            byName[Text.norm(cn)]?.let { return it to null }
            return null to cn
        }
        return null to m
    }

    fun departmentsOf(org: String?): List<String> {
        val nodes = if (org == null) tree else tree.filter { it.organization == org }
        val res = sortedSetOf<String>(collator)
        fun walk(n: DeptNode) {
            if (n.path.isNotEmpty()) res.add(n.path)
            n.children.forEach(::walk)
        }
        nodes.forEach(::walk)
        return res.toList()
    }

    companion object {
        val collator: Collator = Collator.getInstance(Locale("ru", "RU")).apply { strength = Collator.SECONDARY }
        val EMPTY = Directory(emptyList(), emptyMap(), emptyMap(), emptyMap(), emptyList(), emptyList())

        fun build(entities: List<EmployeeEntity>): Directory {
            val list = entities.map { Employee(it) }.sortedWith { a, b -> collator.compare(a.name, b.name) }
            val byId = list.associateBy { it.id }
            val byIdLower = list.associateBy { it.id.lowercase() }
            val byName = HashMap<String, Employee>()
            list.forEach { byName.putIfAbsent(Text.norm(it.name.trim()), it) }
            val orgs = list.map { it.e.organization.ifBlank { NO_ORG } }.distinct().sortedWith(collator)

            val roots = LinkedHashMap<String, DeptNode>()
            for (org in orgs) roots[org] = DeptNode(org, org, "", 0)
            for (e in list) {
                val org = e.e.organization.ifBlank { NO_ORG }
                var node = roots.getValue(org)
                node.count++
                val parts = e.departmentPath
                for (i in parts.indices) {
                    val path = parts.subList(0, i + 1).joinToString("\\")
                    val child = node.children.firstOrNull { it.path == path }
                        ?: DeptNode(org, parts[i], path, i + 1).also { node.children.add(it) }
                    child.count++
                    node = child
                }
            }
            fun sort(n: DeptNode) {
                n.children.sortWith { a, b -> collator.compare(a.title, b.title) }
                n.children.forEach(::sort)
            }
            roots.values.forEach(::sort)
            return Directory(list, byId, byIdLower, byName, orgs, roots.values.toList())
        }
    }
}

const val NO_ORG = "Без организации"

data class Filters(
    val query: String = "",
    val organization: String? = null,
    val department: String? = null,
    val mobileOnly: Boolean = false,
)

data class Birthday(val employee: Employee, val date: LocalDate, val daysLeft: Int, val age: Int?)

sealed interface ImportState {
    data object Idle : ImportState
    data object Running : ImportState
    data class Done(val count: Int) : ImportState
    data class Failed(val message: String) : ImportState
}

class PhonebookViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDb.get(app).dao()
    private val prefs = app.getSharedPreferences("settings", 0)

    private val _directory = MutableStateFlow<Directory?>(null) // null — ещё загружается
    val directory: StateFlow<Directory?> = _directory.asStateFlow()

    private val _filters = MutableStateFlow(Filters())
    val filters: StateFlow<Filters> = _filters.asStateFlow()

    private val _import = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _import.asStateFlow()

    private val _importedAt = MutableStateFlow(prefs.getLong("importedAt", 0L))
    val importedAt: StateFlow<Long> = _importedAt.asStateFlow()

    val favorites: StateFlow<Set<String>> = dao.favorites().map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val filtered: StateFlow<List<Employee>> = combine(_directory, _filters) { dir, f ->
        val d = dir ?: return@combine emptyList()
        val tokens = Text.tokens(f.query)
        val deptPrefix = f.department?.let { it + "\\" }
        d.all.filter { e ->
            (f.organization == null || e.e.organization.ifBlank { NO_ORG } == f.organization) &&
                (f.department == null || e.departmentPath.joinToString("\\").let { it == f.department || it.startsWith(deptPrefix!!) }) &&
                (!f.mobileOnly || e.hasMobile) &&
                (tokens.isEmpty() || e.matches(tokens))
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch {
            val entities = withContext(Dispatchers.IO) { dao.all() }
            _directory.value = withContext(Dispatchers.Default) { Directory.build(entities) }
        }
    }

    fun setQuery(q: String) = _filters.update { it.copy(query = q) }
    fun setOrganization(o: String?) = _filters.update {
        it.copy(organization = o, department = if (o != it.organization) null else it.department)
    }
    fun setDepartment(d: String?) = _filters.update { it.copy(department = d) }
    fun setMobileOnly(v: Boolean) = _filters.update { it.copy(mobileOnly = v) }
    fun resetFilters() = _filters.update { Filters(query = it.query) }
    fun showDepartment(org: String, path: String) {
        _filters.value = Filters(organization = org, department = path.ifEmpty { null })
    }

    private inline fun MutableStateFlow<Filters>.update(f: (Filters) -> Filters) { value = f(value) }

    fun toggleFavorite(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (id in favorites.value) dao.removeFavorite(id) else dao.addFavorite(FavoriteEntity(id))
        }
    }

    fun import(uri: Uri) {
        if (_import.value == ImportState.Running) return
        _import.value = ImportState.Running
        viewModelScope.launch {
            try {
                val entities = withContext(Dispatchers.IO) {
                    val stream = getApplication<Application>().contentResolver.openInputStream(uri)
                        ?: throw Importer.ImportException("Не удалось открыть файл")
                    val parsed = stream.use { Importer.parse(it) }
                    dao.replaceAll(parsed)
                    parsed
                }
                _directory.value = withContext(Dispatchers.Default) { Directory.build(entities) }
                _filters.value = Filters()
                val now = System.currentTimeMillis()
                prefs.edit().putLong("importedAt", now).apply()
                _importedAt.value = now
                _import.value = ImportState.Done(entities.size)
            } catch (e: Importer.ImportException) {
                _import.value = ImportState.Failed(e.message ?: "Ошибка загрузки")
            } catch (e: OutOfMemoryError) {
                _import.value = ImportState.Failed("Файл слишком большой")
            } catch (e: Exception) {
                _import.value = ImportState.Failed("Не удалось загрузить файл: ${e.javaClass.simpleName}")
            }
        }
    }

    fun clearImportState() { _import.value = ImportState.Idle }

    fun birthdays(dir: Directory, today: LocalDate = LocalDate.now()): List<Birthday> =
        dir.all.mapNotNull { e ->
            val b = e.birthDate ?: return@mapNotNull null
            var next = sameDay(b, today.year)
            if (next.isBefore(today)) next = sameDay(b, today.year + 1)
            val days = ChronoUnit.DAYS.between(today, next).toInt()
            if (days > 7) return@mapNotNull null
            val age = (next.year - b.year).takeIf { b.year > 1900 && it in 1..120 }
            Birthday(e, next, days, age)
        }.sortedWith(compareBy<Birthday> { it.daysLeft }.thenComparator { a, b -> Directory.collator.compare(a.employee.name, b.employee.name) })

    private fun sameDay(b: LocalDate, year: Int): LocalDate =
        if (b.monthValue == 2 && b.dayOfMonth == 29 && !java.time.Year.isLeap(year.toLong())) LocalDate.of(year, 2, 28)
        else LocalDate.of(year, b.monthValue, b.dayOfMonth)
}
