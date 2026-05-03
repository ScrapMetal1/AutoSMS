package com.elias.autosms.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.elias.autosms.data.AutoReplyRule
import com.elias.autosms.databinding.ItemAutoReplyRuleBinding

class AutoReplyRuleAdapter(
        private val onToggleClick: (AutoReplyRule, Boolean) -> Unit,
        private val onEditClick: (AutoReplyRule) -> Unit,
        private val onDeleteClick: (AutoReplyRule) -> Unit
) : ListAdapter<AutoReplyRule, AutoReplyRuleAdapter.RuleViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RuleViewHolder {
        val binding = ItemAutoReplyRuleBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
        )
        return RuleViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RuleViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class RuleViewHolder(private val binding: ItemAutoReplyRuleBinding) :
            RecyclerView.ViewHolder(binding.root) {
        fun bind(rule: AutoReplyRule) {
            binding.textDisplayName.text = rule.displayName.ifBlank { rule.phoneNumber }
            binding.textPhoneNumber.text = rule.phoneNumber
            binding.textPrompt.text = rule.systemPrompt
            // Detach listener before set so re-binding doesn't trigger it.
            binding.switchEnabled.setOnCheckedChangeListener(null)
            binding.switchEnabled.isChecked = rule.isEnabled
            binding.switchEnabled.setOnCheckedChangeListener { _, isChecked ->
                onToggleClick(rule, isChecked)
            }
            binding.buttonEdit.setOnClickListener { onEditClick(rule) }
            binding.buttonDelete.setOnClickListener { onDeleteClick(rule) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<AutoReplyRule>() {
            override fun areItemsTheSame(a: AutoReplyRule, b: AutoReplyRule) = a.id == b.id
            override fun areContentsTheSame(a: AutoReplyRule, b: AutoReplyRule) = a == b
        }
    }
}
