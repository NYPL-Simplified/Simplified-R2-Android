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
      CbcAdeptResource(resource, encryptionProps)
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
      readByOrderedChunks()
      //_read(range)

    suspend fun _read(range: LongRange?): ResourceTry<ByteArray> =
      resource.read(range).mapCatching {
        try {
          val isStart = range == null || range.first == 0L
          val isEnd =  range == null || range.last == resource.length().getOrThrow() - 1
          logger.debug("Calling decryptRange with isStart = $isStart and isEnd = $isEnd")
          decryptor.decryptRange(it, isStart, isEnd)
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

    // The following methods are intended for testing purposes.
    suspend fun readByOrderedChunks(): ResourceTry<ByteArray> {
      val length = resource.length().getOrThrow()
      logger.debug("resource length $length")
      var offset = 0L
      val buffer = ByteArrayOutputStream(length.toInt())
      while(offset < length) {
        val range = offset until kotlin.math.min(offset + 4096, length)
        logger.debug("range $range")
        logger.debug("range length ${range.last - range.first + 1}")

        /*if (offset > 0 && offset + 4096 < length)
          read */

        val decryptedBytes = _read(range).getOrThrow()
        logger.debug("decryptedBytes length ${decryptedBytes.size}")
        offset += 4096
        buffer.write(decryptedBytes, 0, decryptedBytes.size)
      }
      return Try.success(buffer.toByteArray())
    }

    suspend fun readByUnorderedChunks(keepFirstBlockAtTheBeginning: Boolean = false): ResourceTry<ByteArray> {
      val length = resource.length().getOrThrow()
      logger.debug("resource length $length")
      val blockNb =  ceil(length / 4096.toDouble()).toInt()
      val blocks = (0 until blockNb)
        .map { Pair(it, it * 4096L until kotlin.math.min(length, (it + 1)  * 4096L)) }
        .toMutableList()

      if (blocks.size > 1) {
        // Forbid the true order
        while (blocks.map(Pair<Int, LongRange>::first) == (0 until blockNb).toList())
          blocks.shuffle()
      }

      if (keepFirstBlockAtTheBeginning)
        blocks.apply {
          val originalFirstIndex = indexOfFirst { it.first == 0 }
          val actualFirstItem = first()
          set(0, get(originalFirstIndex))
          set(originalFirstIndex, actualFirstItem)
        }

      logger.debug("blocks $blocks")
      val decryptedBlocks = blocks.map {
        logger.debug("block index ${it.first}")
        val decryptedBytes= _read(it.second).getOrThrow()
        logger.debug("decryptedBytes size ${decryptedBytes.size}")
        Pair(it.first, decryptedBytes)
      }.sortedBy(Pair<Int, ByteArray>::first)
        .map(Pair<Int, ByteArray>::second)

      val buffer = ByteArrayOutputStream(length.toInt())
      decryptedBlocks.forEach {
        buffer.write(it, 0, it.size)
      }

      return Try.success(buffer.toByteArray())
    }

  }

  // Although they might be static, native methods are kept inside the class to avoid weird JNI names.

  external fun createDecryptor(resourceId: ByteArray, algoName: ByteArray, originalLength: Long, rights: ByteArray): Long

  external fun readThroughDecryptor(decryptorPtr: Long, data: ByteArray, isStart: Boolean, isEnd: Boolean): ByteArray

  external fun deleteDecryptor(decryptorPtr: Long)

}
