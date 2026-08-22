package com.thejas.fleetmanagementtask.ui.dashboard

import com.thejas.fleetmanagementtask.R
import com.thejas.fleetmanagementtask.data.remote.dto.DashboardDto

/** One tile on the overview grid. */
data class Stat(val labelRes: Int, val value: String)

/** Maps the API payload onto the metrics listed in spec section 12. */
fun DashboardDto.toStats(): List<Stat> = listOf(
    Stat(R.string.stat_total_vehicles, vehicles.total.toString()),
    Stat(R.string.stat_available, vehicles.available.toString()),
    Stat(R.string.stat_on_trip, vehicles.onTrip.toString()),
    Stat(R.string.stat_maintenance, vehicles.inMaintenance.toString()),
    Stat(R.string.stat_inactive, vehicles.inactive.toString()),
    Stat(R.string.stat_active_trips, trips.active.toString()),
    Stat(R.string.stat_distance_today, distanceTodayKm),
    Stat(R.string.stat_drivers_active, driversActive.toString()),
    Stat(R.string.stat_maintenance_due, maintenanceDue.toString()),
    Stat(R.string.stat_open_incidents, openIncidents.toString()),
    Stat(R.string.stat_expiring_docs, expiringDocuments.total.toString()),
)
