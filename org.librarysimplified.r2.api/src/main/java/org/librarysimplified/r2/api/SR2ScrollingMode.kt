package org.librarysimplified.r2.api

/**
 * A specification of the scrolling mode.
 */

enum class SR2ScrollingMode {

  /**
   * Scrolling mode is enabled. Scrollbars will be shown in the web view, and the user can manually
   * scroll up and down individual chapters.
   */

  SCROLLING_MODE_ENABLED,

  /**
   * Scrolling mode is disabled. Scrollbars will not be shown in the web view, and the book
   * is presented as a set of pages. The user can navigate forwards and backwards a page at a
   * time.
   */

  SCROLLING_MODE_DISABLED
}
