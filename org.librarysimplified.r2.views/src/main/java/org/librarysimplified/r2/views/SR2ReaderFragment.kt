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
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import io.reactivex.disposables.Disposable
import org.librarysimplified.r2.api.SR2Bookmark
import org.librarysimplified.r2.api.SR2Bookmark.Type.EXPLICIT
import org.librarysimplified.r2.api.SR2Command
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
import org.librarysimplified.r2.api.SR2ReadingPosition
import org.librarysimplified.r2.api.SR2ScrollingMode.SCROLLING_MODE_CONTINUOUS
import org.librarysimplified.r2.api.SR2ScrollingMode.SCROLLING_MODE_PAGINATED
import org.librarysimplified.r2.api.SR2Theme
import org.librarysimplified.r2.ui_thread.SR2UIThreadService
import org.librarysimplified.r2.views.internal.SR2BrightnessService
import org.librarysimplified.r2.views.internal.SR2FragmentViewModelFactory
import org.librarysimplified.r2.views.internal.SR2ReaderFragmentViewModel
import org.librarysimplified.r2.views.internal.SR2SettingsDialog
import org.librarysimplified.r2.views.internal.SR2ViewModelBookEvent
import org.slf4j.LoggerFactory

class SR2ReaderFragment private constructor(
  private val parameters: SR2ReaderParameters
) : Fragment() {

  companion object {
    fun create(parameters: SR2ReaderParameters): SR2ReaderFragment {
      return SR2ReaderFragment(parameters)
    }
  }

  private val logger =
    LoggerFactory.getLogger(SR2ReaderFragment::class.java)

  private val viewModel: SR2ReaderFragmentViewModel by viewModels(
    factoryProducer = {
      val actFactory = { SR2ReaderViewModelFactory(requireActivity().application, this.parameters) }
      val actModel: SR2ReaderViewModel by activityViewModels(factoryProducer = actFactory)
      SR2FragmentViewModelFactory(actModel)
    }
  )

  private var bookEvents: Disposable? = null
  private var controllerEvents: Disposable? = null
  private var controller: SR2ControllerType? = null

  private lateinit var container: ViewGroup
  private lateinit var loadingView: ProgressBar
  private lateinit var menuBookmarkItem: MenuItem
  private lateinit var positionPageView: TextView
  private lateinit var positionPercentView: TextView
  private lateinit var positionTitleView: TextView
  private lateinit var progressContainer: ViewGroup
  private lateinit var progressView: ProgressBar
  private lateinit var toolbar: Toolbar
  private lateinit var webView: WebView

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    this.setHasOptionsMenu(true)
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    return inflater.inflate(R.layout.sr2_reader, container, false)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

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

    /*
     * We don't show page numbers in continuous scroll mode.
     */

    when (this.parameters.scrollingMode) {
      SCROLLING_MODE_PAGINATED -> {
        // The defaults are fine
      }
      SCROLLING_MODE_CONTINUOUS -> {
        this.positionPageView.visibility = View.INVISIBLE
      }
    }

    /*
     * The last book event is received at each subscription, that is every time
     * the View is created. The UI will be configured in the handler.
     */
    this.bookEvents = this.viewModel.bookEvents.subscribe(this::onBookEvents)

    this.configureViewsLoading()
  }

  private fun onReaderMenuAddBookmarkSelected(): Boolean {
    SR2UIThreadService.checkIsUIThread()

    val controllerNow = this.controller
    if (controllerNow != null) {
      if (this.findBookmarkForCurrentPage(controllerNow, controllerNow.positionNow().locator) == null) {
        controllerNow.submitCommand(SR2Command.BookmarkCreate)
      }
    }
    return true
  }

  private fun onReaderMenuTOCSelected(): Boolean {
    SR2UIThreadService.checkIsUIThread()

    this.viewModel.openTOC()
    return true
  }

  private fun onReaderMenuSettingsSelected(): Boolean {
    SR2UIThreadService.checkIsUIThread()

    this.openSettings()
    return true
  }

  private fun openSettings() {
    val activity = this.requireActivity()
    val controllerNow = this.controller
    if (controllerNow != null) {
      SR2SettingsDialog(
        brightness = SR2BrightnessService(activity),
        context = activity,
        controller = controllerNow
      ).show()
    }
  }

  override fun onStart() {
    super.onStart()
    this.logger.debug("onStart")

    this.controllerEvents =
      this.viewModel.controllerEvents.subscribe(this::onControllerEvent)

    this.showOrHideReadingUI(true)
  }

  override fun onStop() {
    super.onStop()
    this.logger.debug("onStop")

    this.controllerEvents?.dispose()
  }

  override fun onDestroyView() {
    super.onDestroyView()
    this.logger.debug("onDestroyView")

    this.bookEvents?.dispose()
    this.controller?.viewDisconnect()
  }

  private fun onBookEvents(event: SR2ViewModelBookEvent) {
    SR2UIThreadService.checkIsUIThread()

    return when (event) {
      is SR2ViewModelBookEvent.SR2ViewModelBookOpenFailed -> {
        // Nothing to do
      }
      is SR2ViewModelBookEvent.SR2ViewModelBookOpened -> {
        this.configureUiWithController(event.controller)
        this.controller = event.controller
      }
    }
  }

  private fun configureUiWithController(controller: SR2ControllerType) {
    controller.viewConnect(this.webView)
    controller.submitCommand(SR2Command.Refresh)
    this.toolbar.title = controller.bookMetadata.title
    this.configureForTheme(controller.themeNow())
    this.configureForPosition(controller.positionNow())
    this.configureViewsLoading()
  }

  private fun configureForTheme(theme: SR2Theme) {
    SR2UIThreadService.checkIsUIThread()

    val background = theme.colorScheme.background()
    val foreground = theme.colorScheme.foreground()

    this.container.setBackgroundColor(background)
    this.positionPageView.setTextColor(foreground)
    this.positionTitleView.setTextColor(foreground)
    this.positionPercentView.setTextColor(foreground)
  }

  private fun configureForPosition(position: SR2ReadingPosition) {
    this.logger.debug("chapterTitle=${position.chapterTitle}")
    if (position.chapterTitle == null) {
      this.positionTitleView.visibility = View.GONE
    } else {
      this.positionTitleView.text = position.chapterTitle
      this.positionTitleView.visibility = View.VISIBLE
    }

    if (position.currentPage == null || position.pageCount == null) {
      this.positionPageView.visibility = View.GONE
    } else {
      this.positionPageView.text = requireContext()
        .getString(R.string.progress_page, position.currentPage, position.pageCount)
      this.positionPageView.visibility = View.VISIBLE
    }

    val bookProgressPercent = position.bookProgressPercent
    if (bookProgressPercent == null) {
      this.positionPercentView.visibility = View.GONE
      this.progressView.visibility = View.GONE
    } else {
      this.positionPercentView.text = this.getString(R.string.progress_percent, bookProgressPercent)
      this.progressView.apply { this.max = 100; this.progress = bookProgressPercent }
      this.positionPercentView.visibility = View.VISIBLE
      this.progressView.visibility = View.VISIBLE
    }
    this.reconfigureBookmarkMenuItem(position.locator)
  }

  private fun reconfigureBookmarkMenuItem(currentPosition: SR2Locator) {
    SR2UIThreadService.checkIsUIThread()

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
    SR2UIThreadService.checkIsUIThread()

    return when (event) {
      is SR2ReadingPositionChanged -> {
        this.configureForPosition(event.position)
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

      is SR2CommandExecutionStarted,
      is SR2CommandExecutionRunningLong,
      is SR2CommandExecutionSucceeded,
      is SR2CommandExecutionFailed -> {
        this.configureViewsLoading()
      }

      is SR2Event.SR2ExternalLinkSelected -> {
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(event.link))
        this.startActivity(browserIntent)
      }
    }
  }

  private fun onBookmarksChanged() {
    SR2UIThreadService.checkIsUIThread()

    val controllerNow = this.controller
    if (controllerNow != null) {
      this.reconfigureBookmarkMenuItem(controllerNow.positionNow().locator)
    }
  }

  private fun showOrHideReadingUI(uiVisible: Boolean) {
    if (uiVisible) {
      this.progressContainer.visibility = View.VISIBLE
      this.toolbar.visibility = View.VISIBLE
    } else {
      this.progressContainer.visibility = View.GONE
      this.toolbar.visibility = View.GONE
    }
  }

  private fun configureViewsLoading() {
    val loading = this.controller?.longRunningCommandNow() ?: true
    if (loading) {
      this.viewsShowLoading()
    } else {
      this.viewsHideLoading()
    }
  }

  private fun viewsHideLoading() {
    if (this.webView.visibility != View.VISIBLE) {
      this.webView.visibility = View.VISIBLE
    }
    if (this.loadingView.visibility != View.INVISIBLE) {
      this.loadingView.visibility = View.INVISIBLE
    }
  }

  private fun viewsShowLoading() {
    if (this.webView.visibility != View.INVISIBLE) {
      this.webView.visibility = View.INVISIBLE
    }
    if (this.loadingView.visibility != View.VISIBLE) {
      this.loadingView.visibility = View.VISIBLE
    }
  }
}
