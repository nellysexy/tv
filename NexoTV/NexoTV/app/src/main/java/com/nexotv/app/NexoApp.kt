package com.nexotv.app

import android.app.Application
import com.nexotv.app.data.Repository

class NexoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        repo = Repository(this)
        repo.start()
    }

    companion object {
        lateinit var repo: Repository
            private set
    }
}
