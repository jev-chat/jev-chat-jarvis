package com.jev.probe.jev

import java.util.Base64
import java.util.UUID

/**
 * Builds the image-related pieces of a Jev decisions request: extra state
 * fields, the `assets` entry, and the TypeSafe preview header.
 *
 * No network, no Android. [build] with a null jpeg returns empty extras so
 * the POST body stays `{model, state, questions}` — byte-for-byte the
 * pre-vision shape. [rank] never calls this with a jpeg.
 */
internal object JudgeRequest {

    const val HEADER_IMAGES = "TypeSafe-Danger-Zone-Images"
    const val REF_PREFIX = "jev-shot-"
    const val SCREENSHOT_NOTE =
        "The screenshot is the chat as shown on screen. The messages field is OCR text from the same screenshot and may contain recognition errors; trust the screenshot when they disagree."

    data class Asset(
        val ref: String,
        val type: String = "image",
        val format: String = "jpg",
        val dataBase64: String
    )

    /**
     * Image extras to splice into an otherwise unchanged decisions body.
     * [asset] == null means do not add `assets` or `screenshot*`.
     */
    data class Built(
        val headers: Map<String, String>,
        val screenshotRef: String?,
        val screenshotNote: String?,
        val asset: Asset?
    ) {
        val hasImage: Boolean get() = asset != null
    }

    fun newRefName(): String =
        REF_PREFIX + UUID.randomUUID().toString().replace("-", "").take(8)

    /** Standard base64, padded, no wraps, no `data:` prefix. */
    fun encodeBase64(jpeg: ByteArray): String =
        Base64.getEncoder().encodeToString(jpeg)

    fun headers(base: Map<String, String>, withImage: Boolean): Map<String, String> =
        if (withImage) base + (HEADER_IMAGES to "true") else base

    fun build(
        baseHeaders: Map<String, String>,
        jpeg: ByteArray?,
        refName: String = newRefName()
    ): Built {
        if (jpeg == null) return Built(baseHeaders, null, null, null)
        return Built(
            headers = headers(baseHeaders, true),
            screenshotRef = refName,
            screenshotNote = SCREENSHOT_NOTE,
            asset = Asset(ref = refName, dataBase64 = encodeBase64(jpeg))
        )
    }
}
