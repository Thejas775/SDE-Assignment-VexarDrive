package com.thejas.fleetmanagementtask.ui.drivers

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.thejas.fleetmanagementtask.R
import com.thejas.fleetmanagementtask.core.FleetEnums
import com.thejas.fleetmanagementtask.data.remote.dto.DriverDto
import com.thejas.fleetmanagementtask.databinding.ItemDriverBinding

class DriverAdapter(
    private val onClick: (DriverDto) -> Unit,
) : ListAdapter<DriverDto, DriverAdapter.Holder>(Diff) {

    class Holder(val binding: ItemDriverBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemDriverBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val driver = getItem(position)
        with(holder.binding) {
            nameText.text = driver.fullName
            licenceText.text = root.context.getString(R.string.driver_licence, driver.licenseNumber)
            vehicleText.text = driver.assignedVehicle?.label
                ?: root.context.getString(R.string.driver_unassigned)
            statusChip.text = FleetEnums.label(driver.status)
            warningIcon.visibility = if (driver.needsAttention) View.VISIBLE else View.GONE
            root.setOnClickListener { onClick(driver) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<DriverDto>() {
        override fun areItemsTheSame(old: DriverDto, new: DriverDto) = old.id == new.id
        override fun areContentsTheSame(old: DriverDto, new: DriverDto) = old == new
    }
}
