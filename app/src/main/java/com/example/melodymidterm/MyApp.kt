package com.example.melodymidterm

import android.app.Application
import data.AppDatabase

class MyApp : Application() {
    lateinit var database: AppDatabase

    override fun onCreate() {
        super.onCreate()
        // Initialize database
        database = AppDatabase.getDatabase(this)
    }
}
