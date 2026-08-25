package com.offhand.demo

import androidx.work.WorkManager
import com.offhand.AppContainer
import com.offhand.data.ContactEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Demo hygiene (M6): a long-press on the Home title wipes actions, notes and
 * contacts, cancels pending work, and re-seeds the contact list.
 */
class DemoSeeder(private val container: AppContainer) {

    suspend fun reset() = withContext(Dispatchers.IO) {
        WorkManager.getInstance(container.appContext).cancelAllWork()
        container.database.clearAllTables()
        seedContacts()
    }

    private suspend fun seedContacts() {
        listOf(
            "Priya Sharma" to "priya@example.com",
            "Rahul Verma" to "rahul@example.com",
            "Sneha Rao" to "sneha@example.com",
            "Karan Mehta" to "karan@example.com",
            "Kiran Joshi" to "kiran@example.com",
            "Professor Kumar" to "kumar@example.com",
        ).forEach { (name, email) ->
            container.contactDao.upsert(ContactEntity(name = name, email = email))
        }
    }
}
