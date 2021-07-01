package org.librarysimplified.r2.api

import java.net.URI
import java.net.URISyntaxException

/**
 * A navigation node.
 */

sealed class SR2NavigationNode {

  /**
   * @return true If the locator matches this navigation node
   */

  fun matches(locator: SR2Locator): Boolean {
    return try {
      val nodeURI = URI(this.navigationPoint.locator.chapterHref)
      val wantURI = URI(locator.chapterHref)
      nodeURI.path == wantURI.path
    } catch (e: URISyntaxException) {
      false
    }
  }

  abstract val navigationPoint: SR2NavigationPoint

  /**
   * The node title, equivalent to that of the navigation point within.
   */

  val title: String
    get() = this.navigationPoint.title

  /**
   * A node in the reading order.
   */

  data class SR2NavigationReadingOrderNode(
    override val navigationPoint: SR2NavigationPoint,
    val index: Int
  ) : SR2NavigationNode()

  /**
   * A node in the table of contents.
   */

  data class SR2NavigationTOCNode(
    override val navigationPoint: SR2NavigationPoint,
    val children: List<SR2NavigationTOCNode>
  ) : SR2NavigationNode()

  /**
   * A node in the resource list.
   */

  data class SR2NavigationResourceNode(
    override val navigationPoint: SR2NavigationPoint
  ) : SR2NavigationNode()
}
