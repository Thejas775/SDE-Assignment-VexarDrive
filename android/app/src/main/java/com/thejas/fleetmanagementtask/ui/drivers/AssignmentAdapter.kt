package com.thejas.fleetmanagementtask.ui.drivers

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.thejas.fleetmanagementtask.core.FleetEnums
import com.thejas.fleetmanagementtask.data.remote.dto.AssignmentDto
import com.thejas.fleetmanagementtask.databinding.ItemAssignmentBinding

class AssignmentAdapter(
    private val onClick: (AssignmentDto) -> Unit,
) : ListAdapter<AssignmentDto, AssignmentAdapter.Holder>(Diff) {

    class Holder(val binding: ItemAssignmentBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemAssignmentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val assignment = getItem(position)
        with(holder.binding) {
            assignmentVehicle.text = assignment.vehicle.label
            assignmentPeriod.text = assignment.period
            assignmentStatus.text = FleetEnums.label(assignment.status)
            root.setOnClickListener { onClick(assignment) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<AssignmentDto>() {
        override fun areItemsTheSame(old: AssignmentDto, new: AssignmentDto) = old.id == new.id
        override fun areContentsTheSame(old: AssignmentDto, new: AssignmentDto) = old == new
    }
}
