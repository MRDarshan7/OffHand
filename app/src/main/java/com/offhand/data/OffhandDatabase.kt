package com.offhand.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ActionEntity::class, ContactEntity::class, NoteEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class OffhandDatabase : RoomDatabase() {
    abstract fun actionDao(): ActionDao
    abstract fun contactDao(): ContactDao
    abstract fun noteDao(): NoteDao

    companion object {
        /** Demo seed: the in-app contact list starts non-empty and editable. */
        val seedCallback = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                listOf(
                    "Priya Sharma" to "priya@example.com",
                    "Rahul Verma" to "rahul@example.com",
                    "Sneha Rao" to "sneha@example.com",
                    "Karan Mehta" to "karan@example.com",
                    "Kiran Joshi" to "kiran@example.com",
                    "Professor Kumar" to "kumar@example.com",
                ).forEach { (name, email) ->
                    db.execSQL(
                        "INSERT INTO contacts (name, email) VALUES (?, ?)",
                        arrayOf(name, email),
                    )
                }
            }
        }
    }
}
