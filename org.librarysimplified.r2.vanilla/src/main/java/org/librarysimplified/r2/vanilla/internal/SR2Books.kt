package org.librarysimplified.r2.vanilla.internal

import org.librarysimplified.r2.api.SR2BookChapter
import org.librarysimplified.r2.api.SR2BookMetadata
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Publication

internal object SR2Books {

  fun makeMetadata(
    publication: Publication,
    bookId: String,
    positionCount: Int
  ): SR2BookMetadata {
    return SR2BookMetadata(
      id = bookId,
      title = publication.metadata.title,
      readingOrder = this.makeReadingOrder(publication),
      positionCount = positionCount
    )
  }

  private fun makeReadingOrder(publication: Publication) =
    publication.readingOrder.mapIndexed { index, _ -> this.makeChapter(publication, index) }

  private fun makeChapter(
    publication: Publication,
    index: Int
  ): SR2BookChapter {
    val chapter =
      publication.readingOrder[index]
    val href =
      chapter.href
    val title =
      this.findTitle(publication, chapter) ?: ""

    return SR2BookChapter(
      chapterIndex = index,
      chapterHref = href,
      title = title
    )
  }

  private fun findTitle(
    publication: Publication,
    chapter: Link
  ): String? {
    for (tableItem in publication.tableOfContents) {
      if (tableItem.href.startsWith(chapter.href)) {
        return tableItem.title
      }
      if (chapter.href.startsWith(tableItem.href)) {
        return tableItem.title
      }
    }
    return null
  }
}
