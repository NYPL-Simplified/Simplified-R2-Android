package org.librarysimplified.r2.drm.adobe

import org.readium.r2.shared.fetcher.Resource
import org.readium.r2.shared.fetcher.ResourceTry
import org.readium.r2.shared.fetcher.mapCatching
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.encryption.encryption
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.getOrElse
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.lang.Exception
import kotlin.math.ceil
import kotlin.math.roundToInt

private val logger = LoggerFactory.getLogger(AdobeAdeptDecryptor::class.java)

suspend fun Publication.checkDecryption() {
    var failures = 0

    logger.debug("checking resources are readable in one block")
    val protectedLinks = (readingOrder + resources).filter(Link::isAdeptProtected)

    for (link in protectedLinks) {
        logger.debug("attempting to read ${link.href} in one block")
        get(link).use { resource ->
            resource.read().onFailure {
                logger.error("failed to read ${link.href} in one block", it)
                failures += 1
            }
        }
    }

    logger.debug("checking resources can be read in one block twice")
    for (link in protectedLinks) {
        get(link).use {
            val b1 = it.read().getOrThrow()
            val b2 = it.read().getOrThrow()
            try {
                check(b1.contentEquals(b2))
            } catch (e: Exception) {
                logger.error("two readings of ${link.href} lead to a different result", it)
                failures += 1
            }
        }
    }

    logger.debug("checking length computation is correct")
    for (link in protectedLinks) {
        val trueLength = get(link).use { it.read().getOrThrow().size.toLong() }
        get(link).use { resource ->
            resource.length().onFailure {
                logger.error("failed to compute length of ${link.href}")
                failures += 1
            }.onSuccess {
                try {
                    check(it == trueLength)
                } catch (e: Exception) {
                    logger.error("computed resource length seems to be wrong", it)
                    failures += 1
                }
            }
        }
    }

    logger.debug("checking all uncompressed resources are readable by chunks")
    val uncompressedLinks = (readingOrder + resources).filter(Link::isAdeptUncompressed)

    for (link in uncompressedLinks) {
        logger.debug("attempting to read ${link.href} by chunks ")
        val groundTruth = get(link).use { it.read() }.getOrThrow()
        for (chunkSize in listOf(2048L, 4096L, 8192L, 10000L)) {
            get(link).use { resource ->
                resource.readByChunks(chunkSize, groundTruth).onFailure {
                    logger.error("failed to read ${link.href} by chunks of size $chunkSize", it)
                    failures += 1
                }
            }
        }
    }

    logger.info("$failures tests failed")
}

private suspend fun Resource.readByChunks(chunkSize: Long, groundTruth: ByteArray, shuffle: Boolean = true): ResourceTry<ByteArray> =
    length().mapCatching { length ->
        val blockNb =  ceil(length / chunkSize.toDouble()).toInt()
        val blocks = (0 until blockNb)
            .map { Pair(it, it * chunkSize until kotlin.math.min(length, (it + 1)  * chunkSize)) }
            .toMutableList()

        if (blocks.size > 1 && shuffle) {
            // Forbid the true order
            while (blocks.map(Pair<Int, LongRange>::first) == (0 until blockNb).toList())
                blocks.shuffle()
        }

        logger.debug("blocks $blocks")
        val decryptedBlocks = blocks.map {
            logger.debug("block index ${it.first}")
            val decryptedBytes = read(it.second).getOrThrow()
            check(decryptedBytes.contentEquals(groundTruth.sliceArray(it.second.map(Long::toInt))))
            { "failed to decrypt block ${it.first}: ${it.second}" }
            Pair(it.first, decryptedBytes)
        }.sortedBy(Pair<Int, ByteArray>::first)
            .map(Pair<Int, ByteArray>::second)

        val buffer = ByteArrayOutputStream(length.toInt())
        decryptedBlocks.forEach {
            buffer.write(it, 0, it.size)
        }

        buffer.toByteArray()
    }

private val Link.isAdeptUncompressed: Boolean
    get() = properties.encryption?.algorithm == AdobeAdeptDecryptor.AdeptAlgorithmUncompressed

private val Link.isAdeptCompressed: Boolean
    get() = properties.encryption?.algorithm == AdobeAdeptDecryptor.AdeptAlgorithmCompressed

private val Link.isAdeptProtected: Boolean
    get() = isAdeptUncompressed || isAdeptCompressed
