package org.librarysimplified.r2.api

/**
 * Commands that may be executed by an R2 controller.
 *
 * @see [SR2ControllerType]
 */

sealed class SR2Command {

  /**
   * Reload whatever the controller opened last.
   */

  object Refresh : SR2Command() {
    override fun toString(): String =
      "[Refresh]"
  }

  /**
   * Open the chapter using the given locator.
   *
   * @see [SR2Event.SR2Error.SR2ChapterNonexistent]
   */

  data class OpenChapter(
    val locator: SR2Locator
  ) : SR2Command()

  /**
   * Open the next page. If the current chapter does not contain any more pages, then
   * implementations are required to behave as if the [OpenChapterNext] command had been
   * submitted instead.
   *
   * @see [SR2Event.SR2Error.SR2ChapterNonexistent]
   */

  object OpenPageNext : SR2Command() {
    override fun toString(): String =
      "[OpenPageNext]"
  }

  /**
   * Open the next chapter, moving to the first page in that chapter.
   *
   * @see [SR2Event.SR2Error.SR2ChapterNonexistent]
   */

  object OpenChapterNext : SR2Command() {
    override fun toString(): String =
      "[OpenChapterNext]"
  }

  /**
   * Open the previous page. If the current reading position is the start of the chapter, then
   * implementations are required to behave as if the [OpenChapterPrevious] command had been
   * submitted instead.
   *
   * @see [SR2Event.SR2Error.SR2ChapterNonexistent]
   */

  object OpenPagePrevious : SR2Command() {
    override fun toString(): String =
      "[OpenPagePrevious]"
  }

  /**
   * Open the previous chapter. If [atEnd] is `true`, then seek to the last page in the
   * chapter.
   *
   * @see [SR2Event.SR2Error.SR2ChapterNonexistent]
   */

  data class OpenChapterPrevious(
    val atEnd: Boolean
  ) : SR2Command()

  /**
   * Open an arbitrary link. This will either result a translation to the [OpenChapter]
   * command, or it will result in the controller publishing events indicating either
   * an error, or that the link needs to be opened in an external web view.
   */

  data class OpenLink(
    val link: String
  ) : SR2Command()

  /**
   * Load a set of bookmarks into the controller. This merely has the effect of making
   * the bookmarks available to the table of contents; it does not trigger any changes
   * in navigation.
   */

  data class BookmarksLoad(
    val bookmarks: List<SR2Bookmark>
  ) : SR2Command()

  /**
   * Create a new (explicit) bookmark at the current reading position.
   */

  object BookmarkCreate : SR2Command() {
    override fun toString(): String =
      "[BookmarkCreate]"
  }

  /**
   * Delete a specific bookmark.
   */

  data class BookmarkDelete(
    val bookmark: SR2Bookmark
  ) : SR2Command()

  /**
   * Set the overall theme for the reader.
   */

  data class ThemeSet(
    val theme: SR2Theme
  ) : SR2Command()

  /**
   * Set the scrolling mode for the reader.
   */

  data class ScrollingModeSet(
    val scrollingMode: SR2ScrollingMode
  ) : SR2Command()
}
