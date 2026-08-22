package com.thejas.fleetmanagementtask

import android.app.Application
import com.thejas.fleetmanagementtask.di.ServiceLocator

class FleetApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}
