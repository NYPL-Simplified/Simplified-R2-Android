package org.librarysimplified.r2.drm.core

import org.readium.r2.shared.publication.ContentProtection

interface DrmServiceProvider : AutoCloseable {

    val name: String

    val contentProtection: ContentProtection

    override fun close()
}