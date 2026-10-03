package com.jev.probe.capture

import android.content.res.Resources
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.jev.probe.core.ChatSnapshot
import com.jev.probe.core.Msg

/**
 * Soul (cn.soulapp.android) adapter. Field-verified 2026-09-29 via uiautomator
 * dump (vivo V2464A / Android 16): bodies are `cn.soulapp.android:id/content_text`
 * inside `cn.soulapp.android:id/item_root` rows; title `...:id/tv_title`; input
 * box `...:id/et_sendmessage`; per-row avatar `...:id/avatar`. Not obfuscated.
 *
 * "In a chat window" = the input box id exists (QQ-style: the app is a single
 * activity, so the tree decides). Input present but no readable bodies →
 * empty snapshot, the OCR fallback's cue.
 *
 * Sender side: the avatar sits hard left (x≈15/1080) of the row for the other
 * person, hard right (x≈888) for me — verified 8/8 bubbles. Comparing the
 * avatar's centerX against the row's centerX stays correct even when a long
 * message pushes its bubble past mid-screen. The avatar is searched only
 * inside the same item_root row, so the title bar's `chat_avatar` never matches.
 */
class SoulAdapter : ChatAppAdapter {

    override val pkg: String = "cn.soulapp.android"

    override fun extract(root: AccessibilityNodeInfo, res: Resources): ChatSnapshot? {
        val rows = root.findAccessibilityNodeInfosByViewId(ITEM_ID).orEmpty()
        val hasInput = root.findAccessibilityNodeInfosByViewId(INPUT_ID)?.isNotEmpty() == true
        if (!hasInput && rows.isEmpty()) return null

        var title: String? = null
        root.findAccessibilityNodeInfosByViewId(TITLE_ID)?.firstOrNull()
            ?.text?.toString()?.let { if (it.isNotBlank()) title = it }

        val bubbles = ArrayList<Bubble>()
        for (row in rows) {
            val rowBounds = Rect()
            row.getBoundsInScreen(rowBounds)

            val body = row.findAccessibilityNodeInfosByViewId(BODY_ID)?.firstOrNull()
                ?: continue
            val text = body.text?.toString()?.trim().orEmpty()
            if (text.isEmpty()) continue

            val avatarX: Int = row.findAccessibilityNodeInfosByViewId(AVATAR_ID)
                ?.firstOrNull()
                ?.let { avatar ->
                    val avatarBounds = Rect()
                    avatar.getBoundsInScreen(avatarBounds)
                    avatarBounds.centerX()
                }
                ?: continue

            val side = if (avatarX < rowBounds.centerX()) "other" else "me"

            val bodyBounds = Rect()
            body.getBoundsInScreen(bodyBounds)
            bubbles.add(Bubble(bodyBounds.top, text, side))
        }
        if (bubbles.isEmpty()) return ChatSnapshot(title, emptyList())

        bubbles.sortBy { it.top }
        return ChatSnapshot(title, bubbles.map { Msg(it.side, it.text) })
    }

    private data class Bubble(val top: Int, val text: String, val side: String)

    companion object {
        private const val ITEM_ID = "cn.soulapp.android:id/item_root"
        private const val BODY_ID = "cn.soulapp.android:id/content_text"
        private const val AVATAR_ID = "cn.soulapp.android:id/avatar"
        private const val TITLE_ID = "cn.soulapp.android:id/tv_title"
        private const val INPUT_ID = "cn.soulapp.android:id/et_sendmessage"
    }
}
