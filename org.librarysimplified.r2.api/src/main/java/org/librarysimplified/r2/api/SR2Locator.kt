package org.librarysimplified.r2.api

/**
 * A location within a book.
 */

sealed class SR2Locator : Comparable<SR2Locator> {

  abstract val chapterHref: String

  /**
   * @return The fragment associated with the chapter href, if any
   */

  fun chapterHrefFragment(): String? {
    return when (val fragment = this.chapterHref.substringAfter('#', "")) {
      "" -> null
      else -> fragment
    }
  }

  data class SR2LocatorPercent(
    override val chapterHref: String,
    val chapterProgress: Double
  ) : SR2Locator() {

    init {
      require(this.chapterProgress in 0.0..1.0) {
        "${this.chapterProgress} must be in the range [0, 1]"
      }
    }

    override fun compareTo(other: SR2Locator): Int {
      val indexCmp = this.chapterHref.compareTo(other.chapterHref)
      return if (indexCmp == 0) {
        when (other) {
          is SR2LocatorPercent ->
            this.chapterProgress.compareTo(other.chapterProgress)
          is SR2LocatorChapterEnd ->
            this.chapterProgress.compareTo(1.0)
        }
      } else {
        indexCmp
      }
    }
  }

  data class SR2LocatorChapterEnd(
    override val chapterHref: String,
  ) : SR2Locator() {
    override fun compareTo(other: SR2Locator): Int {
      val indexCmp = this.chapterHref.compareTo(other.chapterHref)
      return if (indexCmp == 0) {
        when (other) {
          is SR2LocatorPercent ->
            1.0.compareTo(other.chapterProgress)
          is SR2LocatorChapterEnd ->
            0
        }
      } else {
        indexCmp
      }
    }
  }
}
