package ru.rossem.phonebook.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.widget.Toast
import ru.rossem.phonebook.data.Employee
import ru.rossem.phonebook.data.Text

object Actions {
    private fun start(ctx: Context, intent: Intent) {
        try {
            ctx.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(ctx, "Нет приложения для этого действия", Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            Toast.makeText(ctx, "Действие недоступно", Toast.LENGTH_SHORT).show()
        }
    }

    fun dial(ctx: Context, number: String) =
        start(ctx, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(Text.dialNumber(number)))))

    fun sms(ctx: Context, number: String) =
        start(ctx, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(Text.dialNumber(number)))))

    fun whatsapp(ctx: Context, number: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + Text.internationalDigits(number)))
        val pm = ctx.packageManager
        val pkg = listOf("com.whatsapp", "com.whatsapp.w4b").firstOrNull {
            runCatching { pm.getPackageInfo(it, 0) }.isSuccess
        }
        if (pkg != null) intent.setPackage(pkg)
        start(ctx, intent)
    }

    fun email(ctx: Context, address: String) =
        start(ctx, Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + address.trim())))

    fun copy(ctx: Context, label: String, value: String) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, value))
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(ctx, "Скопировано", Toast.LENGTH_SHORT).show()
        }
    }

    fun addContact(ctx: Context, e: Employee) {
        val i = Intent(ContactsContract.Intents.Insert.ACTION).apply {
            type = ContactsContract.RawContacts.CONTENT_TYPE
            putExtra(ContactsContract.Intents.Insert.NAME, e.name)
            if (e.e.mobile.isNotBlank()) {
                putExtra(ContactsContract.Intents.Insert.PHONE, Text.dialNumber(e.e.mobile))
                putExtra(ContactsContract.Intents.Insert.PHONE_TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
            }
            if (e.e.ext.isNotBlank()) {
                putExtra(ContactsContract.Intents.Insert.SECONDARY_PHONE, e.e.ext)
                putExtra(ContactsContract.Intents.Insert.SECONDARY_PHONE_TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_WORK)
            }
            if (e.e.email.isNotBlank()) {
                putExtra(ContactsContract.Intents.Insert.EMAIL, e.e.email)
                putExtra(ContactsContract.Intents.Insert.EMAIL_TYPE, ContactsContract.CommonDataKinds.Email.TYPE_WORK)
            }
            if (e.e.organization.isNotBlank()) putExtra(ContactsContract.Intents.Insert.COMPANY, e.e.organization)
            if (e.e.role.isNotBlank()) putExtra(ContactsContract.Intents.Insert.JOB_TITLE, e.e.role)
        }
        start(ctx, i)
    }
}
