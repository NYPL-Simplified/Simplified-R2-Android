package org.librarysimplified.r2.vanilla.internal

import org.librarysimplified.r2.api.SR2BookMetadata
import org.readium.r2.shared.publication.Publication

internal object SR2Books {

  fun makeMetadata(
    publication: Publication,
    bookId: String
  ): SR2BookMetadata {
    return SR2BookMetadata(
      id = bookId,
      title = publication.metadata.title,
      navigationGraph = SR2NavigationGraphs.create(publication)
    )
  }
}
