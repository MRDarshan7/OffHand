package com.offhand

import android.content.Context
import androidx.room.Room
import com.offhand.data.ActionDao
import com.offhand.data.OffhandDatabase
import com.offhand.dispatch.ConnectivityObserver

/**
 * Plain constructor-injection container. No DI framework by design.
 */
class AppContainer(context: Context) {

    val database: OffhandDatabase = Room.databaseBuilder(
        context.applicationContext,
        OffhandDatabase::class.java,
        "offhand.db",
    )
        .fallbackToDestructiveMigration()
        .build()

    val actionDao: ActionDao
        get() = database.actionDao()

    val connectivityObserver = ConnectivityObserver(context.applicationContext)
}
