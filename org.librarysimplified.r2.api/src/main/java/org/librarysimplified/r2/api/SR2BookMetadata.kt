package org.librarysimplified.r2.api

/**
 * Information about the currently loaded book.
 */

data class SR2BookMetadata(

  /**
   * The unique identifier of the book.
   */

  val id: String,

  /**
   * The book title.
   */

  val title: String,

  /**
   * The book's navigation graph
   */

  val navigationGraph: SR2NavigationGraph
)
