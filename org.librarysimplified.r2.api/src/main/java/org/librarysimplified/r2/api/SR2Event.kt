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
      val chapterHref: String,
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
   * controls as a result of this event. For convenience, the controller tracks
   * a boolean value indicating whether or not the UI should be shown, assuming
   * that the UI starts initially visible and the visibility is toggled on each
   * center tap.
   */

  class SR2OnCenterTapped(
    val uiVisible: Boolean
  ) : SR2Event()

  /**
   * The reading position changed.
   */

  data class SR2ReadingPositionChanged(
    val chapterHref: String,
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
      SR2Locator.SR2LocatorPercent(this.chapterHref, this.chapterProgress)
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
   * The user attempted to open a link that should be opened in an external web browser.
   */

  data class SR2ExternalLinkSelected(
    val link: String
  ) : SR2Event()

  /**
   * The reader theme was changed.
   */

  data class SR2ThemeChanged(
    val theme: SR2Theme
  ) : SR2Event()

  /**
   * The scrolling mode was changed.
   */

  data class SR2ScrollingModeChanged(
    val scrollingMode: SR2ScrollingMode
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
     * A command is executing and is expected to take long enough that the user should probably
     * be shown a progress bar.
     */

    data class SR2CommandExecutionRunningLong(
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
