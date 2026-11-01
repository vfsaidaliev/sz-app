package ru.rossem.phonebook.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

private sealed interface Route {
    data class Card(val id: String) : Route
    data object Settings : Route
}

private data class Tab(val title: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("Сотрудники", Icons.Filled.People),
    Tab("Избранное", Icons.Filled.Star),
    Tab("Отделы", Icons.Filled.AccountTree),
    Tab("Дни рождения", Icons.Filled.Cake),
)

val JSON_MIME_TYPES = arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhonebookApp(vm: PhonebookViewModel = viewModel()) {
    val directory by vm.directory.collectAsStateWithLifecycle()
    val importState by vm.importState.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val stack = remember { mutableStateListOf<Route>() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.import(uri)
    }
    val pickFile = { picker.launch(JSON_MIME_TYPES) }

    LaunchedEffect(importState) {
        when (val s = importState) {
            is ImportState.Done -> {
                snackbar.showSnackbar("Загружено сотрудников: ${s.count}")
                vm.clearImportState()
            }
            is ImportState.Failed -> {
                snackbar.showSnackbar(s.message)
                vm.clearImportState()
            }
            else -> Unit
        }
    }

    BackHandler(enabled = stack.isNotEmpty()) { stack.removeAt(stack.lastIndex) }
    BackHandler(enabled = stack.isEmpty() && tab != 0) { tab = 0 }

    val dir = directory
    when {
        dir == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

        dir.all.isEmpty() -> Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { pad ->
            Welcome(running = importState == ImportState.Running, onPick = pickFile, modifier = Modifier.padding(pad))
        }

        else -> {
            val top = stack.lastOrNull()
            when (top) {
                is Route.Card -> {
                    val emp = dir.byId[top.id]
                    if (emp == null) {
                        LaunchedEffect(top) { stack.removeAt(stack.lastIndex) }
                    } else {
                        EmployeeCard(
                            employee = emp,
                            directory = dir,
                            favorite = emp.id in favorites,
                            onToggleFavorite = { vm.toggleFavorite(emp.id) },
                            onBack = { stack.removeAt(stack.lastIndex) },
                            onOpen = { stack.add(Route.Card(it)) },
                        )
                    }
                }

                Route.Settings -> SettingsScreen(
                    vm = vm,
                    count = dir.all.size,
                    running = importState == ImportState.Running,
                    snackbar = snackbar,
                    onPick = pickFile,
                    onBack = { stack.removeAt(stack.lastIndex) },
                )

                null -> Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(if (tab == 0) "Справочник РосСЭМ" else tabs[tab].title) },
                            actions = {
                                IconButton(onClick = { stack.add(Route.Settings) }) {
                                    Icon(Icons.Filled.Settings, "Настройки")
                                }
                            },
                        )
                    },
                    bottomBar = {
                        NavigationBar {
                            tabs.forEachIndexed { i, t ->
                                NavigationBarItem(
                                    selected = tab == i,
                                    onClick = { tab = i },
                                    icon = { Icon(t.icon, null) },
                                    label = { Text(t.title, maxLines = 1) },
                                )
                            }
                        }
                    },
                    snackbarHost = { SnackbarHost(snackbar) },
                ) { pad ->
                    val open: (String) -> Unit = { stack.add(Route.Card(it)) }
                    Box(Modifier.padding(pad)) {
                        when (tab) {
                            0 -> EmployeesScreen(vm, dir, favorites, open)
                            1 -> FavoritesScreen(vm, dir, favorites, open)
                            2 -> DepartmentsScreen(dir) { org, path ->
                                vm.showDepartment(org, path)
                                tab = 0
                            }
                            else -> BirthdaysScreen(vm, dir, favorites, open)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Welcome(running: Boolean, onPick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.Contacts, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("Справочник РосСЭМ", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text(
            "Справочник пока пуст. Выберите файл employees.json в памяти телефона — данные будут сохранены только на этом устройстве.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        if (running) {
            CircularProgressIndicator()
            Spacer(Modifier.height(8.dp))
            Text("Загрузка…")
        } else {
            Button(onClick = onPick) {
                Icon(Icons.Filled.FileOpen, null)
                Spacer(Modifier.size(8.dp))
                Text("Выбрать файл employees.json")
            }
        }
    }
}
