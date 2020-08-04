package org.librarysimplified.r2.drm.adobe

import android.content.Context
import org.librarysimplified.r2.drm.core.ContentProtectionProvider
import org.readium.r2.shared.publication.ContentProtection

class AdobeAdeptContentProtectionProvider : ContentProtectionProvider {

    override fun create(context: Context): ContentProtection = AdobeAdeptContentProtection()

}
