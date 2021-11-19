package org.librarysimplified.r2.views.internal

import androidx.lifecycle.ViewModel
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import org.librarysimplified.r2.api.SR2Command
import org.librarysimplified.r2.api.SR2ControllerType
import org.librarysimplified.r2.api.SR2Locator
import org.librarysimplified.r2.api.SR2TOCEntry
import org.librarysimplified.r2.views.SR2ReaderViewModel

internal class SR2TOCChaptersFragmentViewModel(
  private val activityModel: SR2ReaderViewModel
) : ViewModel() {

  private var controller: SR2ControllerType? = null

  private val subscriptions: CompositeDisposable =
    CompositeDisposable(
      this.activityModel.bookEvents
        .subscribe(this::onViewModelEvent)
    )

  private fun requireController(): SR2ControllerType =
    checkNotNull(this.controller)

  private fun onViewModelEvent(event: SR2ViewModelBookEvent) {
    return when (event) {
      is SR2ViewModelBookEvent.SR2ViewModelBookOpenFailed -> {
        // Nothing to do
      }
      is SR2ViewModelBookEvent.SR2ViewModelBookOpened -> {
        this.controller = event.controller
      }
    }
  }

  val bookEvents: Observable<SR2ViewModelBookEvent> =
    this.activityModel.bookEvents.observeOn(AndroidSchedulers.mainThread())

  override fun onCleared() {
    super.onCleared()
    this.subscriptions.clear()
  }

  fun openTocEntry(entry: SR2TOCEntry) {
    this.requireController().submitCommand(
      SR2Command.OpenChapter(
        SR2Locator.SR2LocatorPercent(
          chapterHref = entry.href,
          chapterProgress = 0.0
        )
      )
    )
  }

  fun closeToc() {
    this.activityModel.closeTOC()
  }
}
