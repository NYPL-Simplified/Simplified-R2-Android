package org.librarysimplified.r2.vanilla.internal

import android.webkit.WebView
import androidx.annotation.UiThread
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import org.librarysimplified.r2.api.SR2Command
import org.librarysimplified.r2.api.SR2ControllerCommandQueueType
import org.librarysimplified.r2.api.SR2ScrollingMode
import org.librarysimplified.r2.api.SR2ScrollingMode.SCROLLING_MODE_DISABLED
import org.librarysimplified.r2.api.SR2ScrollingMode.SCROLLING_MODE_ENABLED
import org.librarysimplified.r2.ui_thread.SR2UIThread
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
    SR2UIThread.checkIsUIThread()

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

  override fun setFontFamily(value: String): ListenableFuture<String> {
    val future = this.setUserProperty("fontFamily", value)
    future.addListener(
      Runnable {
        this.setUserProperty("fontOverride", "readium-font-on")
      },
      MoreExecutors.directExecutor()
    )
    return future
  }

  @UiThread
  override fun setFontSize(value: Double): ListenableFuture<String> {
    val percent = (value * 100.0).toString() + "%"
    return this.setUserProperty("fontSize", percent)
  }

  private fun setTextAlign(value: String): ListenableFuture<String> {
    return this.setUserProperty("textAlign", value)
  }

  private fun setPageMargin(value: Double): ListenableFuture<String> {
    // Note: The js property name is 'pageMargins' plural
    return this.setUserProperty("pageMargins", "$value")
  }

  private fun setLineHeight(value: Double): ListenableFuture<String> {
    return this.setUserProperty("lineHeight", "$value")
  }

  private fun setLetterSpacing(value: Double): ListenableFuture<String> {
    return this.setUserProperty("letterSpacing", "${value}em")
  }

  private fun setWordSpacing(value: Double): ListenableFuture<String> {
    return this.setUserProperty("wordSpacing", "${value}rem")
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
  override fun setScrollMode(value: SR2ScrollingMode): ListenableFuture<String> {
    return when (value) {
      SCROLLING_MODE_ENABLED -> this.setUserProperty("scroll", "readium-scroll-on")
      SCROLLING_MODE_DISABLED -> this.setUserProperty("scroll", "readium-scroll-off")
    }
  }

  @UiThread
  override fun broadcastScrollPosition(): ListenableFuture<String> {
    return this.executeJavascript("readium.broadcastScrollPosition();")
  }

  @UiThread
  fun setUserProperty(
    name: String,
    value: String
  ): ListenableFuture<String> {
    return this.executeJavascript("readium.setProperty(\"--USER__${name}\", \"${value}\");")
  }
}
