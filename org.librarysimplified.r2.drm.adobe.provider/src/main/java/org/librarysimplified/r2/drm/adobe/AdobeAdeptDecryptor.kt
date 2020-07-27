package org.librarysimplified.r2.drm.adobe

import org.librarysimplified.r2.drm.adobe.provider.BuildConfig
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
        AdeptAlgorithmUncompressed -> CbcAdeptResource(resource, encryptionProps, rights)
        else -> FullAdeptResource(resource, encryptionProps, rights)
      }
    } catch (e: DRMException) {
      FailureResource(link, Resource.Error.Forbidden)
    }
  }

  private class FullAdeptResource(
    private val resource: Resource,
    private val encryption: AdobeAdeptEncryptionProperties,
    private val rights: String
  ) : BytesResource( {

    val bytes = resource.read().mapCatching { bytes ->
      org.nypl.drm.adobe.AdobeAdeptDecryptor(
          requireNotNull(encryption.resourceId),
          encryption.algorithm,
          encryption.originalLength ?: 0,
          rights
        ).use { decryptor ->
          decryptor.decrypt(bytes, null, true)
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

  private class CbcAdeptResource(
    private val resource: Resource,
    private val encryption: AdobeAdeptEncryptionProperties,
    private val rights: String
  ) : Resource {

    private var firstBlockFed: Boolean = false

    private var decryptor = org.nypl.drm.adobe.AdobeAdeptDecryptor(
      requireNotNull(encryption.resourceId),
      encryption.algorithm,
      encryption.originalLength ?: 0,
      rights
    )

    override suspend fun link(): Link = resource.link()

    override suspend fun read(range: LongRange?): ResourceTry<ByteArray> {
      @Suppress("NAME_SHADOWING")
      val range = range
        ?.coerceToPositiveIncreasing()
        ?.apply { requireLengthFitInt() }

      return resource.read(range).mapCatching {
        try {
          decrypt(it, range)
        } catch (e: DRMException) {
          throw Resource.Error.Forbidden
        }
      }
    }

    private suspend fun decrypt(cipheredData: ByteArray, range: LongRange?): ByteArray {
      val length = resource.length().getOrThrow()

      val previousBlock =
        if (range == null || range.first == 0L)
          null
        else
          resource.read(range.first - decryptor.minimumBlockSize until range.first).getOrThrow()

      if (previousBlock != null) {

        if (!firstBlockFed) {
          val firstBlock = resource.read(0L until decryptor.minimumBlockSize).getOrThrow()
          decryptor.decrypt(firstBlock, null, firstBlock.size.toLong() == length)
        }

        firstBlockFed = true
      }

      val isLastBlock = range == null || range.last == length - 1

      logger.debug("Range $range")
      logger.debug("Ciphered data size ${cipheredData.size}")
      logger.debug("Calling decrypt with previousBlock is null = ${previousBlock == null} and isLastBlock = $isLastBlock")
      return decryptor.decrypt(cipheredData, previousBlock, isLastBlock)
    }

    override suspend fun length(): ResourceTry<Long> {
      if (encryption.originalLength != null)
        return Try.success(encryption.originalLength)

      return resource.length().mapCatching { encryptedLength ->
        val firstBlockRange = 0L until kotlin.math.min(encryptedLength, decryptor.minimumBlockSize.toLong())
        val encryptedFirstBlock = resource.read(firstBlockRange).getOrThrow()
        val decryptedFirstBlock = decrypt(encryptedFirstBlock, firstBlockRange)

        // The first block is also the last one.
        if (encryptedLength == firstBlockRange.last -1)
          return Try.success(decryptedFirstBlock.size.toLong())

        val lastBlockLength = (encryptedLength % decryptor.minimumBlockSize)
          .takeUnless { it == 0L }
          ?: decryptor.minimumBlockSize.toLong()
        val lastBlockStart = encryptedLength - lastBlockLength
        val lastBlockRange = lastBlockStart until encryptedLength
        val encryptedLastBlock = resource.read(lastBlockRange).getOrThrow()
        val decryptedLastBlock = decrypt(encryptedLastBlock, lastBlockRange)

        val firstBlockDiff = encryptedFirstBlock.size - decryptedFirstBlock.size
        val lastBlockDiff = encryptedLastBlock.size - decryptedLastBlock.size
        val decryptedLength = encryptedLength - firstBlockDiff - lastBlockDiff

        if (BuildConfig.DEBUG)
          check(decryptedLength < encryptedLength)

        decryptedLength
      }
    }

    override suspend fun close() {
      decryptor.close()
      resource.close()
    }

  }

}

private fun LongRange.coerceToPositiveIncreasing() =
  if (first >= last)
    0L until 0L
  else
    LongRange(first.coerceAtLeast(0), last.coerceAtLeast(0))

private fun LongRange.requireLengthFitInt() = require(last - first + 1 <= Int.MAX_VALUE)


