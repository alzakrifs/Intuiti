package com.intuiti.cardscanner.util

import android.content.ContentValues
import android.content.Intent
import android.provider.ContactsContract
import com.intuiti.cardscanner.data.ContactFields

object ContactIntent {

    /**
     * Builds an intent that opens the system contacts editor pre-filled with [fields].
     * The user reviews and confirms — the app never writes to the contact provider directly.
     */
    fun build(fields: ContactFields): Intent =
        Intent(ContactsContract.Intents.Insert.ACTION).apply {
            type = ContactsContract.RawContacts.CONTENT_TYPE
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            val displayName = listOf(fields.firstName, fields.lastName)
                .filter { it.isNotBlank() }
                .joinToString(" ")
            if (displayName.isNotBlank()) {
                putExtra(ContactsContract.Intents.Insert.NAME, displayName)
            }
            if (fields.org.isNotBlank()) {
                putExtra(ContactsContract.Intents.Insert.COMPANY, fields.org)
            }
            if (fields.title.isNotBlank()) {
                putExtra(ContactsContract.Intents.Insert.JOB_TITLE, fields.title)
            }
            if (fields.phone.isNotBlank()) {
                putExtra(ContactsContract.Intents.Insert.PHONE, fields.phone)
                putExtra(
                    ContactsContract.Intents.Insert.PHONE_TYPE,
                    ContactsContract.CommonDataKinds.Phone.TYPE_WORK,
                )
            }
            if (fields.mobile.isNotBlank()) {
                putExtra(ContactsContract.Intents.Insert.SECONDARY_PHONE, fields.mobile)
                putExtra(
                    ContactsContract.Intents.Insert.SECONDARY_PHONE_TYPE,
                    ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE,
                )
            }
            if (fields.email.isNotBlank()) {
                putExtra(ContactsContract.Intents.Insert.EMAIL, fields.email)
                putExtra(
                    ContactsContract.Intents.Insert.EMAIL_TYPE,
                    ContactsContract.CommonDataKinds.Email.TYPE_WORK,
                )
            }
            if (fields.address.isNotBlank()) {
                putExtra(ContactsContract.Intents.Insert.POSTAL, fields.address)
                putExtra(
                    ContactsContract.Intents.Insert.POSTAL_TYPE,
                    ContactsContract.CommonDataKinds.StructuredPostal.TYPE_WORK,
                )
            }

            // Website is not a stock Insert extra — push it through DATA so the editor still picks it up.
            if (fields.website.isNotBlank()) {
                val extras = arrayListOf(
                    ContentValues().apply {
                        put(
                            ContactsContract.Data.MIMETYPE,
                            ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE,
                        )
                        put(ContactsContract.CommonDataKinds.Website.URL, fields.website)
                        put(
                            ContactsContract.CommonDataKinds.Website.TYPE,
                            ContactsContract.CommonDataKinds.Website.TYPE_WORK,
                        )
                    },
                )
                putParcelableArrayListExtra(ContactsContract.Intents.Insert.DATA, extras)
            }
        }
}
