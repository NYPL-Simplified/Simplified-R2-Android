package org.librarysimplified.r2.vanilla.internal

import androidx.annotation.UiThread
import com.google.common.util.concurrent.ListenableFuture
import org.librarysimplified.r2.api.SR2ScrollingMode

/**
 * The Javascript API exported by web views.
 */

internal interface SR2JavascriptAPIType {

  /**
   * Open the next page in the current chapter.
   */

  @UiThread
  fun openPageNext(): ListenableFuture<String>

  /**
   * Open the previous page in the current chapter.
   */

  @UiThread
  fun openPagePrevious(): ListenableFuture<String>

  /**
   * Open the final page in the current chapter.
   */

  @UiThread
  fun openPageLast(): ListenableFuture<String>

  /**
   * Set the font family for the reader.
   */

  @UiThread
  fun setFontFamily(value: String): ListenableFuture<String>

  /**
   * Set the text scale (in the range [0, n], where `n = 1.0` means "100%".
   */

  @UiThread
  fun setFontSize(value: Double): ListenableFuture<String>

  /**
   * Set the reader color scheme.
   */

  @UiThread
  fun setTheme(value: SR2ReadiumInternalTheme): ListenableFuture<String>

  /**
   * Set the current chapter position. This must be in the range [0, 1].
   */

  @UiThread
  fun setProgression(progress: Double): ListenableFuture<String>

  /**
   * Enable/disable scroll mode for the reader.
   */

  @UiThread
  fun setScrollMode(value: SR2ScrollingMode): ListenableFuture<String>

  /**
   * Broadcast the current scroll position.
   *
   * @see SR2JavascriptAPIReceiverType.onReadingPositionChanged
   */

  @UiThread
  fun broadcastScrollPosition(): ListenableFuture<String>
}
