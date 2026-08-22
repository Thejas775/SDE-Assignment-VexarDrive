package com.thejas.fleetmanagementtask.ui.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.thejas.fleetmanagementtask.databinding.ItemStatBinding

class StatAdapter : ListAdapter<Stat, StatAdapter.Holder>(Diff) {

    class Holder(val binding: ItemStatBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemStatBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val stat = getItem(position)
        holder.binding.statValue.text = stat.value
        holder.binding.statLabel.setText(stat.labelRes)
    }

    private object Diff : DiffUtil.ItemCallback<Stat>() {
        override fun areItemsTheSame(old: Stat, new: Stat) = old.labelRes == new.labelRes
        override fun areContentsTheSame(old: Stat, new: Stat) = old == new
    }
}
