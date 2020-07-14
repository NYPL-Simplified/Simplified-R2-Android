package org.librarysimplified.r2.adobe

data class AdobeAdeptEncryptionProperties(
    val algorithm: String,
    val resourceId: String,
    val compression: String?,
    val originalLength: Long?
)