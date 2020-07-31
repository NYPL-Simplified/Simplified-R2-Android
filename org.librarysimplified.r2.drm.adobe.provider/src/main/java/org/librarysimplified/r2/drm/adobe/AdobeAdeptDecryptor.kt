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

    logger.debug("attempting to instantiate a decryption Resource for href ${link.href}")
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
    private lateinit var _length: ResourceTry<Long>

    init {
      if (encryption.originalLength != null)
        _length = Try.success(encryption.originalLength)
    }

    private lateinit var _firstBlockLack: ResourceTry<Long>

    private lateinit var _lastBlockExcess: ResourceTry<Long>

    override suspend fun link(): Link = resource.link()

    override suspend fun read(range: LongRange?): ResourceTry<ByteArray> {
      @Suppress("NAME_SHADOWING")
      val range = range
        ?.coerceToPositiveIncreasing()
        ?.requireLengthFitInt()

      return Try.wrap {
        if (range == null)
          decrypt(range)
        else
          readRange(range)
      }
    }

    private suspend fun readRange(range: LongRange): ByteArray {
      val minBlockSize = decryptor.minimumBlockSize.toLong()
      val encryptedLength = resource.length().getOrThrow()

      // The decrypted first block is missingSize shorter than the encrypted first block
      val missingSize = firstBlockLack().getOrThrow()

      // Will the first block be read to provide the desired range
      val readFirstBlock = (range.first + missingSize).floorMultipleOf(minBlockSize) == 0L

      val shiftedStart = (range.first + if (readFirstBlock) 0 else missingSize)
      val shiftedEnd = (range.last + 1 + missingSize).coerceAtMost(encryptedLength)
      val encryptedStart = shiftedStart.floorMultipleOf(minBlockSize)
      val encryptedEnd = shiftedEnd.ceilMultipleOf(minBlockSize).coerceAtMost(encryptedLength)

      val decryptedContent = decrypt(encryptedStart until encryptedEnd)

      // Pick the decrypted data dropping blocks added to reach multiples of minBlockSize
      val sliceStart = (shiftedStart - encryptedStart).toInt()
      var sliceEnd = (decryptedContent.size - (encryptedEnd - shiftedEnd)).toInt()

      if (shiftedEnd < encryptedLength && encryptedEnd >= encryptedLength)
        sliceEnd -= lastBlockExcess().getOrThrow().toInt()

      return decryptedContent.sliceArray(sliceStart until sliceEnd)
    }

    private suspend fun decrypt(range: LongRange?): ByteArray {
      val cipheredData = resource.read(range).getOrThrow()
      val length = resource.length().getOrThrow()

      val previousBlock =
        if (range == null || range.first == 0L)
          null
        else
          resource.read(range.first - decryptor.minimumBlockSize until range.first).getOrThrow()

      if (previousBlock != null && !::_firstBlockLack.isInitialized)
        firstBlockLack()

      val isLastBlock = range == null || range.first + cipheredData.size == length

      logger.debug("attempting to decrypt range $range")
      logger.debug("ciphered data size ${cipheredData.size}")
      logger.debug("calling decrypt with previousBlock is null = ${previousBlock == null} and isLastBlock = $isLastBlock")
      return decryptor.decrypt(cipheredData, previousBlock, isLastBlock)
    }

    override suspend fun length(): ResourceTry<Long> {
      if (::_length.isInitialized)
        return _length

      _length = Try.wrap {
        val encryptedLength = resource.length().getOrThrow()
        val decryptedLength = encryptedLength - firstBlockLack().getOrThrow() + lastBlockExcess().getOrThrow()
        logger.debug("computed length $decryptedLength")
        decryptedLength
      }

      return _length
    }

    private suspend fun firstBlockLack(): ResourceTry<Long> {
      if (::_firstBlockLack.isInitialized)
        return _firstBlockLack

      _firstBlockLack = Try.wrap {
        val encryptedLength = resource.length().getOrThrow()
        val firstBlockRange = 0L until kotlin.math.min(encryptedLength, decryptor.minimumBlockSize.toLong())
        val encryptedFirstBlock = resource.read(firstBlockRange).getOrThrow()
        val decryptedFirstBlock = decrypt(firstBlockRange)
        val lack = (encryptedFirstBlock.size - decryptedFirstBlock.size).toLong()

        if (firstBlockRange.last == encryptedLength - 1)
          _lastBlockExcess = Try.success(-lack)

        lack
      }

      return _firstBlockLack
    }

    private suspend fun lastBlockExcess(): ResourceTry<Long> {
      if (::_lastBlockExcess.isInitialized)
        return _lastBlockExcess

      _lastBlockExcess = Try.wrap {
        val encryptedLength = resource.length().getOrThrow()
        val lastBlockLength = (encryptedLength % decryptor.minimumBlockSize)
          .takeUnless { it == 0L }
          ?: decryptor.minimumBlockSize.toLong()
        val lastBlockStart = encryptedLength - lastBlockLength
        val lastBlockRange = lastBlockStart until encryptedLength
        val decryptedLastBlock = decrypt(lastBlockRange)
        val encryptedLastBlock = resource.read(lastBlockRange).getOrThrow()
        (decryptedLastBlock.size - encryptedLastBlock.size).toLong()
      }

      return _lastBlockExcess
    }

    override suspend fun close() {
      decryptor.close()
      resource.close()
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

