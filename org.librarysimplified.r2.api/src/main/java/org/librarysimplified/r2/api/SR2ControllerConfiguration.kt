package org.librarysimplified.r2.api

import android.content.Context
import com.google.common.util.concurrent.ListeningExecutorService
import org.readium.r2.shared.publication.ContentProtection
import org.readium.r2.shared.publication.asset.PublicationAsset

/**
 * Configuration values for an R2 controller.
 */

data class SR2ControllerConfiguration(

  /**
   * A publication asset containing a book.
   */

  val bookFile: PublicationAsset,

  /**
   * An identifier used to uniquely identify a publication. Unfortunately, identifier are optional
   * in EPUB files. For the sake of consistency, we require an identifier to always be provided.
   */

  val bookId: String,

  /**
   * The initial theme used for the reader.
   */

  val theme: SR2Theme,

  /**
   * The current Android application context.
   */

  val context: Context,

  /**
   * Content protections to provide the Readium Streamer with.
   */

  val contentProtections: List<ContentProtection>,

  /**
   * An executor service used to execute I/O code on one or more background threads.
   */

  val ioExecutor: ListeningExecutorService,

  /**
   * The book scrolling mode.
   */

  val scrollingMode: SR2ScrollingMode,

  /**
   * The pagination numbering mode.
   */

  val pageNumberingMode: SR2PageNumberingMode
)
