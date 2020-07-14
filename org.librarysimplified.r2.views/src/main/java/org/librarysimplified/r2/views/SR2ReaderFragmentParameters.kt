package org.librarysimplified.r2.views

import java.io.File
import java.io.Serializable

/**
 * The parameters required to open an SR2 fragment.
 */

data class SR2ReaderFragmentParameters(

  /**
   * The file containing the book to be opened.
   */

  val bookFile: File,

  /**
   * The file containing Adobe DRM rights information.
   */

  val adobeRightsFile: File?

) : Serializable