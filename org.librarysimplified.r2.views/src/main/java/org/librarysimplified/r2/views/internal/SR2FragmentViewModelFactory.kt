package org.librarysimplified.r2.views.internal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.librarysimplified.r2.views.SR2ReaderViewModel

internal class SR2FragmentViewModelFactory(
  private val activityModel: SR2ReaderViewModel
) : ViewModelProvider.Factory {

  override fun <T : ViewModel?> create(modelClass: Class<T>): T {
    return when {
      (modelClass.isAssignableFrom(SR2ReaderFragmentViewModel::class.java)) ->
        SR2ReaderFragmentViewModel(activityModel) as T
      (modelClass.isAssignableFrom(SR2TOCChaptersFragmentViewModel::class.java)) ->
        SR2TOCChaptersFragmentViewModel(activityModel) as T
      (modelClass.isAssignableFrom(SR2TOCBookmarksFragmentViewModel::class.java)) ->
        SR2TOCBookmarksFragmentViewModel(activityModel) as T
      else ->
        throw IllegalArgumentException("Cannot instantiate a value of type $modelClass")
    }
  }
}
