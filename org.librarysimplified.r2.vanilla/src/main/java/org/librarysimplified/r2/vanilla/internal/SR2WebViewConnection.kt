package org.librarysimplified.r2.vanilla.internal

import android.view.MotionEvent
import android.webkit.WebView
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import org.librarysimplified.r2.api.SR2ControllerCommandQueueType
import org.librarysimplified.r2.api.SR2ScrollingMode
import org.librarysimplified.r2.api.SR2ScrollingMode.SCROLLING_MODE_CONTINUOUS
import org.librarysimplified.r2.api.SR2ScrollingMode.SCROLLING_MODE_PAGINATED
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * A connection to a web view.
 */

internal class SR2WebViewConnection(
  private val jsAPI: SR2JavascriptAPI,
  private val webView: WebView,
  private val requestQueue: ExecutorService,
  private val uiExecutor: (f: () -> Unit) -> Unit,
  private val commandQueue: SR2ControllerCommandQueueType
) : SR2WebViewConnectionType {

  private val logger =
    LoggerFactory.getLogger(SR2WebViewConnection::class.java)

  companion object {

    fun create(
      webView: WebView,
      jsReceiver: SR2JavascriptAPIReceiverType,
      uiExecutor: (f: () -> Unit) -> Unit,
      commandQueue: SR2ControllerCommandQueueType,
      scrollingMode: SR2ScrollingMode
    ): SR2WebViewConnectionType {

      val threadFactory = ThreadFactory { runnable ->
        val thread = Thread(runnable)
        thread.name = "org.librarysimplified.r2.vanilla.WebViewConnection[${thread.id}]"
        return@ThreadFactory thread
      }

      val requestQueue = Executors.newFixedThreadPool(1, threadFactory)
      val webChromeClient = SR2WebChromeClient()
      webView.webChromeClient = webChromeClient
      webView.settings.javaScriptEnabled = true

      /*
       * Allow the web view to obey <meta> viewport elements in the <head> section of
       * documents. This is required for rendering fixed-layout EPUB files.
       *
       * @see "https://www.w3.org/publishing/epub/epub-contentdocs.html#sec-fixed-layouts"
       */

      webView.settings.loadWithOverviewMode = true
      webView.settings.useWideViewPort = true

      when (scrollingMode) {
        SCROLLING_MODE_PAGINATED -> {
          /*
           * Disable manual scrolling on the web view. Scrolling is controlled via the javascript API.
           */

          webView.setOnTouchListener { v, event -> event.action == MotionEvent.ACTION_MOVE }
          webView.isVerticalScrollBarEnabled = false
          webView.isHorizontalScrollBarEnabled = false
        }
        SCROLLING_MODE_CONTINUOUS -> {
          webView.isVerticalScrollBarEnabled = true
          webView.isHorizontalScrollBarEnabled = false
        }
      }

      webView.addJavascriptInterface(jsReceiver, "Android")

      return SR2WebViewConnection(
        jsAPI = SR2JavascriptAPI(webView, commandQueue),
        webView = webView,
        requestQueue = requestQueue,
        uiExecutor = uiExecutor,
        commandQueue = commandQueue
      )
    }
  }

  override fun openURL(
    location: String
  ): ListenableFuture<*> {
    val id = UUID.randomUUID()
    val future = SettableFuture.create<Unit>()

    /*
     * Execute a request to the web view on the UI thread, and wait for that request to
     * complete on the background thread we're using for web view connections. Because
     * the background thread executor is a single thread, this has the effect of serializing
     * and queueing requests made to the web view. We can effectively wait on the [Future]
     * that will be set by the web view on completion without having to block the UI thread
     * waiting for the request to complete.
     */

    this.requestQueue.execute {
      this.logger.debug("[{}]: openURL {}", id, location)
      this.uiExecutor.invoke {
        this.webView.webViewClient = SR2WebViewClient(location, future, this.commandQueue)
        this.webView.loadUrl(location)
      }
      this.waitOrFail(id, future)
    }
    return future
  }

  /**
   * Wait up to a minute. If nothing is happening after a minute, something is seriously
   * broken and we should propagate an error via the [Future].
   */

  private fun <T> waitOrFail(
    id: UUID,
    future: SettableFuture<T>
  ) {
    try {
      this.logger.debug("[{}]: waiting for request to complete", id)
      future.get(1L, TimeUnit.MINUTES)
      this.logger.debug("[{}]: request completed", id)
    } catch (e: TimeoutException) {
      this.logger.error("[{}]: timed out waiting for the web view to complete: ", id, e)
      future.setException(e)
    } catch (e: Exception) {
      this.logger.error("[{}]: future failed: ", id, e)
      future.setException(e)
    }
  }

  override fun executeJS(
    function: (SR2JavascriptAPIType) -> ListenableFuture<*>
  ): ListenableFuture<Any> {
    val id = UUID.randomUUID()
    val future = SettableFuture.create<Any>()

    /*
     * Execute a request to the web view on the UI thread, and wait for that request to
     * complete on the background thread we're using for web view connections. Because
     * the background thread executor is a single thread, this has the effect of serializing
     * and queueing requests made to the web view. We can effectively wait on the [Future]
     * that will be set by the web view on completion without having to block the UI thread
     * waiting for the request to complete.
     */

    this.requestQueue.execute {
      this.logger.debug("[{}] executeJS", id)

      this.uiExecutor.invoke {
        val jsFuture = function.invoke(this.jsAPI)
        jsFuture.addListener(
          {
            try {
              future.set(jsFuture.get())
            } catch (e: Throwable) {
              future.setException(e)
            }
          },
          MoreExecutors.directExecutor()
        )
      }

      this.waitOrFail(id, future)
    }
    return future
  }

  override fun close() {
    this.requestQueue.shutdown()
  }
}
