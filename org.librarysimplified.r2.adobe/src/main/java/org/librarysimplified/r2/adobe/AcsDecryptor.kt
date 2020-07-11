package org.librarysimplified.r2.adobe

import org.readium.r2.shared.fetcher.FailureResource
import org.readium.r2.shared.fetcher.LazyResource
import org.readium.r2.shared.fetcher.Resource
import org.readium.r2.shared.fetcher.ResourceTry
import org.readium.r2.shared.fetcher.mapCatching
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.encryption.encryption
import org.slf4j.LoggerFactory

internal class AcsDecryptor(private val rights: String) {

  companion object {

    private val logger =
      LoggerFactory.getLogger(AcsDecryptor::class.java)

    const val AcsAlgorithmCompressed = "http://www.w3.org/2001/04/xmlenc#aes128-cbc"
    const val AcsAlgorithmUncompressed = "http://ns.adobe.com/adept/xmlenc#aes128-cbc-uncompressed"

    init {
      logger.debug("attempting to load epub3 library")
      System.loadLibrary("epub3")

      logger.debug("attempting to load nypl_adobe library")
      System.loadLibrary("nypl_adobe")

      logger.debug("attempting to load nypl_adobe_filter DRM library")
      System.loadLibrary("nypl_adobe_filter")
    }
  }

  fun transform(resource: Resource): Resource = LazyResource {
    val link = resource.link()
    val encryption = link.properties.encryption
    if (encryption == null || encryption.algorithm !in listOf(AcsAlgorithmCompressed, AcsAlgorithmUncompressed))
      return@LazyResource resource

    return@LazyResource try {
      AcsResource(resource, link, rights)
    } catch (e: Exception) {
      logger.error("unable to instantiate an AcsResource", e)
      FailureResource(link, Resource.Error.Forbidden)
    }
  }

  private inner class AcsResource(private val resource: Resource, private val link: Link, rights: String) : Resource {

    private val decryptorPtr: Long
    private var isClosed: Boolean = false

    init {
      val encryption = requireNotNull(link.properties.encryption)
      logger.debug("initializing a resource for href ${link.href}")
      logger.debug("resource is encrypted with ${encryption.algorithm}")
      logger.debug("originalLength is ${encryption.originalLength}")

      val resourceId = requireNotNull((link.properties["encrypted"] as? Map<*, *>)?.get("resourceId") as? String)
        { "Missing resource id in encryption properties." }
        .toByteArray()
      val algoName = encryption.algorithm.toByteArray()
      val originalLength = encryption.originalLength
        ?.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }
        ?.toInt()
        ?: 0

      decryptorPtr = createDecryptor(resourceId, algoName, originalLength, rights.toByteArray())
      if (decryptorPtr == 0L)
        throw Exception("Unable to instantiate an ACS Decryptor.")

      logger.debug("decryptorPtr")
      logger.debug(decryptorPtr.toString())
    }

    override suspend fun link(): Link = link

    override suspend fun read(range: LongRange?): ResourceTry<ByteArray> {
      check(!isClosed) { "Resource is closed." }

      return resource.read(range).mapCatching {
        readThroughDecryptor(decryptorPtr, it, range?.start, range?.last)
          ?: throw Exception("Unable to decrypt data with ACS Connector.")
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

  external fun createDecryptor(resourceId: ByteArray, algoName: ByteArray, originalLength: Int, rights: ByteArray): Long

  external fun readThroughDecryptor(decryptorPtr: Long, data: ByteArray, start: Long?, end: Long?): ByteArray?

  external fun deleteDecryptor(decryptorPtr: Long)

}
