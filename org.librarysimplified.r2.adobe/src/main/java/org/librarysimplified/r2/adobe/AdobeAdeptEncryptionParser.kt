/*
 * Module: r2-streamer-kotlin
 * Developers: Aferdita Muriqi, Clément Baumann, Quentin Gliosca
 *
 * Copyright (c) 2018. Readium Foundation. All rights reserved.
 * Use of this source code is governed by a BSD-style license which is detailed in the
 * LICENSE file present in the project repository where this source code is maintained.
 */

package org.librarysimplified.r2.adobe

import org.readium.r2.shared.normalize
import org.readium.r2.shared.parser.xml.ElementNode

/**
 * AcsEncryptionParser parses Epub encryption.xml files with the following Adobe's extension :
 * every enc:EncryptedData node has an adept:resource child that contains an uri that is
 * requested by ACS connector to instantiate a decryptor.
 */
internal object AdobeAdeptEncryptionParser {

    object Namespaces {
        const val ENC = "http://www.w3.org/2001/04/xmlenc#"
        const val SIG = "http://www.w3.org/2000/09/xmldsig#"
        const val COMP = "http://www.idpf.org/2016/encryption#compression"
        const val ADEPT = "http://ns.adobe.com/adept"
    }

    fun parse(document: ElementNode): Map<String, AdobeAdeptEncryptionProperties> =
        document.get("EncryptedData", Namespaces.ENC)
            .mapNotNull { parseEncryptedData(it) }
            .toMap()

    private fun parseEncryptedData(node: ElementNode): Pair<String, AdobeAdeptEncryptionProperties>? {
        val resourceURI = node.getFirst("CipherData", Namespaces.ENC)
            ?.getFirst("CipherReference", Namespaces.ENC)?.getAttr("URI")
            ?: return null
        val resourceId = node.getFirst("KeyInfo", Namespaces.SIG)
            ?.getFirst("resource", Namespaces.ADEPT)
            ?.text
            ?: return null
        val algorithm = node.getFirst("EncryptionMethod", Namespaces.ENC)
            ?.getAttr("Algorithm")
            ?: return null
        val compression = node.getFirst("EncryptionProperties", Namespaces.ENC)
            ?.let { parseEncryptionProperties(it) }
        val originalLength = compression?.first
        val compressionMethod = compression?.second
        val enc = AdobeAdeptEncryptionProperties(
            algorithm = algorithm,
            resourceId = resourceId,
            compression = compressionMethod,
            originalLength = originalLength
        )
        return Pair(normalize("/", resourceURI), enc)
    }

    private fun parseEncryptionProperties(encryptionProperties: ElementNode): Pair<Long, String>? {
        for (encryptionProperty in encryptionProperties.get("EncryptionProperty", Namespaces.ENC)) {
            val compressionElement = encryptionProperty.getFirst("Compression", Namespaces.COMP)
            if (compressionElement != null) {
                parseCompressionElement(compressionElement)?.let { return it }
            }
        }
        return null
    }

    private fun parseCompressionElement(compressionElement: ElementNode): Pair<Long, String>? {
        val originalLength = compressionElement.getAttr("OriginalLength")?.toLongOrNull()
            ?: return null
        val method = compressionElement.getAttr("Method")
            ?: return null
        val compression = if (method == "8") "deflate" else "none"
        return Pair(originalLength, compression)
    }
}
