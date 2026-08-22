package com.thejas.fleetmanagementtask.ui.auth

import android.app.Activity
import android.content.Intent
import com.thejas.fleetmanagementtask.ui.driver.DriverActivity
import com.thejas.fleetmanagementtask.ui.manager.ManagerActivity

const val ROLE_FLEET_MANAGER = "FLEET_MANAGER"

/** Role decides the home screen; there is no shared landing page. */
fun Activity.goHome(role: String?) {
    val target = if (role == ROLE_FLEET_MANAGER) {
        ManagerActivity::class.java
    } else {
        DriverActivity::class.java
    }
    startActivity(
        Intent(this, target)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    )
    finish()
}
