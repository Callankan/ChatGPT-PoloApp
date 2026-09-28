package com.poloapp

import android.app.Application
import com.poloapp.data.CarRepository
import com.poloapp.platform.ReminderScheduler

class PoloApplication : Application() {
    val repository by lazy { CarRepository(this) }
    override fun onCreate() { super.onCreate(); ReminderScheduler.schedule(this) }
}
