package org.librarysimplified.r2.vanilla

import android.webkit.WebView
import androidx.annotation.IntRange
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import org.librarysimplified.r2.api.SR2Command
import org.librarysimplified.r2.api.SR2ControllerConfiguration
import org.librarysimplified.r2.api.SR2ControllerType
import org.librarysimplified.r2.api.SR2Event
import org.librarysimplified.r2.api.SR2Event.SR2Error.SR2ChapterNonexistent
import org.librarysimplified.r2.api.SR2Event.SR2Error.SR2WebViewInaccessible
import org.readium.r2.shared.Publication
import org.readium.r2.streamer.container.Container
import org.readium.r2.streamer.parser.EpubParser
import org.readium.r2.streamer.server.Server
import org.slf4j.LoggerFactory
import java.io.IOException
import java.lang.IllegalArgumentException
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import javax.annotation.concurrent.GuardedBy
import kotlin.math.round
import kotlin.math.roundToInt

/**
 * The default R2 controller implementation.
 */

class SR2Controller private constructor(
  private val configuration: SR2ControllerConfiguration,
  private val port: Int,
  private val server: Server,
  private val publication: Publication,
  private val container: Container,
  private val epubFileName: String
) : SR2ControllerType {

  companion object {

    private val logger =
      LoggerFactory.getLogger(SR2Controller::class.java)

    /**
     * Find a high-numbered port upon which to run the internal server. Tries up to ten
     * times to find a port and then gives up with an exception if it can't.
     */

    private fun fetchUnusedHTTPPort(): Int {
      for (i in 0 until 10) {
        try {
          val socket = ServerSocket(0)
          val port = socket.localPort
          socket.close()
          return port
        } catch (e: IOException) {
          this.logger.error("failed to open port: ", e)
        }

        try {
          Thread.sleep(1_000L)
        } catch (e: InterruptedException) {
          Thread.currentThread().interrupt()
        }
      }

      throw IOException("Unable to find an unused port for the server")
    }

    /**
     * Create a new controller based on the given configuration.
     */

    fun create(
      configuration: SR2ControllerConfiguration
    ): SR2ControllerType {
      val bookFile = configuration.bookFile
      this.logger.debug("creating controller for {}", bookFile)

      val box =
        EpubParser().parse(bookFile.absolutePath)
          ?: throw IOException("Failed to parse EPUB")

      this.logger.debug("publication uri: {}", box.publication.baseUrl())
      this.logger.debug("publication title: {}", box.publication.metadata.title)
      val port = this.fetchUnusedHTTPPort()
      this.logger.debug("server port: {}", port)

      val server = Server(port)
      this.logger.debug("starting server")
      server.start(5_000)

      this.logger.debug("loading readium resources")
      server.loadReadiumCSSResources(configuration.context.assets)
      server.loadR2ScriptResources(configuration.context.assets)
      server.loadR2FontResources(configuration.context.assets, configuration.context)

      this.logger.debug("loading epub into server")
      val epubName = "/${bookFile.name}"
      server.addEpub(
        publication = box.publication,
        container = box.container,
        fileName = epubName,
        userPropertiesPath = null
      )

      this.logger.debug("server ready")
      return SR2Controller(
        configuration = configuration,
        container = box.container,
        epubFileName = epubName,
        port = port,
        publication = box.publication,
        server = server
      )
    }
  }

  private val logger =
    LoggerFactory.getLogger(SR2Controller::class.java)

  /*
   * A single threaded command executor. The purpose of this executor is to accept
   * commands from multiple threads and ensure that the commands are executed serially.
   */

  private val queueExecutor =
    Executors.newSingleThreadExecutor { runnable ->
      val thread = Thread(runnable)
      thread.name = "org.librarysimplified.r2.vanilla.commandQueue"
      thread
    }

  private val closed = AtomicBoolean(false)

  private val webViewConnectionLock = Any()
  @GuardedBy("webViewConnectionLock")
  private var webViewConnection: SR2WebViewConnection? = null

  @Volatile
  private var currentChapterIndex = 0

  private val eventSubject: PublishSubject<SR2Event> =
    PublishSubject.create()

  private fun locationOfSpineItem(
    index: Int
  ): String {
    require(index < this.publication.readingOrder.size) {
      "index must be in [0, ${this.publication.readingOrder.size}]; was $index"
    }

    return buildString {
      this.append("http://127.0.0.1:")
      this.append(this@SR2Controller.port)
      this.append(this@SR2Controller.epubFileName)

      val publication = this@SR2Controller.publication
      this.append(
        publication.readingOrder[index].href
          ?: throw IllegalStateException("Link to chapter $index is not present")
      )
    }
  }

  private fun setCurrentChapter(index: Int) {
    val title = this.publication.readingOrder[index].title
    this.logger.debug("current chapter $index, $title")
    this.currentChapterIndex = index
  }

  private fun executeCommand(command: SR2Command) {
    this.logger.debug("executing {}", command)

    return when (command) {
      is SR2Command.OpenChapter ->
        this.executeCommandOpenChapter(command)
      SR2Command.OpenPageNext ->
        this.executeCommandOpenPageNext()
      SR2Command.OpenChapterNext ->
        this.executeCommandOpenChapterNext()
      SR2Command.OpenPagePrevious ->
        this.executeCommandOpenPagePrevious()
      is SR2Command.OpenChapterPrevious ->
        this.executeCommandOpenChapterPrevious(command)
    }
  }

  private fun executeCommandOpenChapterPrevious(command: SR2Command.OpenChapterPrevious) {
    this.openChapterIndex(Math.max(0, this.currentChapterIndex - 1), atEnd = true)
  }

  private fun executeCommandOpenChapterNext() {
    this.openChapterIndex(this.currentChapterIndex + 1, atEnd = false)
  }

  private fun executeWithWebView(
    exec: (SR2WebViewConnection) -> Unit
  ) {
    val webViewRef =
      synchronized(this.webViewConnectionLock) { this.webViewConnection }

    if (webViewRef != null) {
      this.configuration.uiExecutor.invoke { exec.invoke(webViewRef) }
    } else {
      this.eventSubject.onNext(SR2WebViewInaccessible("No web view is connected"))
    }
  }

  private fun executeCommandOpenPagePrevious() {
    this.executeWithWebView { webViewConnection -> webViewConnection.jsAPI.openPagePrevious() }
  }

  private fun executeCommandOpenPageNext() {
    this.executeWithWebView { webViewConnection -> webViewConnection.jsAPI.openPageNext() }
  }

  private fun executeCommandOpenChapter(command: SR2Command.OpenChapter) {
    this.openChapterIndex(command.chapterIndex, atEnd = false)
  }

  private fun openChapterIndex(
    targetIndex: Int,
    atEnd: Boolean
  ) {
    val previousIndex = this.currentChapterIndex

    try {
      val location = this.locationOfSpineItem(targetIndex)
      this.logger.debug("opening location {}", location)

      // Warning: The current chapter must be set before loading the spine location. The
      // page will send a reading position changed event immediately on load, but before our
      // callback returns. If we don't update the chapter index first we'll report the wrong
      // chapter location in our SR2ReadingPositionChanged event. This is fragile and should
      // be fixed later.
      this.setCurrentChapter(targetIndex)

      this.openURL(
        location = location,
        onLoad = { webViewConnection ->
          if (atEnd) {
            webViewConnection.jsAPI.openPageLast()
          }
        }
      )
    } catch (e: Exception) {
      this.logger.error("unable to open chapter $targetIndex: ", e)
      this.setCurrentChapter(previousIndex)
      this.eventSubject.onNext(
        SR2ChapterNonexistent(
          chapterIndex = targetIndex,
          message = e.message ?: "Unable to open chapter $targetIndex"
        )
      )
    }
  }

  private fun openURL(
    location: String,
    onLoad: (SR2WebViewConnection) -> Unit
  ) {
    this.executeWithWebView { webViewConnection ->
      webViewConnection.openURL(location) {
        onLoad.invoke(webViewConnection)
      }
    }
  }

  /** Return the title of the current chapter. */

  private fun getChapterTitle(): String? {
    val chapter = this.publication.readingOrder[this.currentChapterIndex]

    // The title is actually part of the table of contents; however, there may not be a
    // one-to-one mapping between chapters and table of contents entries. We do a lookup
    // based on the chapter href.

    return this.publication.tableOfContents.firstOrNull {
      it.href == chapter.href
    }?.title
  }

  /**
   * Return approximate book progress as percent completed.
   *
   * @param chapterProgress [0 - 1]
   */

  private fun getPercentComplete(chapterProgress: Double): Int {
    require(chapterProgress > 1 || chapterProgress < 0) {
      "progress must be in [0, 1]; was $chapterProgress"
    }

    val chapterCount = this.publication.readingOrder.size
    val currentIndex = this.currentChapterIndex
    val result = ((currentIndex + 1 * chapterProgress) / chapterCount)
    logger.debug("$result = ($currentIndex + 1 * $chapterProgress) / $chapterCount")
    return (result * 100).toInt()
  }

  /**
   * A receiver that accepts calls from the Javascript code running inside the current
   * WebView.
   */

  private inner class JavascriptAPIReceiver : SR2JavascriptAPIReceiverType {

    private val logger =
      LoggerFactory.getLogger(JavascriptAPIReceiver::class.java)

    @android.webkit.JavascriptInterface
    override fun onReadingPositionChanged(currentPage: Int, pageCount: Int) {
      val chapterIndex = this@SR2Controller.currentChapterIndex
      val chapterProgress = currentPage.toDouble() / pageCount.toDouble()

      this.logger.debug(
        "onReadingPositionChanged:"
            + " chapterIndex=$chapterIndex,"
            + " currentPage=$currentPage,"
            + " pageCount=$pageCount,"
      )

      this@SR2Controller.eventSubject.onNext(
        SR2Event.SR2ReadingPositionChanged(
          chapterIndex = chapterIndex,
          chapterTitle = getChapterTitle(),
          currentPage = currentPage,
          pageCount = pageCount,
          percent = getPercentComplete(chapterProgress)
        )
      )
    }

    @android.webkit.JavascriptInterface
    override fun onCenterTapped() {
      this.logger.debug("onCenterTapped")
    }

    @android.webkit.JavascriptInterface
    override fun onClicked() {
      this.logger.debug("onClicked")
    }

    @android.webkit.JavascriptInterface
    override fun onLeftTapped() {
      this.logger.debug("onLeftTapped")
      this@SR2Controller.submitCommand(SR2Command.OpenPagePrevious)
    }

    @android.webkit.JavascriptInterface
    override fun onRightTapped() {
      this.logger.debug("onRightTapped")
      this@SR2Controller.submitCommand(SR2Command.OpenPageNext)
    }
  }

  override val events: Observable<SR2Event> =
    this.eventSubject

  override fun submitCommand(command: SR2Command) {
    this.logger.debug("submitCommand {}", command)
    this.queueExecutor.execute {
      this.executeCommand(command)
    }
  }

  override fun viewConnect(webView: WebView) {
    synchronized(this.webViewConnectionLock) {
      this.webViewConnection = SR2WebViewConnection.create(
        webView = webView,
        jsReceiver = this.JavascriptAPIReceiver(),
        commandQueue = this
      )
    }
  }

  override fun viewDisconnect() {
    synchronized(this.webViewConnectionLock) {
      this.webViewConnection = null
    }
  }

  override fun close() {
    if (this.closed.compareAndSet(false, true)) {
      try {
        this.viewDisconnect()
      } catch (e: Exception) {
        this.logger.error("could not disconnect view: ", e)
      }

      try {
        this.server.closeAllConnections()
      } catch (e: Exception) {
        this.logger.error("could not close connections: ", e)
      }

      try {
        this.server.stop()
      } catch (e: Exception) {
        this.logger.error("could not stop server: ", e)
      }

      try {
        this.queueExecutor.shutdown()
      } catch (e: Exception) {
        this.logger.error("could not stop command queue: ", e)
      }

      try {
        this.eventSubject.onComplete()
      } catch (e: Exception) {
        this.logger.error("could not complete event stream: ", e)
      }
    }
  }
}
