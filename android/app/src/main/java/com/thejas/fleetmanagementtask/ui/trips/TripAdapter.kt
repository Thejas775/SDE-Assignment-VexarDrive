package com.thejas.fleetmanagementtask.ui.trips

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.thejas.fleetmanagementtask.core.FleetEnums
import com.thejas.fleetmanagementtask.data.remote.dto.TripDto
import com.thejas.fleetmanagementtask.databinding.ItemTripBinding
import com.thejas.fleetmanagementtask.ui.common.formatInstant
import com.thejas.fleetmanagementtask.ui.common.applyStatus

class TripAdapter(
    private val onClick: (TripDto) -> Unit,
) : ListAdapter<TripDto, TripAdapter.Holder>(Diff) {

    class Holder(val binding: ItemTripBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemTripBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val trip = getItem(position)
        with(holder.binding) {
            tripNumber.text = trip.tripNumber
            tripRoute.text = trip.route
            tripMeta.text = "${trip.vehicle.registrationNumber} · ${trip.driver.fullName}"
            tripSchedule.text = listOfNotNull(
                formatInstant(trip.scheduledStart),
                trip.distanceKm?.let { "$it km" },
            ).joinToString(" · ")
            tripStatus.applyStatus(trip.status)
            root.setOnClickListener { onClick(trip) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<TripDto>() {
        override fun areItemsTheSame(old: TripDto, new: TripDto) = old.id == new.id
        override fun areContentsTheSame(old: TripDto, new: TripDto) = old == new
    }
}
