package com.thejas.fleetmanagementtask.ui.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.thejas.fleetmanagementtask.data.remote.dto.RecentIncidentDto
import com.thejas.fleetmanagementtask.databinding.ItemRecentIncidentBinding

class RecentIncidentAdapter : ListAdapter<RecentIncidentDto, RecentIncidentAdapter.Holder>(Diff) {

    class Holder(val binding: ItemRecentIncidentBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemRecentIncidentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val incident = getItem(position)
        holder.binding.incidentTitle.text = incident.title
        holder.binding.incidentMeta.text =
            "${incident.registrationNumber} · ${incident.status} · ${incident.reportedAt}"
        holder.binding.incidentSeverity.text = incident.severity
    }

    private object Diff : DiffUtil.ItemCallback<RecentIncidentDto>() {
        override fun areItemsTheSame(old: RecentIncidentDto, new: RecentIncidentDto) =
            old.id == new.id

        override fun areContentsTheSame(old: RecentIncidentDto, new: RecentIncidentDto) =
            old == new
    }
}
