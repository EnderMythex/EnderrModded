package com.endermythex.enderrmodded

import android.app.Application
import com.endermythex.enderrmodded.data.PatchRepository
import com.endermythex.enderrmodded.data.PatchedAppsStore
import com.endermythex.enderrmodded.data.Settings
import com.endermythex.enderrmodded.work.Notifications
import com.endermythex.enderrmodded.work.UpdateWorker

/** Point d'entrée : singletons partagés entre l'UI et les tâches de fond. */
class EnderrApp : Application() {
    val settings by lazy { Settings(this) }
    val store by lazy { PatchedAppsStore(this) }
    val patches by lazy { PatchRepository(this, settings) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Notifications.createChannels(this)
        UpdateWorker.schedule(this, settings)
    }

    companion object {
        lateinit var instance: EnderrApp
            private set
    }
}
