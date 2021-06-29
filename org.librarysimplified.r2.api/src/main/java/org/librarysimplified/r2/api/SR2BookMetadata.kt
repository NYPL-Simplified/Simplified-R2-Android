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
   * The chapters of the book in reading order.
   */

  val readingOrder: List<SR2BookChapter>,

  /**
   * The number of positions in the book
   */

  val positionCount: Int
) {

  init {
    require(this.readingOrder.sortedBy { it.chapterIndex } == this.readingOrder) {
      "The reading order must be sorted"
    }
    require(this.readingOrder.associateBy { it.chapterIndex }.size == this.readingOrder.size) {
      "The reading order indices must be unique"
    }
  }

  /**
   * Find a chapter with the given href.
   */

  fun findChapter(
    href: String
  ): SR2BookChapter? {
    for (index in this.readingOrder.indices) {
      if (this.readingOrder[index].chapterHref == href) {
        return this.readingOrder[index]
      }
    }
    return null
  }

  /**
   * Find a chapter for the given locator.
   */

  fun findChapter(
    locator: SR2Locator
  ): SR2BookChapter? {
    return this.findChapter(locator.chapterHref)
  }

  fun nextChapter(
    currentChapter: SR2BookChapter
  ): SR2BookChapter? {
    check(this.readingOrder.contains(currentChapter)) {
      "Book metadata must contain the given chapter $currentChapter"
    }

    return this.readingOrder
      .filter { chapter -> chapter.chapterIndex > currentChapter.chapterIndex }
      .minByOrNull { chapter -> chapter.chapterIndex }
  }

  fun previousChapter(
    currentChapter: SR2BookChapter
  ): SR2BookChapter? {
    check(this.readingOrder.contains(currentChapter)) {
      "Book metadata must contain the given chapter $currentChapter"
    }

    return this.readingOrder
      .filter { chapter -> chapter.chapterIndex < currentChapter.chapterIndex }
      .maxByOrNull { chapter -> chapter.chapterIndex }
  }
}
