package com.schortgen.vehiclelogai.ui.scanphotos

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ViewModel factory for ScanPhotosViewModel.
 *
 * It accepts an addToQueueLambda so callers can forward enqueue operations to
 * the app's ReviewQueueViewModel or repository. A default stub is provided
 * so existing callers that don't pass a lambda will still get a working factory
 * (the default simply returns true).
 */
class ScanViewModelFactory(
    private val context: Context,
    private val addToQueueLambda: suspend (Uri, Long) -> Boolean = { _, _ ->
        // Default stub implementation: mark all items as queued. Replace this
        // by passing a real lambda that enqueues to your ReviewQueue.
        withContext(Dispatchers.IO) { true }
    }
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ScanPhotosViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ScanPhotosViewModel(addToQueueLambda) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
