package org.librarysimplified.r2.vanilla.internal

import android.webkit.WebView
import androidx.annotation.UiThread
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import org.librarysimplified.r2.api.SR2Command
import org.librarysimplified.r2.api.SR2ControllerCommandQueueType
import org.librarysimplified.r2.api.SR2PublisherCSS
import org.librarysimplified.r2.api.SR2PublisherCSS.SR2_PUBLISHER_DEFAULT_CSS_DISABLED
import org.librarysimplified.r2.api.SR2PublisherCSS.SR2_PUBLISHER_DEFAULT_CSS_ENABLED
import org.librarysimplified.r2.api.SR2ScrollingMode
import org.librarysimplified.r2.api.SR2ScrollingMode.SCROLLING_MODE_CONTINUOUS
import org.librarysimplified.r2.api.SR2ScrollingMode.SCROLLING_MODE_PAGINATED
import org.librarysimplified.r2.ui_thread.SR2UIThreadService
import org.librarysimplified.r2.vanilla.internal.SR2ReadiumInternalTheme.DARK
import org.librarysimplified.r2.vanilla.internal.SR2ReadiumInternalTheme.DAY
import org.librarysimplified.r2.vanilla.internal.SR2ReadiumInternalTheme.LIGHT
import org.librarysimplified.r2.vanilla.internal.SR2ReadiumInternalTheme.NIGHT
import org.librarysimplified.r2.vanilla.internal.SR2ReadiumInternalTheme.SEPIA
import org.slf4j.LoggerFactory

/**
 * The default implementation of the Javascript API.
 */

internal class SR2JavascriptAPI(
  private val webView: WebView,
  private val commandQueue: SR2ControllerCommandQueueType
) : SR2JavascriptAPIType {

  private val logger =
    LoggerFactory.getLogger(SR2JavascriptAPI::class.java)

  @UiThread
  private fun executeJavascript(
    script: String
  ): ListenableFuture<String> {
    SR2UIThreadService.checkIsUIThread()

    val future = SettableFuture.create<String>()
    this.logger.debug("evaluating {}", script)
    this.webView.evaluateJavascript(script) {
      try {
        this.logger.debug("evaluated {} ⇒ {}", script, it)
      } finally {
        future.set(it)
      }
    }
    return future
  }

  @UiThread
  override fun openPageNext(): ListenableFuture<String> {
    val future = this.executeJavascript("readium.scrollRight();")
    future.addListener(
      {
        when (future.get()) {
          "false" -> {
            this.commandQueue.submitCommand(SR2Command.OpenChapterNext)
          }
          else -> {
          }
        }
      },
      MoreExecutors.directExecutor()
    )
    return future
  }

  @UiThread
  override fun openPagePrevious(): ListenableFuture<String> {
    val future = this.executeJavascript("readium.scrollLeft();")
    future.addListener(
      {
        when (future.get()) {
          "false" -> {
            this.commandQueue.submitCommand(SR2Command.OpenChapterPrevious(atEnd = true))
          }
          else -> {
          }
        }
      },
      MoreExecutors.directExecutor()
    )
    return future
  }

  @UiThread
  override fun openPageLast(): ListenableFuture<String> {
    return this.executeJavascript("readium.scrollToEnd();")
  }

  @UiThread
  override fun setFontFamily(value: String): ListenableFuture<*> {
    return Futures.allAsList(
      this.setUserProperty("fontFamily", value),
      this.setUserProperty("fontOverride", "readium-font-on")
    )
  }

  @UiThread
  override fun setFontSize(value: Double): ListenableFuture<String> {
    val percent = (value * 100.0).toString() + "%"
    return this.setUserProperty("fontSize", percent)
  }

  override fun setTheme(value: SR2ReadiumInternalTheme): ListenableFuture<String> =
    when (value) {
      LIGHT, DAY ->
        this.setUserProperty("appearance", "readium-default-on")
      DARK, NIGHT ->
        this.setUserProperty("appearance", "readium-night-on")
      SEPIA ->
        this.setUserProperty("appearance", "readium-sepia-on")
    }

  @UiThread
  override fun setProgression(progress: Double): ListenableFuture<String> {
    return this.executeJavascript("readium.scrollToPosition($progress);")
  }

  @UiThread
  override fun broadcastReadingPosition(): ListenableFuture<*> {
    return this.executeJavascript("readium.broadcastReadingPosition();")
  }

  @UiThread
  override fun setScrollMode(mode: SR2ScrollingMode): ListenableFuture<*> {
    return this.setUserProperty(
      name = "scroll",
      value = when (mode) {
        SCROLLING_MODE_PAGINATED -> "readium-scroll-off"
        SCROLLING_MODE_CONTINUOUS -> "readium-scroll-on"
      }
    )
  }

  @UiThread
  override fun scrollToId(id: String): ListenableFuture<*> {
    return this.executeJavascript("readium.scrollToId(\"$id\");")
  }

  @UiThread
  override fun setPublisherCSS(
    css: SR2PublisherCSS
  ): ListenableFuture<*> {
    return when (css) {
      SR2_PUBLISHER_DEFAULT_CSS_ENABLED ->
        Futures.allAsList(
          this.setUserProperty("advancedSettings", ""),
          this.setUserProperty("fontOverride", "")
        )
      SR2_PUBLISHER_DEFAULT_CSS_DISABLED ->
        Futures.allAsList(
          this.setUserProperty("advancedSettings", "readium-advanced-on"),
          this.setUserProperty("fontOverride", "readium-font-on")
        )
    }
  }

  @UiThread
  fun setUserProperty(
    name: String,
    value: String
  ): ListenableFuture<String> {
    return this.executeJavascript("readium.setProperty(\"--USER__${name}\", \"${value}\");")
  }
}
