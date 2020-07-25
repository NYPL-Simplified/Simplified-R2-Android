package org.librarysimplified.r2.drm.adobe

import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import org.librarysimplified.r2.drm.core.DrmServiceProvider
import org.readium.r2.shared.publication.ContentProtection
import java.util.concurrent.Executors

class AdobeAdeptDrmServiceProvider : DrmServiceProvider {

    companion object {

        private val coroutineDispatcher: ExecutorCoroutineDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    }

    override val name = "Adobe Adept"

    override val contentProtection: ContentProtection = AdobeAdeptContentProtection(coroutineDispatcher)

    override fun close() = coroutineDispatcher.close()
}