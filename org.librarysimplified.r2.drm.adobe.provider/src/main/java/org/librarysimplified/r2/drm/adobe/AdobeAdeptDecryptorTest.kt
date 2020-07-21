package org.librarysimplified.r2.drm.adobe

import org.readium.r2.shared.fetcher.Resource
import org.readium.r2.shared.fetcher.ResourceTry
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.Try
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import kotlin.math.ceil

private val logger = LoggerFactory.getLogger(AdobeAdeptDecryptor::class.java)

suspend fun checkLittleYou(publication: Publication) {
    //Link(href=/OEBPS/audio/LittleYou.mp4, type=audio/mp4, templated=false, title=null, rels=[], properties=Properties(otherProperties={encrypted={algorithm=http://ns.adobe.com/adept/xmlenc#aes128-cbc-uncompressed}}), height=null, width=null, bitrate=null, duration=null, languages=[], alternates=[], children=[])
    logger.debug("Maintenant on s'occupe de /OEBPS/audio/LittleYou.mp4")
    val link = publication.linkWithHref("/OEBPS/audio/LittleYou.mp4")!!

    logger.debug("Read resource in one block")
    val bytes1 = publication.get(link).read().getOrThrow()
    logger.debug("Read resource in one block, length : ${bytes1.size}")

    logger.debug("Read resource by ordered chunks")
    val bytes2 = publication.get(link).readByOrderedChunks().getOrThrow()
    logger.debug("Read resource by ordered chunks, length : ${bytes2.size}")

    logger.debug("Read resource by unordered chunks")
    val bytes3 = publication.get(link).readByUnorderedChunks().getOrThrow()
    logger.debug("Read resource by unordered chunks, length : ${bytes3.size}")

    logger.debug("Check equality with ordered chunks")
    check(bytes1.contentEquals(bytes2))

    logger.debug("Check equality with unordered chunks")
    check(bytes1.contentEquals(bytes3))
}

suspend fun Resource.readByOrderedChunks(): ResourceTry<ByteArray> {
    val cipheredResourceLength = 889296L
    logger.debug("ciphered resource length $cipheredResourceLength")
    var offset = 0L
    val buffer = ByteArrayOutputStream(cipheredResourceLength.toInt())
    while(offset < cipheredResourceLength) {
        val range = offset until kotlin.math.min(offset + 4096, cipheredResourceLength)
        logger.debug("range $range")
        logger.debug("range length ${range.last - range.first + 1}")

        val decryptedBytes = read(range).getOrThrow()

        if (offset in 3000 until 300000) {
            if (offset == 32768L)
                logger.debug("offset == 32768")
            val bytesr = read(range).getOrThrow()
            check(bytesr.contentEquals(decryptedBytes))
        }

        logger.debug("decryptedBytes length ${decryptedBytes.size}")
        offset += 4096
        buffer.write(decryptedBytes, 0, decryptedBytes.size)
    }
    return Try.success(buffer.toByteArray())
}

suspend fun Resource.readByUnorderedChunks(
    keepFirstBlockAtTheBeginning: Boolean = true,
    keepLastBlockAtTheEnd: Boolean = true
): ResourceTry<ByteArray> {
    val cipheredResourceLength = 889296L
    logger.debug("ciphered resource length $cipheredResourceLength")
    val blockNb =  ceil(cipheredResourceLength / 4096.toDouble()).toInt()
    val blocks = (0 until blockNb)
        .map { Pair(it, it * 4096L until kotlin.math.min(cipheredResourceLength, (it + 1)  * 4096L)) }
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

    if (keepLastBlockAtTheEnd)
        blocks.apply {
            val originalLastIndex = indexOfFirst { it.first == blockNb - 1 }
            val actualLastItem = last()
            set(blockNb - 1, get(originalLastIndex))
            set(originalLastIndex, actualLastItem)
        }

    logger.debug("blocks $blocks")
    val decryptedBlocks = blocks.map {
        logger.debug("block index ${it.first}")
        val decryptedBytes = read(it.second).getOrThrow()
        logger.debug("decryptedBytes size ${decryptedBytes.size}")
        Pair(it.first, decryptedBytes)
    }.sortedBy(Pair<Int, ByteArray>::first)
        .map(Pair<Int, ByteArray>::second)

    val buffer = ByteArrayOutputStream(cipheredResourceLength.toInt())
    decryptedBlocks.forEach {
        buffer.write(it, 0, it.size)
    }

    return Try.success(buffer.toByteArray())
}