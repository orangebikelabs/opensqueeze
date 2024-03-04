/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */
package com.orangebikelabs.orangesqueeze.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.ColumnAdapter
import app.cash.sqldelight.EnumColumnAdapter
import app.cash.sqldelight.adapter.primitive.IntColumnAdapter
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.orangebikelabs.orangesqueeze.R
import com.orangebikelabs.orangesqueeze.app.PlayerMenuHelper
import com.orangebikelabs.orangesqueeze.common.*
import com.orangebikelabs.orangesqueeze.download.DownloadStatus
import java.util.concurrent.atomic.AtomicReference

/**
 * @author tbsandee@orangebikelabs.com
 */
class DatabaseAccess {

    companion object {
        private var instance: OSDatabase? = null
        private lateinit var callback: Callback
        private val staticLock = Any()

        @JvmStatic
        fun getInstance(context: Context): OSDatabase {
            synchronized(staticLock) {
                var retval = instance
                if (retval == null) {
                    val databaseName = context.resources.getString(R.string.database_name)
                    callback = Callback(OSDatabase.Schema)
                    val driver = AndroidSqliteDriver(schema = OSDatabase.Schema, context = context, name = databaseName, callback = callback)
                    retval = OSDatabase(driver,
                            serverAdapter = Server.Adapter(
                                    serverwakeonlanAdapter = WakeOnLanAdapter(),
                                    servertypeAdapter = EnumColumnAdapter(),
                                    serverlastplayerAdapter = PlayerIdAdapter(),
                                    servermenunodesAdapter = RootMenuNodesAdapter(),
                                    serverplayermenusAdapter = PlayerMenusAdapter(),
                                    serverportAdapter = IntColumnAdapter,
                            ),
                            downloadAdapter = Download.Adapter(
                                    DownloadStatusAdapter()
                            )
                    )
                    instance = retval
                }
                return retval
            }
        }

        internal fun internalGetLegacyDatabase(database: OSDatabase): SupportSQLiteDatabase {
            // force database init if it isn't already
            database.globalQueries.changes().executeAsOneOrNull()

            synchronized(staticLock) {
                return callback.database.get()
            }
        }
    }

    private class DownloadStatusAdapter : ColumnAdapter<DownloadStatus, String> {
        override fun decode(databaseValue: String): DownloadStatus {
            return DownloadStatus.fromJson(databaseValue)
        }

        override fun encode(value: DownloadStatus): String {
            return value.toJson()
        }
    }

    private class WakeOnLanAdapter : ColumnAdapter<WakeOnLanSettings, String> {
        override fun decode(databaseValue: String): WakeOnLanSettings {
            return WakeOnLanSettings.fromJson(databaseValue)
        }

        override fun encode(value: WakeOnLanSettings): String {
            return value.toJson()
        }
    }

    private class PlayerIdAdapter : ColumnAdapter<PlayerId, String> {
        override fun decode(databaseValue: String): PlayerId {
            return PlayerId(databaseValue)
        }

        override fun encode(value: PlayerId): String {
            return value.toString()
        }
    }

    private class PlayerMenusAdapter : ColumnAdapter<Map<PlayerId, PlayerMenuHelper.PlayerMenuSet>, ByteArray> {
        override fun decode(databaseValue: ByteArray): Map<PlayerId, PlayerMenuHelper.PlayerMenuSet> {
            return PlayerMenuHelper.loadPlayerMenus(databaseValue)
        }

        override fun encode(value: Map<PlayerId, PlayerMenuHelper.PlayerMenuSet>): ByteArray {
            return JsonHelper.getSmileObjectWriter().writeValueAsBytes(value)
        }
    }

    private class RootMenuNodesAdapter : ColumnAdapter<List<String>, ByteArray> {
        override fun decode(databaseValue: ByteArray): List<String> {
            return PlayerMenuHelper.loadRootMenuNodes(databaseValue)
        }

        override fun encode(value: List<String>): ByteArray {
            return JsonHelper.getSmileObjectWriter().writeValueAsBytes(value)
        }
    }

    private class Callback(schema: SqlSchema<QueryResult.Value<Unit>>) : AndroidSqliteDriver.Callback(schema) {
        val database = AtomicReference<SupportSQLiteDatabase>()

        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            database.set(db)
        }

        override fun onConfigure(db: SupportSQLiteDatabase) {
            super.onConfigure(db)

            db.setForeignKeyConstraintsEnabled(true)
        }
    }

    /**
     * used in the event of a problem during upgrade
     */
    private fun destructiveUpgrade(db: SQLiteDatabase) {
        val cursor = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null)
        cursor.moveToFirst()
        do {
            val table = cursor.getString(0)
            if (table == null || table.startsWith("android") || table.startsWith("sqlite")) {
                continue
            }
            db.execSQL("DROP TABLE $table")
        } while (cursor.moveToNext())
        cursor.close()
    }
}

fun OSDatabase.deleteServer(id: Long) {
    transaction {
        downloadQueries.deleteWithServerId(id)
        cacheQueries.deleteWithServerId(id)
        serverQueries.deleteById(id)
    }
}

fun OSDatabase.getLegacyDatabase(): SupportSQLiteDatabase {
    return DatabaseAccess.internalGetLegacyDatabase(this)
}