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

    private var decryptor = org.nypl.drm.adobe.AdobeAdeptDecryptor(
      requireNotNull(encryption.resourceId),
      encryption.algorithm,
      encryption.originalLength ?: 0,
      rights
    )

    private var firstBlockFed: Boolean = false

    private lateinit var _originalLength: ResourceTry<Long>

    private lateinit var _encryptedLength:  ResourceTry<Long>

    // Excepted when data size <= decryptor.minimumBlockSize,
    // this might always be decryptor.minimumBlockSize - 32.
    // It should be checked with other books.
    private lateinit var _decryptedFirstBlockLength:  ResourceTry<Long>

    override suspend fun link(): Link = resource.link()

    override suspend fun read(range: LongRange?): ResourceTry<ByteArray> =
      length().mapCatching { length ->
        @Suppress("NAME_SHADOWING")
        val range = range
            ?.coerceToPositiveIncreasing()
            ?.apply { requireLengthFitInt() }
            ?.let { it.first..it.last.coerceAtMost(length - 1) }

        if (range == null || range.first == 0L && range.last == length - 1)
          decrypt(resource.read().getOrThrow(), range)
        else
          readRange(range)
      }

    private suspend fun readRange(range: LongRange): ByteArray {
      val minBlockSize = decryptor.minimumBlockSize.toLong()
      val encryptedLength = encryptedLength().getOrThrow()
      val decryptedFirstBlockLength = decryptedFirstBlockLength().getOrThrow()
      val missingSize = minBlockSize - decryptedFirstBlockLength

      return if (range.first < decryptedFirstBlockLength)  {
        val encryptedRangeEnd = (range.last + 1 + missingSize).ceilMultipleOf(minBlockSize).coerceAtMost(encryptedLength)
        val encryptedRange = 0 until encryptedRangeEnd
        val slice = range.map(Long::toInt)
        val decryptedContent = decrypt(resource.read(encryptedRange).getOrThrow(), encryptedRange)
        decryptedContent.sliceArray(slice)
      } else if ((range.first + missingSize).floorMultipleOf(minBlockSize) == 0L) {
        val encryptedRangeEnd = (range.last + 1 + missingSize).ceilMultipleOf(minBlockSize).coerceAtMost(encryptedLength)
        val encryptedRange = 0 until encryptedRangeEnd
        val sliceStart = range.first.toInt()
        val slice = sliceStart..(sliceStart + range.last - range.first).toInt()
        val decryptedContent = decrypt(resource.read(encryptedRange).getOrThrow(), encryptedRange)
        decryptedContent.sliceArray(slice)
      } else {
        @Suppress("NAME_SHADOWING")
        val range = (range.first + missingSize)..(range.last + missingSize)
        val encryptedRangeStart = range.first.floorMultipleOf(minBlockSize)
        val encryptedRangeEnd = (range.last + 1).ceilMultipleOf(minBlockSize).coerceAtMost(encryptedLength)
        val encryptedRange = encryptedRangeStart until encryptedRangeEnd
        val sliceStart = (range.first - encryptedRange.first).toInt()
        val slice = sliceStart..(sliceStart + range.last - range.first).toInt()
        val decryptedContent = decrypt(resource.read(encryptedRange).getOrThrow(), encryptedRange)
        decryptedContent.sliceArray(slice)
      }
    }

    private suspend fun tryDecrypt(cipheredData: ByteArray, range: LongRange?): ResourceTry<ByteArray> =
      try {
          Try.success(decrypt(cipheredData, range))
      } catch (e: DRMException) {
        Try.failure(Resource.Error.Forbidden)
      }

    private suspend fun decrypt(cipheredData: ByteArray, range: LongRange?): ByteArray {
      val length = encryptedLength().getOrThrow()

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

    private suspend fun decryptedFirstBlockLength(): ResourceTry<Long> {
      if (::_decryptedFirstBlockLength.isInitialized)
        return _decryptedFirstBlockLength

      val encryptedFirstBlockLength = kotlin.math.min(encryptedLength().getOrThrow(), decryptor.minimumBlockSize.toLong())
      val firstBlockRange = 0L until encryptedFirstBlockLength
      val encryptedFirstBlock = resource.read(firstBlockRange).getOrThrow()

      _decryptedFirstBlockLength = tryDecrypt(encryptedFirstBlock, firstBlockRange).map { it.size.toLong() }

      return _decryptedFirstBlockLength
    }

    private suspend fun encryptedLength(): ResourceTry<Long> {
      if (::_encryptedLength.isInitialized)
        return _encryptedLength

      _encryptedLength = resource.length()
      return _encryptedLength
    }

    override suspend fun length(): ResourceTry<Long> {
      if (::_originalLength.isInitialized)
        return _originalLength

      if (encryption.originalLength != null) {
        _originalLength = Try.success(encryption.originalLength)
        return _originalLength
      }

      logger.debug("Computing length")
      _originalLength = encryptedLength().mapCatching { encryptedLength ->
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
        logger.debug("length computed to $decryptedLength")

        decryptedLength
      }

      return _originalLength
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


private fun LongRange.requireLengthFitInt() =
  require(last - first + 1 <= Int.MAX_VALUE)


private fun LongRange.shift(offset: Long) =
  (start + offset)..(last + offset)


private fun Long.ceilMultipleOf(divisor: Long) =
  if (this % divisor == 0L)
    this
  else
    divisor * (this / divisor + 1)


private fun Long.floorMultipleOf(divisor: Long) = when {
  this % divisor == 0L -> this
  this < divisor -> 0
  else -> divisor  * (this / divisor)
}


