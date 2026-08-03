package com.schortgen.vehiclelogai.ui.scanphotos

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Simple ViewModel factory for ScanPhotosViewModel.
 *
 * The factory provides a default addToReviewQueue lambda that always returns true —
 * replace the implementation inside create() with a call into your ReviewQueueViewModel
 * or repository to actually enqueue the photo.
 */
class ScanViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ScanPhotosViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            val addToQueueLambda: suspend (Uri) -> Boolean = { uri ->
                // TODO: Replace this stub with a real enqueue implementation that uses
                // your ReviewQueueViewModel or repository. This placeholder simply
                // returns true to indicate the item was "queued".
                withContext(Dispatchers.IO) {
                    try {
                        // Example placeholder; perform real enqueue here.
                        true
                    } catch (_: Exception) {
                        false
                    }
                }
            }

            return ScanPhotosViewModel(addToQueueLambda) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
