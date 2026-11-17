package com.example.test2.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import com.example.test2.ai.SmartHeuristicParser

object ActionCardIntentHelper {

    fun copyToClipboard(context: Context, label: String, text: String, toastMessage: String? = null) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(label, text)
            clipboard.setPrimaryClip(clip)
            val msg = toastMessage ?: "Đã sao chép $label"
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
        }
    }

    fun openGoogleMaps(context: Context, address: String) {
        try {
            val encoded = Uri.encode(address)
            val gmmIntentUri = Uri.parse("geo:0,0?q=$encoded")
            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
            mapIntent.setPackage("com.google.android.apps.maps")
            if (mapIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(mapIntent)
                return
            }
        } catch (_: Exception) {
        }

        // Fallback: Web browser Google Maps URL
        try {
            val webUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode(address)}")
            val browserIntent = Intent(Intent.ACTION_VIEW, webUri)
            context.startActivity(browserIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "Không thể mở bản đồ: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun dialPhoneNumber(context: Context, phoneNumber: String) {
        try {
            val cleanPhone = phoneNumber.replace(" ", "").replace("-", "").replace(".", "")
            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleanPhone"))
            context.startActivity(dialIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "Không thể thực hiện cuộc gọi: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun openZalo(context: Context, phoneNumber: String) {
        try {
            var cleanPhone = phoneNumber.replace(" ", "").replace("-", "").replace(".", "")
            if (cleanPhone.startsWith("+84")) {
                cleanPhone = "0" + cleanPhone.substring(3)
            }
            val zaloUri = Uri.parse("https://zalo.me/$cleanPhone")
            val intent = Intent(Intent.ACTION_VIEW, zaloUri)
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Không thể mở Zalo: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun insertContact(context: Context, name: String?, phone: String?, email: String?, company: String?) {
        try {
            val intent = Intent(Intent.ACTION_INSERT_OR_EDIT).apply {
                type = ContactsContract.Contacts.CONTENT_ITEM_TYPE
                if (!name.isNullOrBlank()) {
                    putExtra(ContactsContract.Intents.Insert.NAME, name)
                }
                if (!phone.isNullOrBlank()) {
                    putExtra(ContactsContract.Intents.Insert.PHONE, phone)
                }
                if (!email.isNullOrBlank()) {
                    putExtra(ContactsContract.Intents.Insert.EMAIL, email)
                }
                if (!company.isNullOrBlank()) {
                    putExtra(ContactsContract.Intents.Insert.COMPANY, company)
                }
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Không thể mở danh bạ: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun addToCalendar(context: Context, title: String, startTimeMillis: Long, description: String? = null) {
        try {
            val intent = SmartHeuristicParser.createCalendarIntent(title, startTimeMillis, description ?: "")
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Không thể mở ứng dụng Lịch: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
