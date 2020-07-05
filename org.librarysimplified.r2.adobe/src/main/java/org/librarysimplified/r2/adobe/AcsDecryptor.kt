package org.librarysimplified.r2.adobe

import org.readium.r2.shared.fetcher.FailureResource
import org.readium.r2.shared.fetcher.LazyResource
import org.readium.r2.shared.fetcher.Resource
import org.readium.r2.shared.fetcher.ResourceTry
import org.readium.r2.shared.fetcher.mapCatching
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.encryption.encryption
import java.lang.Exception

internal class AcsDecryptor(private val rights: String) {

  companion object {
    const val AcsAlgorithmCompressed = "http://www.w3.org/2001/04/xmlenc#aes128-cbc"
    const val AcsAlgorithmUncompressed = "http://ns.adobe.com/adept/xmlenc#aes128-cbc-uncompressed"

    init {
      System.loadLibrary("AcsDecryptor")
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
      FailureResource(link, Resource.Error.Forbidden)
    }
  }

  private class AcsResource(private val resource: Resource, private val link: Link, rights: String) : Resource {

    private val encryption = requireNotNull(link.properties.encryption)
    private val decryptorPtr: Long
    private var isClosed: Boolean = false

    init {
      val uri = link.href.toByteArray()
      val algo = encryption.algorithm.toByteArray()
      val length = encryption.originalLength
        ?.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }
        ?.toInt()
        ?: 0

      decryptorPtr = createDecryptor(uri, algo, length, rights.toByteArray())
        ?: throw Exception("Unable to instantiate an ACS Decryptor.")
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
      read().map { it.size.toLong() }

    override suspend fun close() {
      if (isClosed)
        return

      deleteDecryptor(decryptorPtr)
      isClosed = true
    }
  }

}

external fun createDecryptor(uri: ByteArray, algo: ByteArray, length: Int, rights: ByteArray): Long?

external fun readThroughDecryptor(decryptorPtr: Long, data: ByteArray, start: Long?, end: Long?): ByteArray?

external fun deleteDecryptor(decryptorPtr: Long)
