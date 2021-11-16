package org.librarysimplified.r2.views.internal

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.reactivex.disposables.CompositeDisposable
import org.librarysimplified.r2.api.SR2Bookmark
import org.librarysimplified.r2.api.SR2Command
import org.librarysimplified.r2.api.SR2ControllerType
import org.librarysimplified.r2.api.SR2Event
import org.librarysimplified.r2.views.SR2ReaderViewModel

internal class SR2TOCBookmarksFragmentViewModel(
  private val activityModel: SR2ReaderViewModel
) : ViewModel() {

  private var controller: SR2ControllerType? =
    null

  private val bookmarksMutable: MutableLiveData<List<SR2Bookmark>> =
    MutableLiveData(emptyList())

  private fun requireController(): SR2ControllerType =
    checkNotNull(this.controller)

  private val subscriptions: CompositeDisposable =
    CompositeDisposable(
      this.activityModel.bookEvents
        .subscribe(this::onViewModelEvent),
      this.activityModel.controllerEvents
        .ofType(SR2Event.SR2BookmarkEvent::class.java)
        .subscribe(this::onBookmarkEvent)
    )

  private fun onViewModelEvent(event: SR2ViewModelBookEvent) {
    return when (event) {
      is SR2ViewModelBookEvent.SR2ViewModelBookOpenFailed -> {
        // Nothing to do
      }
      is SR2ViewModelBookEvent.SR2ViewModelBookOpened -> {
        val controller = event.controller
        this.controller = controller
        this.reloadBookmarks()
      }
    }
  }

  private fun onBookmarkEvent(event: SR2Event.SR2BookmarkEvent) {
    this.reloadBookmarks()
  }

  private fun reloadBookmarks() {
    val bookmarksNow = this.requireController().bookmarksNow()
    this.bookmarksMutable.postValue(bookmarksNow)
  }

  val bookmarks: LiveData<List<SR2Bookmark>>
    get() = this.bookmarksMutable

  override fun onCleared() {
    super.onCleared()
    this.subscriptions.clear()
  }

  fun openBookmark(bookmark: SR2Bookmark) {
    this.requireController().submitCommand(SR2Command.OpenChapter(bookmark.locator))
  }

  fun deleteBookmark(bookmark: SR2Bookmark) {
    this.requireController().submitCommand(SR2Command.BookmarkDelete(bookmark))
  }

  fun closeToc() {
    this.activityModel.closeTOC()
  }
}
