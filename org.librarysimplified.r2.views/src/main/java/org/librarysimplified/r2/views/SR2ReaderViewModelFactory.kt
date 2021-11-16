package org.librarysimplified.r2.views

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class SR2ReaderViewModelFactory constructor(
  private val application: Application,
  private val parameters: SR2ReaderParameters
) : ViewModelProvider.Factory {

  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    return if (modelClass.isAssignableFrom(SR2ReaderViewModel::class.java)) {
      SR2ReaderViewModel(application, this.parameters) as T
    } else {
      throw IllegalArgumentException("Cannot instantiate a value of type $modelClass")
    }
  }
}
