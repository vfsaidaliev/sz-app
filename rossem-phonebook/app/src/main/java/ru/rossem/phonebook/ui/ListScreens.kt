package ru.rossem.phonebook.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import ru.rossem.phonebook.data.Employee
import ru.rossem.phonebook.data.Text as T

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(Icons.Filled.Search, null) },
        trailingIcon = {
            if (value.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Filled.Clear, "Очистить") }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    )
}

@Composable
fun EmployeeList(
    list: List<Employee>,
    favorites: Set<String>,
    onOpen: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    resetKey: Any?,
    modifier: Modifier = Modifier,
) {
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(resetKey) { state.scrollToItem(0) }
    val letterIndex = remember(list) {
        val m = LinkedHashMap<Char, Int>()
        list.forEachIndexed { i, e -> m.putIfAbsent(e.letter, i) }
        m
    }
    Box(modifier.fillMaxSize()) {
        LazyColumn(state = state, modifier = Modifier.fillMaxSize().padding(end = 20.dp)) {
            items(list, key = { it.id }) { e ->
                EmployeeRow(
                    employee = e,
                    favorite = e.id in favorites,
                    onClick = { onOpen(e.id) },
                    onToggleFavorite = { onToggleFavorite(e.id) },
                )
            }
        }
        LetterStrip(
            letters = letterIndex.keys.toList(),
            onLetter = { c -> letterIndex[c]?.let { scope.launch { state.scrollToItem(it) } } },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
fun EmployeesScreen(vm: PhonebookViewModel, dir: Directory, favorites: Set<String>, onOpen: (String) -> Unit) {
    val filters by vm.filters.collectAsStateWithLifecycle()
    val list by vm.filtered.collectAsStateWithLifecycle()
    var orgMenu by remember { mutableStateOf(false) }
    var deptDialog by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        SearchField(
            value = filters.query,
            onChange = vm::setQuery,
            placeholder = "ФИО, должность, отдел, номер…",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        ) {
            Box {
                FilterChip(
                    selected = filters.organization != null,
                    onClick = { orgMenu = true },
                    label = { Text(filters.organization ?: "Организация", maxLines = 1) },
                    trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null, Modifier.width(18.dp)) },
                )
                DropdownMenu(expanded = orgMenu, onDismissRequest = { orgMenu = false }) {
                    DropdownMenuItem(text = { Text("Все организации") }, onClick = { vm.setOrganization(null); orgMenu = false })
                    dir.organizations.forEach { o ->
                        DropdownMenuItem(
                            text = { Text(o) },
                            onClick = { vm.setOrganization(o); orgMenu = false },
                            trailingIcon = { if (o == filters.organization) Icon(Icons.Filled.Check, null) },
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = filters.department != null,
                onClick = { deptDialog = true },
                label = {
                    Text(
                        filters.department?.replace("\\", " / ") ?: "Отдел",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 220.dp),
                    )
                },
                trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null, Modifier.width(18.dp)) },
            )
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = filters.mobileOnly,
                onClick = { vm.setMobileOnly(!filters.mobileOnly) },
                label = { Text("Только с мобильным") },
                leadingIcon = if (filters.mobileOnly) {
                    { Icon(Icons.Filled.Check, null, Modifier.width(FilterChipDefaults.IconSize)) }
                } else null,
            )
            if (filters.organization != null || filters.department != null || filters.mobileOnly) {
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = vm::resetFilters) { Text("Сбросить") }
            }
        }
        Text(
            "Найдено: ${list.size}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
        )
        HorizontalDivider()
        if (list.isEmpty()) EmptyMessage("Никого не найдено")
        else EmployeeList(list, favorites, onOpen, vm::toggleFavorite, resetKey = filters)
    }

    if (deptDialog) {
        DepartmentPicker(
            departments = remember(dir, filters.organization) { dir.departmentsOf(filters.organization) },
            selected = filters.department,
            onSelect = { vm.setDepartment(it); deptDialog = false },
            onDismiss = { deptDialog = false },
        )
    }
}

@Composable
private fun DepartmentPicker(
    departments: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var q by remember { mutableStateOf("") }
    val tokens = T.tokens(q)
    val shown = remember(departments, q) {
        if (tokens.isEmpty()) departments else departments.filter { d -> val n = T.norm(d); tokens.all { n.contains(it) } }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Отдел") },
        text = {
            Column {
                SearchField(q, { q = it }, "Поиск отдела")
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    item {
                        Text(
                            "Все отделы",
                            Modifier.fillMaxWidth().clickable { onSelect(null) }.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    items(shown) { d ->
                        val depth = if (tokens.isEmpty()) d.count { it == '\\' } else 0
                        Text(
                            if (tokens.isEmpty()) d.substringAfterLast('\\') else d.replace("\\", " / "),
                            Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(d) }
                                .padding(start = (depth * 16).dp, top = 10.dp, bottom = 10.dp),
                            style = if (d == selected) MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.primary)
                            else MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
}

@Composable
fun FavoritesScreen(vm: PhonebookViewModel, dir: Directory, favorites: Set<String>, onOpen: (String) -> Unit) {
    val list = remember(dir, favorites) { dir.all.filter { it.id in favorites } }
    if (list.isEmpty()) {
        EmptyMessage("Избранных пока нет. Нажмите на звёздочку рядом с сотрудником, чтобы добавить его сюда.")
    } else {
        EmployeeList(list, favorites, onOpen, vm::toggleFavorite, resetKey = null)
    }
}
