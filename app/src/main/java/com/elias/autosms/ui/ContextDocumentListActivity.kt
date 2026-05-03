package com.elias.autosms.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.elias.autosms.R
import com.elias.autosms.databinding.ActivityContextDocumentListBinding
import com.elias.autosms.ui.adapter.ContextDocumentAdapter
import com.elias.autosms.viewmodel.ContextDocumentListViewModel
import com.elias.autosms.viewmodel.ContextDocumentListViewModelFactory

class ContextDocumentListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityContextDocumentListBinding
    private lateinit var viewModel: ContextDocumentListViewModel
    private lateinit var adapter: ContextDocumentAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityContextDocumentListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val factory = ContextDocumentListViewModelFactory(application)
        viewModel = ViewModelProvider(this, factory)[ContextDocumentListViewModel::class.java]

        adapter = ContextDocumentAdapter(
                onToggleClick = { doc, enabled -> viewModel.setEnabled(doc, enabled) },
                onEditClick = { doc ->
                    val intent = Intent(this, AddEditContextDocumentActivity::class.java)
                    intent.putExtra(AddEditContextDocumentActivity.EXTRA_DOCUMENT, doc)
                    startActivity(intent)
                },
                onDeleteClick = { doc ->
                    AlertDialog.Builder(this)
                            .setMessage(R.string.context_document_delete_confirm)
                            .setPositiveButton(R.string.delete_button) { _, _ -> viewModel.delete(doc) }
                            .setNegativeButton(R.string.cancel_button, null)
                            .show()
                }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        binding.recyclerView.setHasFixedSize(true)

        binding.fabAdd.setOnClickListener {
            startActivity(Intent(this, AddEditContextDocumentActivity::class.java))
        }

        viewModel.documents.observe(this) { docs ->
            adapter.submitList(docs)
            binding.emptyView.visibility = if (docs.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
