package com.prayag.flutter_workmanager_plugin.data

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "app_database.db"
        private const val DATABASE_VERSION = 1

        private const val TABLE_USERS = "users"
        private const val TABLE_USERS_COPY = "users_copy"

        // Columns for the users table
        private const val COLUMN_ID = "id"
        private const val COLUMN_NAME = "name"
        private const val COLUMN_DESCRIPTION = "description"
        private const val COLUMN_ADDRESS = "address"
        private const val COLUMN_GRADE = "grade"
    }

    override fun onCreate(db: SQLiteDatabase?) {
        val createUsersTable = """
            CREATE TABLE $TABLE_USERS (
                $COLUMN_ID INTEGER PRIMARY KEY,
                $COLUMN_NAME TEXT,
                $COLUMN_DESCRIPTION TEXT,
                $COLUMN_ADDRESS TEXT,
                $COLUMN_GRADE TEXT
            )
        """

        val createUsersCopyTable = """
            CREATE TABLE $TABLE_USERS_COPY (
                $COLUMN_ID INTEGER PRIMARY KEY,
                $COLUMN_NAME TEXT,
                $COLUMN_DESCRIPTION TEXT,
                $COLUMN_ADDRESS TEXT,
                $COLUMN_GRADE TEXT
            )
        """

        db?.execSQL(createUsersTable)
        db?.execSQL(createUsersCopyTable)
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        db?.execSQL("DROP TABLE IF EXISTS $TABLE_USERS")
        db?.execSQL("DROP TABLE IF EXISTS $TABLE_USERS_COPY")
        onCreate(db)
    }

    // Function to copy data from users to users_copy
    @SuppressLint("Range")
    fun copyUserDataToUsersCopy() {
        val db = writableDatabase

        // Delete any existing data in the users_copy table
        db.delete(TABLE_USERS_COPY, null, null)

        // Insert all users from users to users_copy
        val cursor = db.query(TABLE_USERS, null, null, null, null, null, null)
        while (cursor.moveToNext()) {
            val contentValues = ContentValues().apply {
                put(COLUMN_ID, cursor.getInt(cursor.getColumnIndex(COLUMN_ID)))
                put(COLUMN_NAME, cursor.getString(cursor.getColumnIndex(COLUMN_NAME)))
                put(COLUMN_DESCRIPTION, cursor.getString(cursor.getColumnIndex(COLUMN_DESCRIPTION)))
                put(COLUMN_ADDRESS, cursor.getString(cursor.getColumnIndex(COLUMN_ADDRESS)))
                put(COLUMN_GRADE, cursor.getString(cursor.getColumnIndex(COLUMN_GRADE)))
            }
            db.insert(TABLE_USERS_COPY, null, contentValues)
        }
        cursor.close()
        db.close()
    }
}