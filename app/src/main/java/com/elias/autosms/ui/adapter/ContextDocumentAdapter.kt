package com.elias.autosms.ui.adapter

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.elias.autosms.data.ContextDocument
import com.elias.autosms.databinding.ItemContextDocumentBinding

class ContextDocumentAdapter(
        private val onToggleClick: (ContextDocument, Boolean) -> Unit,
        private val onEditClick: (ContextDocument) -> Unit,
        private val onDeleteClick: (ContextDocument) -> Unit
) : ListAdapter<ContextDocument, ContextDocumentAdapter.DocumentViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DocumentViewHolder {
        val binding = ItemContextDocumentBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
        )
        return DocumentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DocumentViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class DocumentViewHolder(private val binding: ItemContextDocumentBinding) :
            RecyclerView.ViewHolder(binding.root) {
        fun bind(doc: ContextDocument) {
            binding.textTitle.text = doc.title
            binding.textPreview.text = doc.content
            val rel = DateUtils.getRelativeTimeSpanString(
                    doc.updatedAt,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS
            )
            binding.textMeta.text = "${doc.characterCount()} chars · updated $rel"
            binding.switchEnabled.setOnCheckedChangeListener(null)
            binding.switchEnabled.isChecked = doc.isEnabled
            binding.switchEnabled.setOnCheckedChangeListener { _, checked ->
                onToggleClick(doc, checked)
            }
            binding.buttonEdit.setOnClickListener { onEditClick(doc) }
            binding.buttonDelete.setOnClickListener { onDeleteClick(doc) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ContextDocument>() {
            override fun areItemsTheSame(a: ContextDocument, b: ContextDocument) = a.id == b.id
            override fun areContentsTheSame(a: ContextDocument, b: ContextDocument) = a == b
        }
    }
}
