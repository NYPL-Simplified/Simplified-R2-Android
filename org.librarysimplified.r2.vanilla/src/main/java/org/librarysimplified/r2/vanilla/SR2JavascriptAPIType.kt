package org.librarysimplified.r2.vanilla

import androidx.annotation.UiThread

/**
 * The Javascript API exported by web views.
 */

interface SR2JavascriptAPIType {

  /**
   * Open the next page in the current chapter.
   */

  @UiThread
  fun openPageNext()

  /**
   * Open the previous page in the current chapter.
   */

  @UiThread
  fun openPagePrevious()

  /**
   * Open the final page in the current chapter.
   */

  @UiThread
  fun openPageLast()

  @UiThread
  fun setFontFamily(value: String)

  @UiThread
  fun setTextSize(value: Int)

  @UiThread
  fun setTextAlign(value: String)

  @UiThread
  fun setPageMargin(value: Double)

  @UiThread
  fun setLineHeight(value: Double)

  @UiThread
  fun setLetterSpacing(value: Double)

  @UiThread
  fun setWordSpacing(value: Double)

  @UiThread
  fun setTheme(value: ReaderTheme)

  /**
   * Set the current chapter position. This must be in the range [0, 1].
   */

  @UiThread
  fun setProgression(progress: Double)
}
