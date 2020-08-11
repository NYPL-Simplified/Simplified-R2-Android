package org.librarysimplified.r2.drm.adobe

import org.readium.r2.shared.publication.LocalizedString
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.ContentProtectionService

internal class AdobeAdeptContentProtectionService(override val isRestricted: Boolean) : ContentProtectionService {

    override val error: Exception? = if (isRestricted) Exception("Unable to unlock publication") else null

    override val credentials: String? = null

    override val name: LocalizedString = LocalizedString("Adobe Adept")

    override val rights: ContentProtectionService.UserRights = ContentProtectionService.UserRights.Unrestricted

    companion object {

        fun createFactory(isRestricted: Boolean): (Publication.Service.Context) -> AdobeAdeptContentProtectionService =
            { AdobeAdeptContentProtectionService(isRestricted) }
    }

}