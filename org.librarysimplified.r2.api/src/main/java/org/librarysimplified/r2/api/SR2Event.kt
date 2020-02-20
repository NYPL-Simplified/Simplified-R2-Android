package org.librarysimplified.r2.api

/**
 * The type of events published by R2 controllers.
 */

sealed class SR2Event {

  /**
   * The subset of events that correspond to errors.
   */

  sealed class SR2Error : SR2Event() {

    /**
     * An attempt was made to open a chapter in a book that does not exist.
     */

    data class SR2ChapterNonexistent(
      val chapterIndex: Int,
      val message: String
    ) : SR2Error()

    /**
     * An attempt was made to execute a command that requires access to a [WebView],
     * but not web view is currently connected to the controller.
     */

    data class SR2WebViewInaccessible(
      val message: String
    ) : SR2Error()
  }

  /**
   * The reading position changed.
   *
   * @param chapterIndex
   * @param chapterTitle
   * @param currentPage
   * @param pageCount
   * @param percent
   */

  data class SR2ReadingPositionChanged(
    val chapterIndex: Int,
    val chapterTitle: String?,
    val currentPage: Int,
    val pageCount: Int,
    val percent: Int
  ) : SR2Event()
}
