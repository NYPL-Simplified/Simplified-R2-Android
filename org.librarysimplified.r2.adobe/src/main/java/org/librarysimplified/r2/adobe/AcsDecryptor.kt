package org.librarysimplified.r2.adobe

import org.readium.r2.shared.fetcher.FailureResource
import org.readium.r2.shared.fetcher.LazyResource
import org.readium.r2.shared.fetcher.Resource
import org.readium.r2.shared.fetcher.ResourceTry
import org.readium.r2.shared.fetcher.mapCatching
import org.readium.r2.shared.publication.Link
import org.slf4j.LoggerFactory

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
        readThroughDecryptor(decryptorPtr, it, range?.start, range?.last)
          ?.takeIf { it.isNotEmpty() }
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
  }

  // Although they might be static, native methods are kept inside the class to avoid weird JNI names.

  external fun createDecryptor(resourceId: ByteArray, algoName: ByteArray, originalLength: Long, rights: ByteArray): Long

  external fun readThroughDecryptor(decryptorPtr: Long, data: ByteArray, start: Long?, end: Long?): ByteArray?

  external fun deleteDecryptor(decryptorPtr: Long)

}
