package org.librarysimplified.r2.adobe

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

internal class AcsDecryptor(private val rights: String, private val encryption: Map<String, AcsEncryptionProperties>) {

  companion object {

    private val logger = LoggerFactory.getLogger(AcsDecryptor::class.java)

    const val AcsAlgorithmCompressed = "http://www.w3.org/2001/04/xmlenc#aes128-cbc"
    const val AcsAlgorithmUncompressed = "http://ns.adobe.com/adept/xmlenc#aes128-cbc-uncompressed"

  }

  fun transform(resource: Resource): Resource = LazyResource {
    val link = resource.link()
    val encryptionProps = encryption[link.href]
    if (encryptionProps == null || encryptionProps.algorithm !in listOf(AcsAlgorithmCompressed, AcsAlgorithmUncompressed))
      return@LazyResource resource

    return@LazyResource try {
      logger.debug("attempting to instantiate an AcsResource")
      logger.debug("href is ${link.href}")
      logger.debug("algorithm is ${encryptionProps.algorithm}")
      logger.debug("originalLength is ${encryptionProps.originalLength}")
      AcsResource(resource, encryptionProps)
    } catch (e: Exception) {
      logger.error("unable to instantiate an AcsResource", e)
      FailureResource(link, Resource.Error.Forbidden)
    } catch(e: UnsatisfiedLinkError) {
      logger.error("DRM is not supported")
      resource
    }
  }

  private inner class AcsResource(private val resource: Resource, private val encryption: AcsEncryptionProperties) : Resource {

    private val decryptorPtr: Long
    private var isClosed: Boolean = false

    init {
      val resourceId = requireNotNull(encryption.resourceId).toByteArray()
      val algoName = encryption.algorithm.toByteArray()
      val originalLength = encryption.originalLength ?: 0

      decryptorPtr = createDecryptor(resourceId, algoName, originalLength, rights.toByteArray())
      if (decryptorPtr == 0L)
        throw Exception("Unable to instantiate an ACS Decryptor.")

      logger.debug("decryptorPtr")
      logger.debug(decryptorPtr.toString())
    }

    override suspend fun link(): Link = resource.link()

    override suspend fun read(range: LongRange?): ResourceTry<ByteArray> {
      check(!isClosed) { "Resource is closed." }

      return resource.read(range).mapCatching {
        readThroughDecryptor(decryptorPtr, it, isStart = true, isEnd = true)
          .takeIf { it.isNotEmpty() }
          ?: throw Resource.Error.Forbidden
      }
    }

    override suspend fun length(): ResourceTry<Long> =
      resource.length() //read().map { it.size.toLong() }

    override suspend fun close() {
      if (isClosed)
        return

      resource.close()
      deleteDecryptor(decryptorPtr)
      isClosed = true
    }

    // The following methods are intended for testing purposes.
    suspend fun readByOrderedChunks(): ResourceTry<ByteArray> {
      val length = resource.length().getOrThrow()
      logger.debug("resource length $length")
      var offset = 0L
      val buffer = ByteArrayOutputStream(length.toInt())
      while(offset < length) {
        logger.debug("offset $offset")
        val origBytes = resource.read(offset until offset + 4096).getOrThrow()
        logger.debug("origBytes size ${origBytes.size}")

        /*if (offset > 0 && offset + 4096 < length)
          readThroughDecryptor(decryptorPtr, origBytes, isStart = false, isEnd = false) */

        val decryptedBytes = readThroughDecryptor(decryptorPtr, origBytes, isStart = offset == 0L, isEnd = offset + 4096 >= length)
        logger.debug("decryptedBytes size ${decryptedBytes.size}")
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
        val origBytes = resource.read(it.second).getOrThrow()
        logger.debug("origBytes size ${origBytes.size}")
        val decryptedBytes = readThroughDecryptor(decryptorPtr, origBytes, isStart = it.first == 0, isEnd = it.first == blockNb - 1)
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
