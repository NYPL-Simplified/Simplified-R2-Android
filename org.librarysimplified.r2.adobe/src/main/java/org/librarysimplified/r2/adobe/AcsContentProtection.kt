package org.librarysimplified.r2.adobe

import org.readium.r2.shared.fetcher.Fetcher
import org.readium.r2.shared.fetcher.TransformingFetcher
import org.readium.r2.shared.format.Format
import org.readium.r2.shared.publication.ContentProtection
import org.readium.r2.shared.publication.OnAskCredentials
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.File
import org.readium.r2.shared.util.Try
import java.nio.charset.Charset

class AcsContentProtection : ContentProtection {

  override suspend fun open(
    file: File,
    fetcher: Fetcher,
    askCredentials: Boolean,
    credentials: String?,
    sender: Any?,
    onAskCredentials: OnAskCredentials?
  ): Try<ContentProtection.ProtectedFile, Publication.OpeningError>? {

    val adobeRightsFile = (file as?  AcsReadiumFile)?.adobeRightsFile

    if (file.format() != Format.EPUB || adobeRightsFile == null)
      return null

    val rights = try {
      adobeRightsFile.readText(Charset.defaultCharset())
    } catch (e: Exception) {
      return Try.failure(Publication.OpeningError.ParsingFailed(e))
    }

    val encryption = fetcher.get("/META-INF/encryption.xml").readAsXml()
      .fold(
        { AcsEncryptionParser.parse(it) },
        { return Try.failure(Publication.OpeningError.ParsingFailed(it)) }
      )

    val protectedFile = ContentProtection.ProtectedFile(
      file = file,
      fetcher = TransformingFetcher(fetcher, AcsDecryptor(rights, encryption)::transform)
    )

    return Try.success(protectedFile)
  }

}
