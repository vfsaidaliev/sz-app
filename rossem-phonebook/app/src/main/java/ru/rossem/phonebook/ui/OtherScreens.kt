package ru.rossem.phonebook.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.rossem.phonebook.data.Text as T
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------- Отделы ----------

@Composable
fun DepartmentsScreen(dir: Directory, onShow: (org: String, path: String) -> Unit) {
    var expanded by remember { mutableStateOf(setOf<String>()) }
    val visible = remember(dir, expanded) {
        val out = ArrayList<DeptNode>()
        fun walk(n: DeptNode) {
            out.add(n)
            if (key(n) in expanded) n.children.forEach(::walk)
        }
        dir.tree.forEach(::walk)
        out
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(visible, key = { key(it) }) { n ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onShow(n.organization, n.path) }
                    .padding(start = (8 + n.level * 20).dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (n.children.isNotEmpty()) {
                    val k = key(n)
                    IconButton(onClick = { expanded = if (k in expanded) expanded - k else expanded + k }) {
                        Icon(if (k in expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, "Развернуть")
                    }
                } else {
                    Spacer(Modifier.width(48.dp))
                }
                Text(
                    n.title,
                    Modifier.weight(1f).padding(vertical = 12.dp),
                    style = if (n.level == 0) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                    fontWeight = if (n.level == 0) FontWeight.SemiBold else FontWeight.Normal,
                )
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = 10.dp, vertical = 2.dp),
                ) {
                    Text(
                        n.count.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            if (n.level == 0) HorizontalDivider()
        }
    }
}

private fun key(n: DeptNode) = n.organization + "|" + n.path

// ---------- Дни рождения ----------

@Composable
fun BirthdaysScreen(vm: PhonebookViewModel, dir: Directory, favorites: Set<String>, onOpen: (String) -> Unit) {
    val list = remember(dir) { vm.birthdays(dir) }
    if (list.isEmpty()) {
        EmptyMessage("Сегодня и в ближайшие 7 дней дней рождения нет")
        return
    }
    val groups = list.groupBy { it.daysLeft }
    LazyColumn(Modifier.fillMaxSize()) {
        groups.forEach { (days, items) ->
            item(key = "h$days") {
                val date = items.first().date
                val title = when (days) {
                    0 -> "Сегодня, ${T.dayMonth(date)}"
                    1 -> "Завтра, ${T.dayMonth(date)}"
                    else -> T.dayMonth(date)
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                )
            }
            items(items, key = { "b" + it.employee.id }) { b ->
                val sub = listOfNotNull(
                    b.age?.let { if (days == 0) "исполнилось $it ${T.yearsWord(it)}" else "исполнится $it ${T.yearsWord(it)}" },
                    b.employee.e.role.takeIf { it.isNotBlank() },
                    b.employee.e.organization.takeIf { it.isNotBlank() },
                ).joinToString(" · ")
                EmployeeRow(
                    employee = b.employee,
                    favorite = b.employee.id in favorites,
                    onClick = { onOpen(b.employee.id) },
                    onToggleFavorite = { vm.toggleFavorite(b.employee.id) },
                    subtitle = sub,
                )
            }
        }
    }
}

// ---------- Настройки ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: PhonebookViewModel,
    count: Int,
    running: Boolean,
    snackbar: SnackbarHostState,
    onPick: () -> Unit,
    onBack: () -> Unit,
) {
    val importedAt by vm.importedAt.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val version = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: ""
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Справочник", style = MaterialTheme.typography.titleMedium)
            Text("В справочнике: ${T.peopleCount(count)}")
            if (importedAt > 0) {
                val f = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("ru"))
                Text("Загружен: ${f.format(Date(importedAt))}")
            }
            if (running) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Загрузка…")
                }
            } else {
                Button(onClick = onPick, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.FileOpen, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Обновить справочник")
                }
            }
            Text(
                "Выберите новый файл employees.json. Текущие данные будут заменены, избранное сохранится.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider()
            Text(
                "Данные хранятся только на этом телефоне. Приложение не использует интернет.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text("Версия $version", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
