package com.algoarch.app

import android.app.Application
import com.algoarch.app.data.ContentRepository
import com.algoarch.app.data.ProgressRepository
import java.time.LocalDate

class AlgoArchApp : Application() {
    lateinit var content: ContentRepository
        private set
    lateinit var progress: ProgressRepository
        private set

    override fun onCreate() {
        super.onCreate()
        content = ContentRepository(this)
        progress = ProgressRepository(this)
    }
}

/** Local calendar day number, used for streaks. */
fun today(): Long = LocalDate.now().toEpochDay()
