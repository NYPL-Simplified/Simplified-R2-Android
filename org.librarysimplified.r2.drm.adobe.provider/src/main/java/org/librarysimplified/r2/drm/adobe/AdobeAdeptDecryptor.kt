package org.librarysimplified.r2.drm.adobe

import org.nypl.drm.core.DRMException
import org.readium.r2.shared.fetcher.BytesResource
import org.readium.r2.shared.fetcher.FailureResource
import org.readium.r2.shared.fetcher.LazyResource
import org.readium.r2.shared.fetcher.Resource
import org.readium.r2.shared.fetcher.ResourceTry
import org.readium.r2.shared.fetcher.mapCatching
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.util.Try
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import kotlin.math.ceil

internal class AdobeAdeptDecryptor(private val rights: String, private val encryption: Map<String, AdobeAdeptEncryptionProperties>) {

  companion object {

    private val logger = LoggerFactory.getLogger(AdobeAdeptDecryptor::class.java)

    const val AdeptAlgorithmCompressed = "http://www.w3.org/2001/04/xmlenc#aes128-cbc"
    const val AdeptAlgorithmUncompressed = "http://ns.adobe.com/adept/xmlenc#aes128-cbc-uncompressed"

  }

  fun transform(resource: Resource): Resource = LazyResource {
    val link = resource.link()
    val encryptionProps = encryption[link.href]
    if (encryptionProps == null || encryptionProps.algorithm !in listOf(AdeptAlgorithmCompressed, AdeptAlgorithmUncompressed))
      return@LazyResource resource

    logger.debug("attempting to instantiate a decryption Resource")
    logger.debug("href is ${link.href}")
    logger.debug("algorithm is ${encryptionProps.algorithm}")
    logger.debug("originalLength is ${encryptionProps.originalLength}")

    return@LazyResource try {
      when(encryptionProps.algorithm) {
        AdeptAlgorithmUncompressed -> CbcAdeptResource(resource, encryptionProps)
        else -> FullAdeptResource(resource, encryptionProps)
      }
    } catch (e: DRMException) {
      FailureResource(link, Resource.Error.Forbidden)
    }
  }

  private inner class FullAdeptResource(
    private val resource: Resource,
    private val encryption: AdobeAdeptEncryptionProperties
  ) : BytesResource( {

    val bytes = resource.read().mapCatching { bytes ->
        org.nypl.drm.adobe.AdobeAdeptDecryptor(
          requireNotNull(encryption.resourceId),
          encryption.algorithm,
          encryption.originalLength ?: 0,
          rights
        ).use { decryptor ->
          decryptor.decryptFully(bytes)
        }
    }

    Pair(resource.link(), bytes)

  } ) {

    override suspend fun length(): ResourceTry<Long> =
      encryption.originalLength
        ?.let { Try.success(it) }
        ?: super.length()

    override suspend fun close() = resource.close()
  }

  private inner class CbcAdeptResource(private val resource: Resource, private val encryption: AdobeAdeptEncryptionProperties) : Resource {

    private val decryptor = org.nypl.drm.adobe.AdobeAdeptDecryptor(
      requireNotNull(encryption.resourceId),
      encryption.algorithm,
      encryption.originalLength ?: 0,
      rights
    )

    override suspend fun link(): Link = resource.link()

    override suspend fun read(range: LongRange?): ResourceTry<ByteArray> =
      resource.read(range).mapCatching { cipheredData ->
        try {
          val isStart = range == null || range.first == 0L
          val isEnd =  range == null || range.last == resource.length().getOrThrow() - 1
          logger.debug("Ciphered data size ${cipheredData.size}")
          logger.debug("Calling decryptRange with isStart = $isStart and isEnd = $isEnd")
          val previousBlock =
            if (range == null || isStart)
              null
            else
              resource.read(range.first - 4096 until range.first).getOrThrow()
          decryptor.decryptRange(cipheredData, previousBlock, isStart, isEnd)
        } catch (e: DRMException) {
          throw Resource.Error.Forbidden
        }
      }

    override suspend fun length(): ResourceTry<Long> =
      resource.length() //read().map { it.size.toLong() }

    override suspend fun close() {
      decryptor.close()
      resource.close()
    }

  }

}

