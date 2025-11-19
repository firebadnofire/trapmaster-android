package org.archuser.trapmaster.ui

import android.app.Dialog
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.core.net.toUri
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.archuser.trapmaster.R
import org.archuser.trapmaster.databinding.DialogRtspUriBinding
import java.util.Locale

class RtspUriDialogFragment : DialogFragment() {

    interface Callback {
        fun onRtspUriSelected(uri: Uri)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogRtspUriBinding.inflate(layoutInflater)
        val initialUri = arguments?.getString(ARG_INITIAL_URI)
        if (!initialUri.isNullOrBlank()) {
            binding.rtspUriInput.setText(initialUri)
        }
        binding.rtspUriInput.doAfterTextChanged {
            binding.rtspUriLayout.error = null
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.rtsp_uri_title)
            .setView(binding.root)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.rtsp_uri_positive, null)
            .create()

        dialog.setOnShowListener {
            val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            button.setOnClickListener {
                val uriString = binding.rtspUriInput.text?.toString()?.trim().orEmpty()
                val uri = parseSupportedUri(uriString)
                if (uri == null) {
                    binding.rtspUriLayout.error = getString(R.string.rtsp_uri_error_invalid)
                } else {
                    (activity as? Callback)?.onRtspUriSelected(uri)
                    dismiss()
                }
            }
        }

        return dialog
    }

    private fun parseSupportedUri(value: String): Uri? {
        if (value.isBlank()) return null
        val uri = try {
            value.toUri()
        } catch (ignored: Exception) {
            return null
        }
        return if (isSupportedUri(uri)) uri else null
    }

    companion object {
        private const val ARG_INITIAL_URI = "initial_uri"
        const val TAG = "RtspUriDialogFragment"
        private val SUPPORTED_SCHEMES = setOf("rtsp", "rtsps")

        fun newInstance(initialUri: String? = null): RtspUriDialogFragment {
            return RtspUriDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_INITIAL_URI, initialUri)
                }
            }
        }

        fun isSupportedUri(uri: Uri?): Boolean {
            val scheme = uri?.scheme?.lowercase(Locale.US) ?: return false
            if (!SUPPORTED_SCHEMES.contains(scheme)) return false
            val host = uri.host
            val authority = uri.authority
            return !host.isNullOrBlank() || !authority.isNullOrBlank()
        }
    }
}
