package com.elias.autosms.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import com.elias.autosms.R
import com.elias.autosms.data.ContextDocument
import com.elias.autosms.databinding.ActivityAddEditContextDocumentBinding
import com.elias.autosms.viewmodel.AddEditContextDocumentViewModel
import com.elias.autosms.viewmodel.AddEditContextDocumentViewModelFactory

class AddEditContextDocumentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddEditContextDocumentBinding
    private lateinit var viewModel: AddEditContextDocumentViewModel
    private var existingId: Long? = null

    // Storage Access Framework: lets the user pick any document the OS can
    // resolve. We narrow the MIME hint to plain text but accept */* fallback
    // because some providers report PDFs / docs without an exact match.
    private val openDocumentLauncher =
            registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) viewModel.importTextFile(uri)
            }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddEditContextDocumentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val factory = AddEditContextDocumentViewModelFactory(application)
        viewModel = ViewModelProvider(this, factory)[AddEditContextDocumentViewModel::class.java]

        loadExisting()
        wireListeners()
        observeViewModel()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    private fun loadExisting() {
        val doc: ContextDocument? = intent.getParcelableExtra(EXTRA_DOCUMENT)
        if (doc != null) {
            existingId = doc.id
            supportActionBar?.setTitle(R.string.context_document_edit_title)
            binding.editTitle.setText(doc.title)
            binding.editContent.setText(doc.content)
        } else {
            supportActionBar?.setTitle(R.string.context_document_add_title)
        }
    }

    private fun wireListeners() {
        binding.buttonImport.setOnClickListener {
            // text/* covers .txt; some providers tag .md / .csv differently so
            // we also accept the broader application/* group via OpenDocument's
            // multi-MIME support.
            openDocumentLauncher.launch(arrayOf("text/*", "text/plain"))
        }

        binding.buttonSave.setOnClickListener {
            viewModel.save(
                    existingId = existingId,
                    title = binding.editTitle.text?.toString().orEmpty(),
                    content = binding.editContent.text?.toString().orEmpty()
            )
        }
    }

    private fun observeViewModel() {
        viewModel.importedText.observe(this) { text ->
            text ?: return@observe
            // Replacing existing content is intentional: the user explicitly
            // chose a file, so they want it as the new content.
            binding.editContent.setText(text)
            // Auto-fill the title from the file's first non-blank line if empty.
            if (binding.editTitle.text?.isBlank() == true) {
                val firstLine = text.lineSequence()
                        .firstOrNull { it.isNotBlank() }
                        ?.take(80)
                if (!firstLine.isNullOrBlank()) binding.editTitle.setText(firstLine)
            }
        }
        viewModel.importError.observe(this) { msg ->
            msg ?: return@observe
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        }
        viewModel.saveResult.observe(this) { result ->
            result ?: return@observe
            when (result) {
                AddEditContextDocumentViewModel.SaveResult.Success -> {
                    Toast.makeText(this, R.string.context_document_saved, Toast.LENGTH_SHORT).show()
                    finish()
                }
                is AddEditContextDocumentViewModel.SaveResult.Error -> {
                    Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    companion object {
        const val EXTRA_DOCUMENT = "document"
    }
}
