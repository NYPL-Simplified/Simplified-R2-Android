package org.librarysimplified.r2.views.internal

internal object SR2TOC {

  /**
   * The delay applied before closing the TOC after an item has been selected. This purely exists
   * to allow the selection animation time to complete before the TOC fragment is closed.
   */

  const val closeTocDelay: Long = 300L

  /**
   * The delay applied before enabling clicks on TOC items again. This purely exists to prevent
   * click handlers from being executed multiple times.
   */

  const val reenableClickDelay: Long = 1000L
}
