package com.thejas.fleetmanagementtask.ui.vehicles

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.thejas.fleetmanagementtask.core.FleetEnums
import com.thejas.fleetmanagementtask.data.remote.dto.VehicleDto
import com.thejas.fleetmanagementtask.databinding.ItemVehicleBinding

class VehicleAdapter(
    private val onClick: (VehicleDto) -> Unit,
) : ListAdapter<VehicleDto, VehicleAdapter.Holder>(Diff) {

    class Holder(val binding: ItemVehicleBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemVehicleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val vehicle = getItem(position)
        with(holder.binding) {
            registrationText.text = vehicle.title
            detailsText.text = vehicle.subtitle
            mileageText.text = "${vehicle.currentMileage} km · ${FleetEnums.label(vehicle.fuelType)}"
            statusChip.text = FleetEnums.label(vehicle.status)
            warningIcon.visibility = if (vehicle.needsAttention) View.VISIBLE else View.GONE
            root.setOnClickListener { onClick(vehicle) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<VehicleDto>() {
        override fun areItemsTheSame(old: VehicleDto, new: VehicleDto) = old.id == new.id
        override fun areContentsTheSame(old: VehicleDto, new: VehicleDto) = old == new
    }
}
