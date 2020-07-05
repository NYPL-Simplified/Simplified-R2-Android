package org.librarysimplified.r2.adobe

import org.readium.r2.shared.extensions.tryOrNull
import org.readium.r2.shared.fetcher.Fetcher
import org.readium.r2.shared.fetcher.TransformingFetcher
import org.readium.r2.shared.format.Format
import org.readium.r2.shared.publication.ContentProtection
import org.readium.r2.shared.publication.OnAskCredentialsCallback
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.File
import org.readium.r2.shared.util.Try
import java.nio.charset.Charset

class AcsContentProtection : ContentProtection {

  private val RIGHTS_XML_SUFFIX = "_rights.xml"

  override suspend fun open(
    file: File,
    fetcher: Fetcher,
    askCredentials: Boolean,
    credentials: String?,
    sender: Any?,
    onAskCredentials: OnAskCredentialsCallback?
  ): Try<ContentProtection.ProtectedFile, Publication.OpeningError>? {

    if (file.format() != Format.EPUB)
      return null

    val rights = tryOrNull {
      File(file.path + RIGHTS_XML_SUFFIX).file.readText(Charset.defaultCharset())
    } ?: return null

    val protectedFile = ContentProtection.ProtectedFile(
      file,
      TransformingFetcher(fetcher, AcsDecryptor(rights)::transform),
      null
    )

    return Try.success(protectedFile)
  }

}
