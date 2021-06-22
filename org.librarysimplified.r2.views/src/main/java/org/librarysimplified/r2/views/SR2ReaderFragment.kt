package org.librarysimplified.r2.views

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.common.base.Preconditions
import io.reactivex.disposables.Disposable
import org.librarysimplified.r2.api.SR2Bookmark
import org.librarysimplified.r2.api.SR2Bookmark.Type.EXPLICIT
import org.librarysimplified.r2.api.SR2Command
import org.librarysimplified.r2.api.SR2ControllerConfiguration
import org.librarysimplified.r2.api.SR2ControllerType
import org.librarysimplified.r2.api.SR2Event
import org.librarysimplified.r2.api.SR2Event.SR2BookmarkEvent.SR2BookmarkCreated
import org.librarysimplified.r2.api.SR2Event.SR2BookmarkEvent.SR2BookmarkDeleted
import org.librarysimplified.r2.api.SR2Event.SR2BookmarkEvent.SR2BookmarksLoaded
import org.librarysimplified.r2.api.SR2Event.SR2CommandEvent.SR2CommandEventCompleted.SR2CommandExecutionFailed
import org.librarysimplified.r2.api.SR2Event.SR2CommandEvent.SR2CommandEventCompleted.SR2CommandExecutionSucceeded
import org.librarysimplified.r2.api.SR2Event.SR2CommandEvent.SR2CommandExecutionRunningLong
import org.librarysimplified.r2.api.SR2Event.SR2CommandEvent.SR2CommandExecutionStarted
import org.librarysimplified.r2.api.SR2Event.SR2Error.SR2ChapterNonexistent
import org.librarysimplified.r2.api.SR2Event.SR2Error.SR2WebViewInaccessible
import org.librarysimplified.r2.api.SR2Event.SR2OnCenterTapped
import org.librarysimplified.r2.api.SR2Event.SR2ReadingPositionChanged
import org.librarysimplified.r2.api.SR2Event.SR2ThemeChanged
import org.librarysimplified.r2.api.SR2Locator
import org.librarysimplified.r2.api.SR2ScrollingMode.SCROLLING_MODE_DISABLED
import org.librarysimplified.r2.api.SR2ScrollingMode.SCROLLING_MODE_ENABLED
import org.librarysimplified.r2.api.SR2Theme
import org.librarysimplified.r2.ui_thread.SR2UIThread
import org.librarysimplified.r2.views.SR2ReaderViewEvent.SR2ReaderViewBookEvent.SR2BookLoadingFailed
import org.librarysimplified.r2.views.SR2ReaderViewEvent.SR2ReaderViewControllerEvent.SR2ControllerBecameAvailable
import org.librarysimplified.r2.views.SR2ReaderViewEvent.SR2ReaderViewNavigationEvent.SR2ReaderViewNavigationOpenTOC
import org.librarysimplified.r2.views.internal.SR2BrightnessService
import org.librarysimplified.r2.views.internal.SR2SettingsDialog
import org.librarysimplified.r2.views.internal.SR2ViewModelBookEvent
import org.librarysimplified.r2.views.internal.SR2ViewModelBookEvent.SR2ViewModelBookOpenFailed
import org.librarysimplified.r2.views.internal.SR2ViewModelBookEvent.SR2ViewModelBookOpened
import org.slf4j.LoggerFactory

class SR2ReaderFragment private constructor(
  private val parameters: SR2ReaderParameters
) : Fragment() {

  private val logger =
    LoggerFactory.getLogger(SR2ReaderFragment::class.java)

  companion object {
    fun create(parameters: SR2ReaderParameters): SR2ReaderFragment {
      return SR2ReaderFragment(parameters)
    }
  }

  private lateinit var container: ViewGroup
  private lateinit var loadingView: ProgressBar
  private lateinit var menuBookmarkItem: MenuItem
  private lateinit var positionPageView: TextView
  private lateinit var positionPercentView: TextView
  private lateinit var positionTitleView: TextView
  private lateinit var progressContainer: ViewGroup
  private lateinit var progressView: ProgressBar
  private lateinit var readerModel: SR2ReaderViewModel
  private lateinit var toolbar: Toolbar
  private lateinit var webView: WebView
  private var controller: SR2ControllerType? = null
  private var controllerEvents: Disposable? = null
  private var controllerLifecycleEvents: Disposable? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    this.setHasOptionsMenu(true)
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? {
    val view =
      inflater.inflate(R.layout.sr2_reader, container, false)

    this.container =
      view.findViewById(R.id.readerContainer)
    this.toolbar =
      view.findViewById(R.id.readerToolbar)
    this.progressContainer =
      view.findViewById(R.id.readerProgressContainer)
    this.webView =
      view.findViewById(R.id.readerWebView)
    this.progressView =
      view.findViewById(R.id.reader2_progress)
    this.positionPageView =
      view.findViewById(R.id.reader2_position_page)
    this.positionTitleView =
      view.findViewById(R.id.reader2_position_title)
    this.positionPercentView =
      view.findViewById(R.id.reader2_position_percent)
    this.loadingView =
      view.findViewById(R.id.readerLoading)

    this.toolbar.inflateMenu(R.menu.sr2_reader_menu)
    this.menuBookmarkItem = this.toolbar.menu.findItem(R.id.readerMenuAddBookmark)
    this.toolbar.menu.findItem(R.id.readerMenuSettings)
      .setOnMenuItemClickListener { this.onReaderMenuSettingsSelected() }
    this.toolbar.menu.findItem(R.id.readerMenuTOC)
      .setOnMenuItemClickListener { this.onReaderMenuTOCSelected() }
    this.toolbar.menu.findItem(R.id.readerMenuAddBookmark)
      .setOnMenuItemClickListener { this.onReaderMenuAddBookmarkSelected() }

    this.configureForTheme(this.controller?.themeNow() ?: this.parameters.theme)
    this.viewsShowLoading()
    return view
  }

  private fun onReaderMenuAddBookmarkSelected(): Boolean {
    SR2UIThread.checkIsUIThread()

    val controllerNow = this.controller
    if (controllerNow != null) {
      if (this.findBookmarkForCurrentPage(controllerNow, controllerNow.positionNow()) == null) {
        controllerNow.submitCommand(SR2Command.BookmarkCreate)
      }
    }
    return true
  }

  private fun onReaderMenuTOCSelected(): Boolean {
    SR2UIThread.checkIsUIThread()

    this.readerModel.publishViewEvent(SR2ReaderViewNavigationOpenTOC)
    return true
  }

  private fun onReaderMenuSettingsSelected(): Boolean {
    SR2UIThread.checkIsUIThread()

    this.openSettings()
    return true
  }

  private fun configureForTheme(theme: SR2Theme) {
    SR2UIThread.checkIsUIThread()

    val background = theme.colorScheme.background()
    val foreground = theme.colorScheme.foreground()

    this.container.setBackgroundColor(background)
    this.positionPageView.setTextColor(foreground)
    this.positionTitleView.setTextColor(foreground)
    this.positionPercentView.setTextColor(foreground)
  }

  private fun openSettings() {
    val activity = this.requireActivity()
    SR2SettingsDialog.create(
      brightness = SR2BrightnessService(activity),
      context = activity,
      controller = this.controller!!
    )
  }

  override fun onStart() {
    super.onStart()
    this.logger.debug("onStart")

    Preconditions.checkArgument(this.controller == null, "Controller must be null")

    val activity =
      this.requireActivity()

    this.readerModel =
      ViewModelProvider(activity, SR2ReaderViewModelFactory(this.parameters))
        .get(SR2ReaderViewModel::class.java)

    this.controllerEvents =
      this.readerModel.controllerEvents.subscribe(this::onControllerEvent)
    this.controllerLifecycleEvents =
      this.readerModel.bookEvents.subscribe(this::onControllerLifecycleEvent)

    /*
     * Fetch or create a controller with the given EPUB loaded into it.
     */

    this.readerModel.createOrGet(
      configuration = SR2ControllerConfiguration(
        bookFile = this.parameters.bookFile,
        bookId = this.parameters.bookId,
        context = activity,
        ioExecutor = this.readerModel.ioExecutor,
        streamer = this.parameters.streamer,
        theme = this.parameters.theme,
        uiExecutor = SR2UIThread::runOnUIThread
      )
    )

    this.showOrHideReadingUI(true)
  }

  override fun onStop() {
    super.onStop()
    this.logger.debug("onStop")

    this.controllerEvents?.dispose()
    this.controllerLifecycleEvents?.dispose()
    this.controller?.viewDisconnect()
    this.controller = null
  }

  private fun onReadingPositionChanged(event: SR2ReadingPositionChanged) {
    val context = this.context ?: return
    this.logger.debug("chapterTitle=${event.chapterTitle}")
    this.progressView.apply { this.max = 100; this.progress = event.bookProgressPercent }
    this.positionPageView.text = context.getString(R.string.progress_page, event.currentPage, event.pageCount)
    this.positionTitleView.text = event.chapterTitle
    this.positionPercentView.text = this.getString(R.string.progress_percent, event.bookProgressPercent)
    this.reconfigureBookmarkMenuItem(event.locator)
  }

  private fun reconfigureBookmarkMenuItem(currentPosition: SR2Locator) {
    SR2UIThread.checkIsUIThread()

    val controllerNow = this.controller
    if (controllerNow != null) {
      val bookmark = this.findBookmarkForCurrentPage(controllerNow, currentPosition)
      if (bookmark != null) {
        this.menuBookmarkItem.setIcon(R.drawable.sr2_bookmark_active)
      } else {
        this.menuBookmarkItem.setIcon(R.drawable.sr2_bookmark_inactive)
      }
    }
  }

  private fun findBookmarkForCurrentPage(
    controllerNow: SR2ControllerType,
    currentPosition: SR2Locator
  ): SR2Bookmark? {
    return controllerNow.bookmarksNow()
      .find { bookmark -> this.locationMatchesBookmark(bookmark, currentPosition) }
  }

  private fun locationMatchesBookmark(
    bookmark: SR2Bookmark,
    location: SR2Locator
  ): Boolean {
    return bookmark.type == EXPLICIT && location.compareTo(bookmark.locator) == 0
  }

  private fun onControllerLifecycleEvent(event: SR2ViewModelBookEvent) {
    SR2UIThread.checkIsUIThread()

    return when (event) {
      is SR2ViewModelBookOpened -> {
        val newController = event.reference.controller
        this.logger.debug(
          "SR2LifecycleControllerBecameAvailable: {} first startup {}",
          newController,
          event.reference.isFirstStartup
        )
        this.controller = newController
        newController.viewConnect(this.webView)
        this.toolbar.title = newController.bookMetadata.title
        this.readerModel.publishViewEvent(SR2ControllerBecameAvailable(event.reference))
      }

      is SR2ViewModelBookOpenFailed -> {
        this.logger.error("SR2LifecycleControllerFailedToOpen: ", event.exception)
        this.readerModel.publishViewEvent(SR2BookLoadingFailed(event.exception))
      }
    }
  }

  private fun onControllerEvent(event: SR2Event) {
    SR2UIThread.checkIsUIThread()

    return when (event) {
      is SR2ReadingPositionChanged -> {
        this.onReadingPositionChanged(event)
      }

      SR2BookmarksLoaded,
      is SR2BookmarkDeleted,
      is SR2BookmarkCreated -> {
        this.onBookmarksChanged()
      }

      is SR2ThemeChanged -> {
        this.configureForTheme(event.theme)
      }

      is SR2ChapterNonexistent,
      is SR2WebViewInaccessible -> {
        // Nothing
      }

      is SR2OnCenterTapped -> {
        this.showOrHideReadingUI(event.uiVisible)
      }

      is SR2CommandExecutionStarted -> {
        // Nothing
      }

      is SR2CommandExecutionRunningLong -> {
        this.viewsShowLoading()
      }

      is SR2CommandExecutionSucceeded,
      is SR2CommandExecutionFailed -> {
        this.viewsHideLoading()
      }

      is SR2Event.SR2ExternalLinkSelected -> {
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(event.link))
        this.startActivity(browserIntent)
      }

      is SR2Event.SR2ScrollingModeChanged -> {
        when (event.scrollingMode) {
          SCROLLING_MODE_ENABLED -> {
            this.positionPageView.visibility = View.INVISIBLE
          }
          SCROLLING_MODE_DISABLED -> {
            this.positionPageView.visibility = View.VISIBLE
          }
        }
      }
    }
  }

  private fun showOrHideReadingUI(uiVisible: Boolean) {
    SR2UIThread.checkIsUIThread()

    if (uiVisible) {
      this.progressContainer.visibility = View.VISIBLE
      this.toolbar.visibility = View.VISIBLE
    } else {
      this.progressContainer.visibility = View.GONE
      this.toolbar.visibility = View.GONE
    }
  }

  private fun viewsHideLoading() {
    SR2UIThread.checkIsUIThread()

    if (this.webView.visibility != View.VISIBLE) {
      this.webView.visibility = View.VISIBLE
    }
    if (this.loadingView.visibility != View.INVISIBLE) {
      this.loadingView.visibility = View.INVISIBLE
    }
  }

  private fun viewsShowLoading() {
    SR2UIThread.checkIsUIThread()

    if (this.webView.visibility != View.INVISIBLE) {
      this.webView.visibility = View.INVISIBLE
    }
    if (this.loadingView.visibility != View.VISIBLE) {
      this.loadingView.visibility = View.VISIBLE
    }
  }

  private fun onBookmarksChanged() {
    SR2UIThread.checkIsUIThread()

    val controllerNow = this.controller
    if (controllerNow != null) {
      this.reconfigureBookmarkMenuItem(controllerNow.positionNow())
    }
  }
}
