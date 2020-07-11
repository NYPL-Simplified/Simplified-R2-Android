package org.librarysimplified.r2.adobe

data class AcsEncryptionProperties(
    val algorithm: String,
    val resourceId: String,
    val compression: String?,
    val originalLength: Long?
)