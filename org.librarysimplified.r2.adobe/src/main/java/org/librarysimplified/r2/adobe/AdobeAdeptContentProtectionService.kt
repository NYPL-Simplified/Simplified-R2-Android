package org.librarysimplified.r2.adobe

import org.readium.r2.shared.publication.LocalizedString
import org.readium.r2.shared.publication.services.ContentProtectionService

internal class AdobeAdeptContentProtectionService : ContentProtectionService {
    override val credentials: String? = null

    override val isRestricted: Boolean = false

    override val name: LocalizedString = LocalizedString("Adobe Adept")

    override val rights: ContentProtectionService.UserRights = ContentProtectionService.UserRights.Unrestricted

}