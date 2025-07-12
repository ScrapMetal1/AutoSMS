package com.elias.autosms.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.elias.autosms.data.SmsSchedule
import com.elias.autosms.databinding.ItemSmsScheduleBinding

class SmsScheduleAdapter(
    private val onToggleClick: (SmsSchedule) -> Unit,
    private val onEditClick: (SmsSchedule) -> Unit,
    private val onDeleteClick: (SmsSchedule) -> Unit
) : ListAdapter<SmsSchedule, SmsScheduleAdapter.SmsScheduleViewHolder>(DiffCallback) {

    object DiffCallback : DiffUtil.ItemCallback<SmsSchedule>() {
        override fun areItemsTheSame(oldItem: SmsSchedule, newItem: SmsSchedule): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: SmsSchedule, newItem: SmsSchedule): Boolean {
            return oldItem == newItem
        }
    }

    class SmsScheduleViewHolder(private val binding: ItemSmsScheduleBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(
            schedule: SmsSchedule,
            onToggleClick: (SmsSchedule) -> Unit,
            onEditClick: (SmsSchedule) -> Unit,
            onDeleteClick: (SmsSchedule) -> Unit
        ) {
            binding.apply {
                textContactName.text = schedule.contactName
                textPhoneNumber.text = schedule.phoneNumber
                textMessage.text = schedule.message
                textTime.text = schedule.getFormattedTime()
                switchEnabled.isChecked = schedule.isEnabled

                // Apply visual feedback for enabled/disabled state
                val alpha = if (schedule.isEnabled) 1.0f else 0.5f
                textContactName.alpha = alpha
                textPhoneNumber.alpha = alpha
                textMessage.alpha = alpha
                textTime.alpha = alpha

                switchEnabled.setOnCheckedChangeListener { _, _ ->
                    onToggleClick(schedule)
                }

                buttonEdit.setOnClickListener {
                    onEditClick(schedule)
                }

                buttonDelete.setOnClickListener {
                    onDeleteClick(schedule)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SmsScheduleViewHolder {
        val binding = ItemSmsScheduleBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SmsScheduleViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SmsScheduleViewHolder, position: Int) {
        holder.bind(getItem(position), onToggleClick, onEditClick, onDeleteClick)
    }
}