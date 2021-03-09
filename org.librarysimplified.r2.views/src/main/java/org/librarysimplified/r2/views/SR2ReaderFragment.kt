package org.librarysimplified.r2.views

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.UiThread
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import io.reactivex.disposables.Disposable
import org.librarysimplified.r2.api.SR2Bookmark
import org.librarysimplified.r2.api.SR2Bookmark.Type.EXPLICIT
import org.librarysimplified.r2.api.SR2ColorScheme
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
import org.librarysimplified.r2.api.SR2Theme
import org.librarysimplified.r2.ui_thread.SR2UIThread
import org.librarysimplified.r2.views.SR2ReaderViewEvent.SR2ReaderViewBookEvent.SR2BookLoadingFailed
import org.librarysimplified.r2.views.SR2ReaderViewEvent.SR2ReaderViewControllerEvent.SR2ControllerBecameAvailable
import org.librarysimplified.r2.views.SR2ReaderViewEvent.SR2ReaderViewNavigationEvent.SR2ReaderViewNavigationOpenTOC
import org.librarysimplified.r2.views.internal.SR2BrightnessService
import org.librarysimplified.r2.views.internal.SR2SettingsDialog
import org.slf4j.LoggerFactory
import java.util.concurrent.ExecutionException

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
  private lateinit var pageButtonPrevious: ImageButton
  private lateinit var pageButtons: ViewGroup
  private lateinit var pageButtonNext: ImageButton
  private lateinit var positionPageView: TextView
  private lateinit var positionPercentView: TextView
  private lateinit var positionTitleView: TextView
  private lateinit var progressContainer: ViewGroup
  private lateinit var progressView: ProgressBar
  private lateinit var readerModel: SR2ReaderViewModel
  private lateinit var toolbar: Toolbar
  private lateinit var webView: WebView
  private var controller: SR2ControllerType? = null
  private var controllerSubscription: Disposable? = null

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
    this.pageButtons =
      view.findViewById(R.id.readerPageButtons)
    this.pageButtonPrevious =
      view.findViewById(R.id.readerPagePrevious)
    this.pageButtonNext =
      view.findViewById(R.id.readerPageNext)

    this.toolbar.inflateMenu(R.menu.sr2_reader_menu)
    this.menuBookmarkItem = this.toolbar.menu.findItem(R.id.readerMenuAddBookmark)
    this.toolbar.menu.findItem(R.id.readerMenuSettings)
      .setOnMenuItemClickListener { this.onReaderMenuSettingsSelected() }
    this.toolbar.menu.findItem(R.id.readerMenuTOC)
      .setOnMenuItemClickListener { this.onReaderMenuTOCSelected() }
    this.toolbar.menu.findItem(R.id.readerMenuAddBookmark)
      .setOnMenuItemClickListener { this.onReaderMenuAddBookmarkSelected() }

    this.pageButtons.visibility = View.INVISIBLE

    this.configureForTheme(this.controller?.themeNow() ?: this.parameters.theme)
    this.viewsShowLoading()
    return view
  }

  private fun onReaderMenuAddBookmarkSelected(): Boolean {
    val controllerNow = this.controller
    if (controllerNow != null) {
      if (this.findBookmarkForCurrentPage(controllerNow, controllerNow.positionNow()) == null) {
        controllerNow.submitCommand(SR2Command.BookmarkCreate)
      }
    }
    return true
  }

  private fun onReaderMenuTOCSelected(): Boolean {
    this.readerModel.publishViewEvent(SR2ReaderViewNavigationOpenTOC)
    return true
  }

  private fun onReaderMenuSettingsSelected(): Boolean {
    this.openSettings()
    return true
  }

  @UiThread
  private fun configureForTheme(theme: SR2Theme) {
    val background = theme.colorScheme.background()
    val foreground = theme.colorScheme.foreground()

    this.container.setBackgroundColor(background)
    this.positionPageView.setTextColor(foreground)
    this.positionTitleView.setTextColor(foreground)
    this.positionPercentView.setTextColor(foreground)

    return when (theme.colorScheme) {
      SR2ColorScheme.DARK_TEXT_LIGHT_BACKGROUND -> {
        this.pageButtonPrevious.setBackgroundResource(R.drawable.sr2_button_white)
        this.pageButtonPrevious.setImageResource(R.drawable.sr2_go_prev_dark)
        this.pageButtonNext.setBackgroundResource(R.drawable.sr2_button_white)
        this.pageButtonNext.setImageResource(R.drawable.sr2_go_next_dark)
      }
      SR2ColorScheme.LIGHT_TEXT_DARK_BACKGROUND -> {
        this.pageButtonPrevious.setBackgroundResource(R.drawable.sr2_button_black)
        this.pageButtonPrevious.setImageResource(R.drawable.sr2_go_prev_light)
        this.pageButtonNext.setBackgroundResource(R.drawable.sr2_button_black)
        this.pageButtonNext.setImageResource(R.drawable.sr2_go_next_light)
      }
      SR2ColorScheme.DARK_TEXT_ON_SEPIA -> {
        this.pageButtonPrevious.setBackgroundResource(R.drawable.sr2_button_beige)
        this.pageButtonPrevious.setImageResource(R.drawable.sr2_go_prev_dark)
        this.pageButtonNext.setBackgroundResource(R.drawable.sr2_button_beige)
        this.pageButtonNext.setImageResource(R.drawable.sr2_go_next_dark)
      }
    }
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

    val activity =
      this.requireActivity()

    this.readerModel =
      ViewModelProvider(activity, SR2ReaderViewModelFactory(this.parameters))
        .get(SR2ReaderViewModel::class.java)

    /*
     * Fetch or create a controller with the given EPUB loaded into it.
     */

    val controllerFuture: ListenableFuture<SR2ControllerReference> =
      this.readerModel.createOrGet(
        configuration = SR2ControllerConfiguration(
          bookFile = this.parameters.bookFile,
          context = activity,
          ioExecutor = this.readerModel.ioExecutor,
          streamer = this.parameters.streamer,
          theme = this.parameters.theme,
          uiExecutor = SR2UIThread::runOnUIThread
        )
      )

    controllerFuture.addListener(
      {
        try {
          try {
            val ref = controllerFuture.get()
            this.onBookOpenSucceeded(ref.controller, ref.isFirstStartup)
          } catch (e: ExecutionException) {
            throw e.cause!!
          }
        } catch (e: Exception) {
          this.onBookOpenFailed(e)
        }
      },
      MoreExecutors.directExecutor()
    )

    this.showOrHideReadingUI(true)
  }

  override fun onStop() {
    super.onStop()
    this.controllerSubscription?.dispose()
    this.controller?.viewDisconnect()
  }

  private fun onBookOpenSucceeded(
    controller: SR2ControllerType,
    isFirstStartup: Boolean
  ) {
    this.logger.debug("onBookOpenSucceeded: first startup {}", isFirstStartup)
    SR2UIThread.runOnUIThread { this.onBookOpenSucceededUI(controller, isFirstStartup) }
  }

  @UiThread
  private fun onBookOpenSucceededUI(
    controller: SR2ControllerType,
    isFirstStartup: Boolean
  ) {
    this.controller = controller
    controller.viewConnect(this.webView)
    this.controllerSubscription = controller.events.subscribe(this::onControllerEvent)
    this.readerModel.publishViewEvent(
      SR2ControllerBecameAvailable(SR2ControllerReference(controller, isFirstStartup))
    )
    this.toolbar.title = controller.bookMetadata.title

    this.pageButtonNext.setOnClickListener {
      controller.submitCommand(SR2Command.OpenPageNext)
    }
    this.pageButtonPrevious.setOnClickListener {
      controller.submitCommand(SR2Command.OpenPagePrevious)
    }
  }

  private fun onBookOpenFailed(e: Throwable) {
    this.logger.error("onBookOpenFailed: ", e)
    this.readerModel.publishViewEvent(SR2BookLoadingFailed(e))
  }

  @UiThread
  private fun onReadingPositionChanged(event: SR2ReadingPositionChanged) {
    val context = this.context ?: return
    this.logger.debug("chapterTitle=${event.chapterTitle}")
    this.progressView.apply { this.max = 100; this.progress = event.bookProgressPercent }
    this.positionPageView.text = context.getString(R.string.progress_page, event.currentPage, event.pageCount)
    this.positionTitleView.text = event.chapterTitle
    this.positionPercentView.text = this.getString(R.string.progress_percent, event.bookProgressPercent)
    this.reconfigureBookmarkMenuItem(event.locator)
  }

  @UiThread
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

  private fun onControllerEvent(event: SR2Event) {
    return when (event) {
      is SR2ReadingPositionChanged -> {
        SR2UIThread.runOnUIThread {
          if (!this.isDetached) {
            this.onReadingPositionChanged(event)
          }
        }
      }

      SR2BookmarksLoaded,
      is SR2BookmarkDeleted,
      is SR2BookmarkCreated -> {
        this.onBookmarksChanged()
      }

      is SR2ThemeChanged -> {
        SR2UIThread.runOnUIThread {
          if (!this.isDetached) {
            this.configureForTheme(event.theme)
          }
        }
      }

      is SR2ChapterNonexistent,
      is SR2WebViewInaccessible -> {
        // Nothing
      }

      is SR2OnCenterTapped -> {
        SR2UIThread.runOnUIThread {
          if (!this.isDetached) {
            this.showOrHideReadingUI(event.uiVisible)
          }
        }
      }

      is SR2CommandExecutionStarted -> {
        // Nothing
      }

      is SR2CommandExecutionRunningLong -> {
        SR2UIThread.runOnUIThread {
          if (!this.isDetached) {
            this.viewsShowLoading()
          }
        }
      }

      is SR2CommandExecutionSucceeded,
      is SR2CommandExecutionFailed -> {
        SR2UIThread.runOnUIThread {
          if (!this.isDetached) {
            this.viewsHideLoading()
          }
        }
      }
    }
  }

  @UiThread
  private fun showOrHideReadingUI(uiVisible: Boolean) {
    if (uiVisible) {
      this.progressContainer.visibility = View.VISIBLE
      this.toolbar.visibility = View.VISIBLE
    } else {
      this.progressContainer.visibility = View.GONE
      this.toolbar.visibility = View.GONE
    }
  }

  @UiThread
  private fun viewsHideLoading() {
    if (this.webView.visibility != View.VISIBLE) {
      this.webView.visibility = View.VISIBLE
    }
    if (this.pageButtons.visibility != View.VISIBLE) {
      this.pageButtons.visibility = View.VISIBLE
    }
    if (this.loadingView.visibility != View.INVISIBLE) {
      this.loadingView.visibility = View.INVISIBLE
    }
  }

  @UiThread
  private fun viewsShowLoading() {
    if (this.webView.visibility != View.INVISIBLE) {
      this.webView.visibility = View.INVISIBLE
    }
    if (this.pageButtons.visibility != View.INVISIBLE) {
      this.pageButtons.visibility = View.INVISIBLE
    }
    if (this.loadingView.visibility != View.VISIBLE) {
      this.loadingView.visibility = View.VISIBLE
    }
  }

  private fun onBookmarksChanged() {
    SR2UIThread.runOnUIThread {
      val controllerNow = this.controller
      if (controllerNow != null) {
        if (!this.isDetached) {
          this.reconfigureBookmarkMenuItem(controllerNow.positionNow())
        }
      }
    }
  }
}
