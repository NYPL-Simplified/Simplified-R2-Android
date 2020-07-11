package org.librarysimplified.r2.adobe

import org.readium.r2.shared.fetcher.Fetcher
import org.readium.r2.shared.fetcher.TransformingFetcher
import org.readium.r2.shared.format.Format
import org.readium.r2.shared.publication.ContentProtection
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Manifest
import org.readium.r2.shared.publication.OnAskCredentials
import org.readium.r2.shared.publication.Properties
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.File
import org.readium.r2.shared.util.Try
import org.slf4j.LoggerFactory
import java.nio.charset.Charset

class AcsContentProtection : ContentProtection {

  companion object {

    private val logger =
      LoggerFactory.getLogger(AcsContentProtection::class.java)

  }

  private val RIGHTS_XML_SUFFIX = "_rights.xml"

  override suspend fun open(
    file: File,
    fetcher: Fetcher,
    askCredentials: Boolean,
    credentials: String?,
    sender: Any?,
    onAskCredentials: OnAskCredentials?
  ): Try<ContentProtection.ProtectedFile, Publication.OpeningError>? {

    if (file.format() != Format.EPUB)
      return null

    val rights = try {
        //File(file.path + RIGHTS_XML_SUFFIX).file.readText(Charset.defaultCharset())
        val pubDir = file.file.parentFile
        java.io.File(pubDir,"epub-rights_adobe.xml").readText(Charset.defaultCharset())
    } catch (e: Exception) {
        logger.info("no rights file found")
        file.file.parentFile.walk().forEach { logger.debug(it.path) }
        return null
    }

    val encryptionProperties = fetcher.get("/META-INF/encryption.xml").readAsXml().fold(
        {  AcsEncryptionParser.parse(it) },
        { return Try.failure(Publication.OpeningError.ParsingFailed(it)) }
      )

    val protectedFile = ContentProtection.ProtectedFile(
      file = file,
      fetcher = TransformingFetcher(fetcher, AcsDecryptor(rights)::transform),
      onCreateManifest = { _, manifest -> onCreateManifest(manifest, encryptionProperties) }
    )

    return Try.success(protectedFile)
  }

  private fun onCreateManifest(manifest: Manifest, encryptionProperties: Map<String, Map<String, Any>>): Manifest {

    fun Link.withEncryptionProperties(encryptionProperties: Map<String, Any>) =
      copy(properties = Properties(properties.otherProperties + ("encrypted" to encryptionProperties)))

    val readingOrder = manifest.readingOrder.map { link ->
      encryptionProperties[link.href]?.let { link.withEncryptionProperties(it) }
        ?: link
    }
    val resources = manifest.resources.map { link ->
      encryptionProperties[link.href]?.let { link.withEncryptionProperties(it) }
        ?: link
    }

    return manifest.copy(readingOrder = readingOrder, resources = resources)
  }

}
