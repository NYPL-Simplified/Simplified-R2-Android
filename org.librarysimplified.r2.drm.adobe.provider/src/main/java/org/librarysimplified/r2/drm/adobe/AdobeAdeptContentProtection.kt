package org.librarysimplified.r2.drm.adobe

import org.librarysimplified.r2.drm.core.DrmProtectedFile
import org.nypl.drm.core.DRMException
import org.readium.r2.shared.fetcher.Fetcher
import org.readium.r2.shared.fetcher.TransformingFetcher
import org.readium.r2.shared.format.Format
import org.readium.r2.shared.publication.ContentProtection
import org.readium.r2.shared.publication.OnAskCredentials
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.contentProtectionServiceFactory
import org.readium.r2.shared.util.File
import org.readium.r2.shared.util.Try

internal class AdobeAdeptContentProtection : ContentProtection {

  override suspend fun open(
    file: File,
    fetcher: Fetcher,
    allowUserInteraction: Boolean,
    credentials: String?,
    sender: Any?,
    onAskCredentials: OnAskCredentials?
  ): Try<ContentProtection.ProtectedFile, Publication.OpeningError>? {

    val adobeRightsFile = (file as? DrmProtectedFile)?.adobeRightsFile

    if (file.format() != Format.EPUB || adobeRightsFile == null)
      return null

    val rights = try {
      adobeRightsFile.readText(Charsets.UTF_8)
    } catch (e: Exception) {
      return Try.failure(Publication.OpeningError.ParsingFailed(e))
    }

    val encryption = fetcher.get("/META-INF/encryption.xml").readAsXml()
      .fold(
        { AdobeAdeptEncryptionParser.parse(it) },
        { return Try.failure(Publication.OpeningError.ParsingFailed(it)) }
      )

    val encryptionItem =  encryption.values
      .firstOrNull { "aes128"  in it.algorithm }

    val isRestricted = if (encryptionItem == null) false else isRestricted(rights, encryptionItem)

    val serviceFactory =
      AdobeAdeptContentProtectionService.createFactory(isRestricted)

    val protectedFile = ContentProtection.ProtectedFile(
      file = file,
      fetcher = TransformingFetcher(fetcher, AdobeAdeptDecryptor(rights, encryption)::transform),
      onCreatePublication = {
        servicesBuilder.contentProtectionServiceFactory = serviceFactory
      }
    )

    return Try.success(protectedFile)
  }

  private fun isRestricted(rights: String, encryptionItem: AdobeAdeptEncryptionProperties): Boolean {

    return try {
      val decryptor = org.nypl.drm.adobe.AdobeAdeptDecryptor(
        encryptionItem.resourceId,
        encryptionItem.algorithm,
        encryptionItem.originalLength ?: 0,
        rights
      )
      decryptor.close()
      false
    } catch (e: Exception) {
      true
    }
  }
}
