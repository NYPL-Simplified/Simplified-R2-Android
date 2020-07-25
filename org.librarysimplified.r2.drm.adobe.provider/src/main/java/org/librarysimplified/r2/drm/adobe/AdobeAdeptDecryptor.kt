package org.librarysimplified.r2.drm.adobe

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
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

internal class AdobeAdeptDecryptor(
  private val rights: String,
  private val encryption: Map<String, AdobeAdeptEncryptionProperties>,
  private val coroutineDispatcher: CoroutineDispatcher
) {

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
      withContext(coroutineDispatcher) {
        org.nypl.drm.adobe.AdobeAdeptDecryptor(
          requireNotNull(encryption.resourceId),
          encryption.algorithm,
          encryption.originalLength ?: 0,
          rights
        ).use { decryptor ->
          decryptor.decrypt(bytes, null, true)
        }
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

    private var firstBlockFed: Boolean = false

    private lateinit var decryptor: org.nypl.drm.adobe.AdobeAdeptDecryptor

    override suspend fun link(): Link = resource.link()

    override suspend fun read(range: LongRange?): ResourceTry<ByteArray> =
      resource.read(range).mapCatching {
        try {
          decrypt(it, range, resource)
        } catch (e: DRMException) {
          throw Resource.Error.Forbidden
        }
      }

    private suspend fun decrypt(cipheredData: ByteArray, range: LongRange?, resource: Resource): ByteArray {

      if (!::decryptor.isInitialized)
        decryptor = withContext(coroutineDispatcher) {
          org.nypl.drm.adobe.AdobeAdeptDecryptor(
            requireNotNull(encryption.resourceId),
            encryption.algorithm,
            encryption.originalLength ?: 0,
            rights
          )
        }

      val minBlockSize = withContext(coroutineDispatcher) {
        decryptor.minimumBlockSize
      }

      val length = resource.length().getOrThrow()

      val previousBlock =
        if (range == null || range.first == 0L)
          null
        else
          resource.read(range.first - minBlockSize until range.first).getOrThrow()

      if (previousBlock != null) {

        if (!firstBlockFed) {
          val firstBlock = resource.read(0L until minBlockSize).getOrThrow()
          withContext(coroutineDispatcher) {
            decryptor.decrypt(firstBlock, null, firstBlock.size.toLong() == length)
          }
        }

        firstBlockFed = true
      }

      val isLastBlock = range == null || range.last == length - 1

      logger.debug("Ciphered data size ${cipheredData.size}")
      logger.debug("Calling decryptRange with previousBlock is null = ${previousBlock == null} and isLastBlock = $isLastBlock")
      return withContext(coroutineDispatcher) {
        decryptor.decrypt(cipheredData, previousBlock, isLastBlock)
      }
    }

    override suspend fun length(): ResourceTry<Long> =
      resource.length() //read().map { it.size.toLong() }

    override suspend fun close() {
      if (::decryptor.isInitialized)
        withContext(coroutineDispatcher) {
          decryptor.close()
        }

      resource.close()
    }

  }

}

