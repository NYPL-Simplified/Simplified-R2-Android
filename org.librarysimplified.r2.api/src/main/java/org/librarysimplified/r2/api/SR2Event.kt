package org.librarysimplified.r2.api

import java.lang.Exception

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
   * The center of the webview was tapped. Implementors may want to show reader
   * controls as a result of this event.
   */

  class SR2OnCenterTapped : SR2Event() {
    override fun toString(): String =
      "[SR2OnCenterTapped]"
  }

  /**
   * The reading position changed.
   */

  data class SR2ReadingPositionChanged(
    val chapterIndex: Int,
    val chapterTitle: String?,
    val chapterProgress: Double,
    val currentPage: Int,
    val pageCount: Int,
    val bookProgress: Double
  ) : SR2Event() {

    init {
      require(this.chapterProgress in 0.0..1.0) {
        "Chapter progress ${this.chapterProgress} must be in the range [0, 1]"
      }
      require(this.bookProgress in 0.0..1.0) {
        "Book progress ${this.bookProgress} must be in the range [0, 1]"
      }
    }

    val bookProgressPercent: Int
      get() = (this.bookProgress * 100.0).toInt()

    val locator =
      SR2Locator.SR2LocatorPercent(this.chapterIndex, this.chapterProgress)
  }

  /**
   * The set of events related to bookmarks.
   */

  sealed class SR2BookmarkEvent : SR2Event() {

    /**
     * A bookmark was created.
     */

    data class SR2BookmarkCreated(
      val bookmark: SR2Bookmark
    ) : SR2BookmarkEvent()

    /**
     * A bookmark was deleted.
     */

    data class SR2BookmarkDeleted(
      val bookmark: SR2Bookmark
    ) : SR2BookmarkEvent()

    /**
     * Bookmarks were loaded into the controller.
     */

    object SR2BookmarksLoaded : SR2BookmarkEvent() {
      override fun toString(): String =
        "[SR2BookmarksLoaded]"
    }
  }

  /**
   * The reader theme was changed.
   */

  data class SR2ThemeChanged(
    val theme: SR2Theme
  ) : SR2Event()

  /**
   * The set of events related to commands.
   */

  sealed class SR2CommandEvent : SR2Event() {

    /**
     * The command to which the event refers.
     */

    abstract val command: SR2Command

    /**
     * The execution of a command started.
     */

    data class SR2CommandExecutionStarted(
      override val command: SR2Command
    ) : SR2CommandEvent()

    /**
     * The execution of a command finished.
     */

    sealed class SR2CommandEventCompleted : SR2CommandEvent() {

      /**
       * The execution of a command succeeded.
       */

      data class SR2CommandExecutionSucceeded(
        override val command: SR2Command
      ) : SR2CommandEventCompleted()

      /**
       * The execution of a command failed.
       */

      data class SR2CommandExecutionFailed(
        override val command: SR2Command,
        val exception: Exception
      ) : SR2CommandEventCompleted()
    }
  }
}
