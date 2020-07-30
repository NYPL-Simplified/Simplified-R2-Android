package org.librarysimplified.r2.drm.adobe

import org.readium.r2.shared.fetcher.Resource
import org.readium.r2.shared.fetcher.ResourceTry
import org.readium.r2.shared.fetcher.mapCatching
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.encryption.encryption
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.lang.IllegalStateException
import kotlin.math.ceil

private val logger = LoggerFactory.getLogger(AdobeAdeptDecryptor::class.java)


suspend fun Publication.checkDecryption() {

    checkResourcesAreReadableInOneBlock(this)

    checkResourcesCanBeReadInOneBlockTwice(this)

    checkLengthComputationIsCorrect(this)

    checkAllUncompressedResourcesAreReadableByChunks(this)

    checkExceedingRangesAreAllowed(this)
}

private suspend fun checkResourcesAreReadableInOneBlock(publication: Publication) {
    logger.debug("checking resources are readable in one block")

    (publication.readingOrder + publication.resources)
        .filter(Link::isAdeptProtected)
        .forEach { link ->
            logger.debug("attempting to read ${link.href} in one block")
            publication.get(link).use { resource ->
                val bytes = resource.read()
                check(bytes.isSuccess) { "failed to read ${link.href} in one block" }
            }
     }
}

private suspend fun checkResourcesCanBeReadInOneBlockTwice(publication: Publication) {
    logger.debug("checking resources can be read in one block twice")

    (publication.readingOrder + publication.resources)
        .filter(Link::isAdeptProtected)
        .forEach { link ->
            publication.get(link).use {
                val b1 = it.read().getOrThrow()
                val b2 = it.read().getOrThrow()
                check(b1.contentEquals(b2)) { "two readings of ${link.href} lead to a different result" }
            }
    }
}

private suspend fun checkLengthComputationIsCorrect(publication: Publication) {
    logger.debug("checking length computation is correct")

    (publication.readingOrder + publication.resources)
        .filter(Link::isAdeptProtected)
        .forEach { link ->
            val trueLength = publication.get(link).use { it.read().getOrThrow().size.toLong() }
            publication.get(link).use { resource ->
                val computedLength =  resource.length()
                check(computedLength.isSuccess) { "failed to compute length of ${link.href}" }
                check(computedLength.getOrThrow() == trueLength) { "computed length of ${link.href} seems to be wrong" }
            }
        }
}

private suspend fun checkAllUncompressedResourcesAreReadableByChunks(publication: Publication) {
    logger.debug("checking all uncompressed resources are readable by chunks")

    (publication.readingOrder + publication.resources)
        .filter(Link::isAdeptUncompressed)
        .forEach { link ->
            logger.debug("attempting to read ${link.href} by chunks ")
            val groundTruth = publication.get(link).use { it.read() }.getOrThrow()
            for (chunkSize in listOf(2048L, 4096L, 8192L, 10000L)) {
                publication.get(link).use { resource ->
                    resource.readByChunks(chunkSize, groundTruth).onFailure {
                        logger.error("grrr", it)
                        throw IllegalStateException("failed to read ${link.href} by chunks of size $chunkSize", it)
                    }
                }
            }
        }
}

private suspend fun checkExceedingRangesAreAllowed(publication: Publication) {
    logger.debug("checking exceeding ranges are allowed in CbcAdeptResource")

    (publication.readingOrder + publication.resources)
        .filter(Link::isAdeptUncompressed)
        .forEach { link ->
            publication.get(link).use { resource ->
                for (excess in listOf(100, 2048, 4096, 5028)) {
                    val length = resource.length().getOrThrow()
                    val bytes = resource.read(0 until length + excess).getOrThrow()
                    val truth = resource.read().getOrThrow()
                    check(bytes.contentEquals(truth)) {
                        "decryption of a range exceeding the length by $excess failed"
                    }
                }
            }
        }

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
            logger.debug("block index ${it.first}: ${it.second}")
            val decryptedBytes = read(it.second).getOrThrow()
            logger.debug("decrypted length: ${decryptedBytes.size}")
            logger.debug("expected length: ${groundTruth.sliceArray(it.second.map(Long::toInt)).size}")
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
