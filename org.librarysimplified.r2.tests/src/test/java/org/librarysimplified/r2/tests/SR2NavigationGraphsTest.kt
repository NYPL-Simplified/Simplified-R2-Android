package org.librarysimplified.r2.tests

import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.librarysimplified.r2.vanilla.internal.SR2NavigationGraphs
import org.mockito.Mockito
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.asset.FileAsset
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.streamer.Streamer
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

class SR2NavigationGraphsTest {

  private val logger =
    LoggerFactory.getLogger(SR2NavigationGraphsTest::class.java)

  private lateinit var context: Context
  private lateinit var directory: File

  @BeforeEach
  fun testSetup() {
    this.directory =
      TestDirectories.temporaryDirectory()
    this.context =
      Mockito.mock(Context::class.java)

    Mockito.`when`(this.context.applicationContext)
      .thenReturn(this.context)
  }

  private fun loadPublication(name: String): Publication {
    val path = "/org/librarysimplified/r2/tests/$name"
    val url = SR2NavigationGraphsTest::class.java.getResource(path)
      ?: throw FileNotFoundException(path)

    val output = File(this.directory, "output.epub")
    url.openStream().use { inputStream ->
      output.outputStream().use { outputStream ->
        inputStream.copyTo(outputStream)
      }
    }

    val streamer = Streamer(this.context)
    return runBlocking {
      streamer.open(FileAsset(output), allowUserInteraction = false)
    }.getOrElse {
      throw IOException("Failed to open EPUB", it)
    }
  }

  @Test
  fun testBigTOC() {
    val publication = this.loadPublication("epubs/bigtoc.epub")
    val graph = SR2NavigationGraphs.create(publication)

    assertEquals(1000, graph.tableOfContentsFlat.size)
    assertEquals(1000, graph.tableOfContents.size)
    assertEquals(1000, graph.readingOrder.size)
    assertEquals(2, graph.resources.size)
  }

  @Test
  fun testLinks() {
    val publication = this.loadPublication("epubs/links.epub")
    val graph = SR2NavigationGraphs.create(publication)

    assertEquals(1, graph.tableOfContentsFlat.size)
    assertEquals(1, graph.tableOfContents.size)
    assertEquals(1, graph.readingOrder.size)
    assertEquals(1, graph.resources.size)
  }

  @Test
  fun testNestedTOC() {
    val publication = this.loadPublication("epubs/nestedtoc.epub")
    val graph = SR2NavigationGraphs.create(publication)

    assertEquals(3, graph.tableOfContentsFlat.size)
    assertEquals(1, graph.tableOfContents.size)
    assertEquals(1, graph.readingOrder.size)
    assertEquals("Something", graph.readingOrder[0].navigationPoint.title)
    assertEquals("Something", graph.tableOfContentsFlat[0].node.navigationPoint.title)
    assertEquals("Something Nested", graph.tableOfContentsFlat[1].node.navigationPoint.title)
    assertEquals("Something Else Nested", graph.tableOfContentsFlat[2].node.navigationPoint.title)
  }

  @Test
  fun testGreatExpectations() {
    val publication = this.loadPublication("epubs/charles-dickens_great-expectations.epub")
    val graph = SR2NavigationGraphs.create(publication)

    assertEquals(63, graph.tableOfContentsFlat.size)
    assertEquals(63, graph.tableOfContents.size)
    assertEquals(63, graph.readingOrder.size)
    assertEquals(7, graph.resources.size)
  }
}
