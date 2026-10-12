package com.primalapp.android

import android.app.Application
import com.primalapp.database.PlatformContext
import com.primalapp.sync.AppCore
import com.primalapp.viewmodel.AppViewModel

/**
 * Процесс приложения: один [AppCore] (БД, справочники, синхронизация) и одна корневая ViewModel — переживают
 * пересоздание экрана и получают ссылки на сайт (App Links).
 */
class PrimalApplication : Application() {

    lateinit var core: AppCore
        private set

    lateinit var appViewModel: AppViewModel
        private set

    override fun onCreate() {
        super.onCreate()
        core = AppCore(PlatformContext(this))
        appViewModel = AppViewModel(core)
    }
}
