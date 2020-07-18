package org.librarysimplified.r2.drm.core

import org.readium.r2.shared.format.Format
import java.io.File

class DrmProtectedFile(file: File, val adobeRightsFile: File?)
    : org.readium.r2.shared.util.File(path = file.path, format = Format.EPUB)