package org.librarysimplified.r2.vanilla.internal

import org.librarysimplified.r2.api.SR2Locator
import org.librarysimplified.r2.api.SR2NavigationGraph
import org.librarysimplified.r2.api.SR2NavigationNode.SR2NavigationReadingOrderNode
import org.librarysimplified.r2.api.SR2NavigationNode.SR2NavigationResourceNode
import org.librarysimplified.r2.api.SR2NavigationNode.SR2NavigationTOCNode
import org.librarysimplified.r2.api.SR2NavigationPoint
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Publication

object SR2NavigationGraphs {

  fun create(
    publication: Publication
  ): SR2NavigationGraph {

    val readingOrder =
      publication.readingOrder.mapIndexed { index, link ->
        this.makeReadingOrderNode(publication, index, link)
      }
    val tableOfContents =
      publication.tableOfContents.map { link ->
        this.makeTableOfContentsNode(publication, link)
      }
    val resources =
      publication.resources.map { link -> this.makeResourceNode(link) }

    return SR2NavigationGraph(
      readingOrder = readingOrder,
      tableOfContents = tableOfContents,
      resources = resources
    )
  }

  private fun makeResourceNode(
    link: Link
  ): SR2NavigationResourceNode {
    return SR2NavigationResourceNode(
      SR2NavigationPoint(
        title = link.title ?: "",
        locator = SR2Locator.SR2LocatorPercent(link.href, 0.0)
      )
    )
  }

  private fun makeTableOfContentsNode(
    publication: Publication,
    link: Link
  ): SR2NavigationTOCNode {
    val navigationPoint =
      SR2NavigationPoint(
        title = link.title ?: "",
        locator = SR2Locator.SR2LocatorPercent(link.href, 0.0)
      )
    return SR2NavigationTOCNode(
      navigationPoint = navigationPoint,
      children = link.children.map { child -> makeTableOfContentsNode(publication, child) }
    )
  }

  private fun makeReadingOrderNode(
    publication: Publication,
    index: Int,
    link: Link
  ): SR2NavigationReadingOrderNode {
    val title =
      publication.tableOfContents.find { this.hrefMatches(it, link.href) }
        ?.title
        ?: link.title ?: ""
    return SR2NavigationReadingOrderNode(
      navigationPoint = SR2NavigationPoint(title, SR2Locator.SR2LocatorPercent(link.href, 0.0)),
      index = index
    )
  }

  private fun hrefMatches(
    link: Link,
    href: String
  ): Boolean {
    return when {
      link.href.startsWith(href) -> true
      href.startsWith(link.href) -> true
      else -> false
    }
  }
}
