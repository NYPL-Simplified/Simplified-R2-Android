package org.librarysimplified.r2.api

data class SR2ReadingPosition(
  val chapterHref: String,
  val chapterProgress: Double,
  val chapterTitle: String?,
  val currentPage: Int?,
  val pageCount: Int?,
  val bookProgress: Double?
) {
  init {
    require(this.chapterProgress in 0.0..1.0) {
      "Chapter progress ${this.chapterProgress} must be in the range [0, 1]"
    }
    require(this.bookProgress == null || this.bookProgress in 0.0..1.0) {
      "Book progress ${this.bookProgress} must be null or in the range [0, 1]"
    }
  }

  val bookProgressPercent: Int?
    get() = this.bookProgress?.let { (it * 100.0).toInt() }

  val locator =
    SR2Locator.SR2LocatorPercent(this.chapterHref, this.chapterProgress)
}
