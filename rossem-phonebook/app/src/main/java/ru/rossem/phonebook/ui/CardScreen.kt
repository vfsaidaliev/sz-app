package ru.rossem.phonebook.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.rossem.phonebook.data.Employee
import ru.rossem.phonebook.data.Text as T

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployeeCard(
    employee: Employee,
    directory: Directory,
    favorite: Boolean,
    onToggleFavorite: () -> Unit,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val ctx = LocalContext.current
    val e = employee.e
    val (manager, managerText) = remember(employee, directory) { directory.manager(employee) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Карточка сотрудника") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
                actions = {
                    IconButton(onClick = onToggleFavorite) {
                        if (favorite) Icon(Icons.Filled.Star, "Убрать из избранного", tint = Color(0xFFFFB300))
                        else Icon(Icons.Outlined.StarOutline, "В избранное")
                    }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Avatar(employee, 96.dp)
                Spacer(Modifier.height(12.dp))
                SelectionContainer {
                    Text(employee.name, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                }
                if (e.role.isNotBlank()) {
                    Text(
                        e.role,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                if (e.isLead) {
                    Spacer(Modifier.height(8.dp))
                    AssistChip(
                        onClick = {},
                        label = { Text("Руководитель") },
                        leadingIcon = { Icon(Icons.Filled.WorkspacePremium, null, Modifier.width(AssistChipDefaults.IconSize)) },
                    )
                }
            }
            HorizontalDivider()

            if (e.mobile.isNotBlank()) {
                Field("Мобильный", e.mobile)
                ActionsRow {
                    Action(Icons.Filled.Call, "Позвонить") { Actions.dial(ctx, e.mobile) }
                    Action(Icons.Filled.Sms, "SMS") { Actions.sms(ctx, e.mobile) }
                    Action(Icons.AutoMirrored.Filled.Chat, "WhatsApp") { Actions.whatsapp(ctx, e.mobile) }
                    Action(Icons.Filled.ContentCopy, "Копировать") { Actions.copy(ctx, "Телефон", e.mobile) }
                }
            }
            if (e.ext.isNotBlank()) {
                Field("Внутренний номер", e.ext)
                ActionsRow {
                    Action(Icons.Filled.Call, "Позвонить") { Actions.dial(ctx, e.ext) }
                    Action(Icons.Filled.ContentCopy, "Копировать") { Actions.copy(ctx, "Внутренний номер", e.ext) }
                }
            }
            if (e.email.isNotBlank()) {
                Field("Почта", e.email)
                ActionsRow {
                    Action(Icons.Filled.Email, "Написать") { Actions.email(ctx, e.email) }
                    Action(Icons.Filled.ContentCopy, "Копировать") { Actions.copy(ctx, "Почта", e.email) }
                }
            }
            if (e.organization.isNotBlank()) Field("Организация", e.organization)
            if (employee.departmentPath.isNotEmpty()) Field("Отдел", employee.departmentPath.joinToString(" / "))
            if (e.office.isNotBlank()) Field("Кабинет", e.office)
            if (e.birthday.isNotBlank()) {
                val bd = employee.birthDate
                Field("День рождения", if (bd != null) "${T.dayMonth(bd)} ${bd.year}" else e.birthday)
            }
            if (manager != null) {
                Row(
                    Modifier.fillMaxWidth().clickable { onOpen(manager.id) }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(manager, 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Руководитель", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(manager.name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                        if (manager.e.role.isNotBlank()) {
                            Text(manager.e.role, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                }
            } else if (managerText != null) {
                Field("Руководитель", "$managerText (нет в справочнике)")
            }

            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = { Actions.addContact(ctx, employee) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) {
                Icon(Icons.Filled.PersonAdd, null)
                Spacer(Modifier.width(8.dp))
                Text("Добавить в контакты телефона")
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SelectionContainer { Text(value, style = MaterialTheme.typography.bodyLarge) }
    }
}

@Composable
private fun ActionsRow(content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun Action(icon: ImageVector, label: String, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, null, Modifier.width(AssistChipDefaults.IconSize)) },
    )
}
