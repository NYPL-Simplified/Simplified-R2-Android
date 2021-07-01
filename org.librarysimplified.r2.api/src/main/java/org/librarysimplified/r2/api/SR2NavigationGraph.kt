package org.librarysimplified.r2.api

import org.librarysimplified.r2.api.SR2NavigationNode.SR2NavigationReadingOrderNode
import org.librarysimplified.r2.api.SR2NavigationNode.SR2NavigationResourceNode
import org.librarysimplified.r2.api.SR2NavigationNode.SR2NavigationTOCNode

/**
 * A navigation graph.
 */

data class SR2NavigationGraph(

  /**
   * The book reading order.
   */

  val readingOrder: List<SR2NavigationReadingOrderNode>,

  /**
   * The book's table of contents.
   */

  val tableOfContents: List<SR2NavigationTOCNode>,

  /**
   * The book's list of extra resources.
   */

  val resources: List<SR2NavigationResourceNode>
) {
  init {
    require(this.readingOrder.sortedBy { it.index } == this.readingOrder) {
      "The reading order must be sorted"
    }
    require(this.readingOrder.associateBy { it.index }.size == this.readingOrder.size) {
      "The reading order indices must be unique"
    }
  }

  /*
   * A flattened table of contents.
   */

  val tableOfContentsFlat: List<SR2TOCEntry> =
    flattenTOC(this.tableOfContents)

  /**
   * The node pointing at the start of the book.
   */

  fun start(): SR2NavigationNode {
    return this.readingOrder.first()
  }

  /**
   * Try to find a relevant navigation node for the given locator, but do not check outside
   * of the reading order to do so.
   */

  fun findNavigationReadingOrderNode(locator: SR2Locator): SR2NavigationReadingOrderNode? {
    for (node in this.readingOrder) {
      if (node.matches(locator)) {
        return node
      }
    }
    return null
  }

  /**
   * Try to find a relevant navigation node for the given locator, but do not check outside
   * of the table of contents to do so.
   */

  fun findNavigationTOCNode(locator: SR2Locator): SR2NavigationTOCNode? {
    for (entry in this.tableOfContentsFlat) {
      if (entry.node.matches(locator)) {
        return entry.node
      }
    }
    return null
  }

  /**
   * Try to find a relevant navigation node for the given locator, but do not check outside
   * of the resources to do so.
   */

  fun findResourcesNode(locator: SR2Locator): SR2NavigationResourceNode? {
    for (entry in this.resources) {
      if (entry.matches(locator)) {
        return entry
      }
    }
    return null
  }

  /**
   * Find a relevant navigation node for the given locator.
   */

  fun findNavigationNode(locator: SR2Locator): SR2NavigationNode? {
    return this.findNavigationReadingOrderNode(locator)
      ?: this.findNavigationTOCNode(locator)
      ?: this.findResourcesNode(locator)
  }

  /**
   * Find the "previous" node for the given node. There may not be any such thing as
   * a "previous" node for items that are not in the reading order.
   */

  fun findPreviousNode(currentNode: SR2NavigationNode): SR2NavigationNode? {
    return when (currentNode) {
      is SR2NavigationReadingOrderNode -> {
        if (currentNode.index == 0) {
          null
        } else {
          this.readingOrder[currentNode.index - 1]
        }
      }

      is SR2NavigationResourceNode ->
        null

      is SR2NavigationTOCNode -> {
        val indexOf = this.tableOfContentsFlat.indexOfFirst { entry -> entry.node == currentNode }
        if (indexOf > 0) {
          this.tableOfContentsFlat[indexOf - 1].node
        } else {
          null
        }
      }
    }
  }

  /**
   * Find the "next" node for the given node. There may not be any such thing as
   * a "next" node for items that are not in the reading order.
   */

  fun findNextNode(currentNode: SR2NavigationNode): SR2NavigationNode? {
    return when (currentNode) {
      is SR2NavigationReadingOrderNode -> {
        if (currentNode.index == this.readingOrder.size - 1) {
          null
        } else {
          this.readingOrder[currentNode.index + 1]
        }
      }

      is SR2NavigationResourceNode ->
        null

      is SR2NavigationTOCNode -> {
        val indexOf = this.tableOfContentsFlat.indexOfFirst { entry -> entry.node == currentNode }
        if (indexOf > 0) {
          if (indexOf + 1 < this.tableOfContentsFlat.size - 1) {
            this.tableOfContentsFlat[indexOf + 1].node
          } else {
            null
          }
        } else {
          null
        }
      }
    }
  }

  companion object {
    private fun flattenTOC(
      tableOfContents: List<SR2NavigationTOCNode>
    ): List<SR2TOCEntry> {
      val results = mutableListOf<SR2TOCEntry>()
      tableOfContents.forEach { node -> this.flattenTOCNode(node, 0, results) }
      return results.toList()
    }

    private fun flattenTOCNode(
      node: SR2NavigationTOCNode,
      depth: Int,
      results: MutableList<SR2TOCEntry>
    ) {
      results.add(SR2TOCEntry(node, depth))
      for (child in node.children) {
        this.flattenTOCNode(child, depth + 1, results)
      }
    }
  }
}
