package com.thejas.fleetmanagementtask.ui.incidents

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.thejas.fleetmanagementtask.core.FleetEnums
import com.thejas.fleetmanagementtask.data.remote.dto.IncidentDto
import com.thejas.fleetmanagementtask.databinding.ItemIncidentBinding
import com.thejas.fleetmanagementtask.ui.common.formatInstant

class IncidentAdapter(
    private val onClick: (IncidentDto) -> Unit,
) : ListAdapter<IncidentDto, IncidentAdapter.Holder>(Diff) {

    class Holder(val binding: ItemIncidentBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemIncidentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val incident = getItem(position)
        with(holder.binding) {
            incidentTitle.text = incident.title
            incidentVehicle.text = incident.vehicle.label
            incidentMeta.text = listOf(
                FleetEnums.label(incident.status),
                incident.reportedBy.fullName,
                formatInstant(incident.reportedAt),
            ).joinToString(" · ")
            severityChip.text = FleetEnums.label(incident.severity)
            root.setOnClickListener { onClick(incident) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<IncidentDto>() {
        override fun areItemsTheSame(old: IncidentDto, new: IncidentDto) = old.id == new.id
        override fun areContentsTheSame(old: IncidentDto, new: IncidentDto) = old == new
    }
}
