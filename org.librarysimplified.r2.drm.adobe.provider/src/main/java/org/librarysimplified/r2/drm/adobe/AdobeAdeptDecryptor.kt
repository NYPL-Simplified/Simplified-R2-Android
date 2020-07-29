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
          encryption.resourceId,
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
    encryption: AdobeAdeptEncryptionProperties,
    rights: String
  ) : Resource {

    private var decryptor = org.nypl.drm.adobe.AdobeAdeptDecryptor(
      encryption.resourceId,
      encryption.algorithm,
      encryption.originalLength ?: 0,
      rights
    )

    private var _firstBlockFed: Boolean = false

    private lateinit var _length: ResourceTry<Long>

    init {
      if (encryption.originalLength != null)
        _length = Try.success(encryption.originalLength)
    }

    private lateinit var _decryptedFirstBlockLength:  ResourceTry<Long>

    override suspend fun link(): Link = resource.link()

    override suspend fun read(range: LongRange?): ResourceTry<ByteArray> {
      if (range == null)
        return Try.wrap { decrypt(range) }

      return Try.wrap {
        val length = length().getOrThrow()

        @Suppress("NAME_SHADOWING")
        val range = range
          .coerceToPositiveIncreasing()
          .requireLengthFitInt()
          .coerceLastAtMost(length - 1)

        if (range.first == 0L && range.last == length - 1)
          decrypt(range)
        else
          readRange(range)
      }
    }

    private suspend fun readRange(range: LongRange): ByteArray {
      val minBlockSize = decryptor.minimumBlockSize.toLong()
      val encryptedLength = resource.length().getOrThrow()

      // The decrypted first block is missingSize shorter than the encrypted first block
      val missingSize = minBlockSize - decryptedFirstBlockLength().getOrThrow()

      // Will the first block be read to provide the desired range
      val readFirstBlock = (range.first + missingSize).floorMultipleOf(minBlockSize) == 0L

      val shiftedRange = (range.first + if (readFirstBlock) 0 else missingSize)..(range.last + missingSize)
      val encryptedRangeStart = shiftedRange.first.floorMultipleOf(minBlockSize)
      val encryptedRangeEnd = (shiftedRange.last + 1).ceilMultipleOf(minBlockSize).coerceAtMost(encryptedLength)

      val decryptedContent = decrypt(encryptedRangeStart until encryptedRangeEnd)

      // Pick the decrypted data dropping blocks added to reach multiples of minBlockSize
      val sliceStart = (shiftedRange.first - encryptedRangeStart).toInt()

      val slice = sliceStart..(sliceStart + range.last - range.first).toInt()
      return decryptedContent.sliceArray(slice)
    }

    private suspend fun decrypt(range: LongRange?): ByteArray {
      val cipheredData = resource.read(range).getOrThrow()
      val length = resource.length().getOrThrow()

      val previousBlock =
        if (range == null || range.first == 0L)
          null
        else
          resource.read(range.first - decryptor.minimumBlockSize until range.first).getOrThrow()

      if (previousBlock != null && !_firstBlockFed) {
          // This should happen only when calling from decryptedFirstBlockLength() for the first time
          // since either we have a full request and previousBlock == null
          // or the first block must have been fed while computing length
          val firstBlock = resource.read(0L until decryptor.minimumBlockSize).getOrThrow()
          decryptor.decrypt(firstBlock, null, firstBlock.size.toLong() == length)
         _firstBlockFed = true
      }

      val isLastBlock = range == null || range.last == length - 1

      logger.debug("attempting to decrypt range $range")
      logger.debug("ciphered data size ${cipheredData.size}")
      logger.debug("calling decrypt with previousBlock is null = ${previousBlock == null} and isLastBlock = $isLastBlock")
      return decryptor.decrypt(cipheredData, previousBlock, isLastBlock)
    }

    override suspend fun length(): ResourceTry<Long> {
      if (::_length.isInitialized)
        return _length

      logger.debug("computing resource length")
      _length = Try.wrap {
        val encryptedLength = resource.length().getOrThrow()
        val firstBlockRange = 0L until kotlin.math.min(encryptedLength, decryptor.minimumBlockSize.toLong())
        val decryptedFirstBlockLength = decryptedFirstBlockLength().getOrThrow()

        // The first block is also the last one.
        if (encryptedLength == firstBlockRange.last -1)
          return Try.success(decryptedFirstBlockLength)

        val lastBlockLength = (encryptedLength % decryptor.minimumBlockSize)
          .takeUnless { it == 0L }
          ?: decryptor.minimumBlockSize.toLong()
        val lastBlockStart = encryptedLength - lastBlockLength
        val lastBlockRange = lastBlockStart until encryptedLength
        val decryptedLastBlock = decrypt(lastBlockRange)

        val encryptedFirstBlock = resource.read(firstBlockRange).getOrThrow()
        val firstBlockDiff = encryptedFirstBlock.size - decryptedFirstBlockLength
        val encryptedLastBlock = resource.read(lastBlockRange).getOrThrow()
        val lastBlockDiff = encryptedLastBlock.size - decryptedLastBlock.size
        val decryptedLength = encryptedLength - firstBlockDiff - lastBlockDiff
        logger.debug("computed length $decryptedLength")
        decryptedLength
      }

      return _length
    }

    override suspend fun close() {
      decryptor.close()
      resource.close()
    }

    private suspend fun decryptedFirstBlockLength(): ResourceTry<Long> {
      if (::_decryptedFirstBlockLength.isInitialized)
        return _decryptedFirstBlockLength

      _decryptedFirstBlockLength = Try.wrap {
        val encryptedLength = resource.length().getOrThrow()
        val encryptedFirstBlockLength = kotlin.math.min(encryptedLength, decryptor.minimumBlockSize.toLong())
        decrypt( 0L until encryptedFirstBlockLength).size.toLong()
      }

      return _decryptedFirstBlockLength
    }
  }
}


private inline fun <S> Try.Companion.wrap(compute: () -> S): ResourceTry<S> =
  try {
    success(compute())
  } catch (e: Resource.Error) {
    failure(e)
  } catch (e: DRMException) {
    failure(Resource.Error.Forbidden)
  } catch (e: Exception) {
    failure(Resource.Error.Other(e))
  }


private fun LongRange.coerceToPositiveIncreasing() =
  if (first >= last)
    0L until 0L
  else
    LongRange(first.coerceAtLeast(0), last.coerceAtLeast(0))

private fun LongRange.coerceLastAtMost(max: Long) =
  LongRange(first, last.coerceAtMost(max))

private fun LongRange.requireLengthFitInt() =
  this.apply { require(last - first + 1 <= Int.MAX_VALUE) }



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

