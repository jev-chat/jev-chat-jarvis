package com.jev.probe.core

import com.jev.probe.core.RegionProfiles.Companion.K_MIGRATED_V18_REGION
import com.jev.probe.core.RegionProfiles.Companion.K_REGION
import com.jev.probe.core.RegionProfiles.Companion.LIVE_JUDGE_BASE
import com.jev.probe.core.RegionProfiles.Companion.LIVE_JUDGE_KEY
import com.jev.probe.core.RegionProfiles.Companion.LIVE_JUDGE_MODEL
import com.jev.probe.core.RegionProfiles.Companion.LIVE_JUDGE_PROVIDER
import com.jev.probe.core.RegionProfiles.Companion.LIVE_REPLY_BASE
import com.jev.probe.core.RegionProfiles.Companion.LIVE_REPLY_KEY
import com.jev.probe.core.RegionProfiles.Companion.LIVE_REPLY_MODEL
import com.jev.probe.core.RegionProfiles.Companion.REGION_CN
import com.jev.probe.core.RegionProfiles.Companion.REGION_INTL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RegionProfilesTest {

    private fun seedLive(
        store: KvStore,
        provider: String,
        judgeKey: String = "jk",
        judgeBase: String? = null,
        judgeModel: String? = null,
        replyBase: String = Prefs.DEFAULT_REPLY_BASE,
        replyKey: String = "",
        replyModel: String = Prefs.DEFAULT_REPLY_MODEL,
    ) {
        store.put(LIVE_JUDGE_PROVIDER, provider)
        store.put(LIVE_JUDGE_KEY, judgeKey)
        store.put(
            LIVE_JUDGE_BASE,
            judgeBase ?: when (provider) {
                Prefs.PROVIDER_BOCHA -> Prefs.DEFAULT_JUDGE_BASE_BOCHA
                Prefs.PROVIDER_TYPESAFE -> Prefs.DEFAULT_JUDGE_BASE_TYPESAFE
                Prefs.PROVIDER_VERCEL -> Prefs.DEFAULT_JUDGE_BASE_VERCEL
                Prefs.PROVIDER_ZEN -> Prefs.DEFAULT_JUDGE_BASE_ZEN
                else -> Prefs.DEFAULT_JUDGE_BASE_OPENROUTER
            },
        )
        store.put(
            LIVE_JUDGE_MODEL,
            judgeModel ?: when (provider) {
                Prefs.PROVIDER_BOCHA -> Prefs.DEFAULT_JUDGE_MODEL_BOCHA
                Prefs.PROVIDER_TYPESAFE -> Prefs.DEFAULT_JUDGE_MODEL_TYPESAFE
                Prefs.PROVIDER_VERCEL -> Prefs.DEFAULT_JUDGE_MODEL_VERCEL
                Prefs.PROVIDER_ZEN -> Prefs.DEFAULT_JUDGE_MODEL_ZEN
                else -> Prefs.DEFAULT_JUDGE_MODEL_OPENROUTER
            },
        )
        store.put(LIVE_REPLY_BASE, replyBase)
        store.put(LIVE_REPLY_KEY, replyKey)
        store.put(LIVE_REPLY_MODEL, replyModel)
    }

    @Test fun migrateBochaGoesToCnAndCopiesLive() {
        val store = MapKvStore()
        seedLive(
            store,
            Prefs.PROVIDER_BOCHA,
            judgeKey = "bocha-key",
            replyBase = Prefs.DEEPSEEK_BASE,
            replyKey = "ds-key",
            replyModel = Prefs.DEEPSEEK_MODEL,
        )
        RegionProfiles.migrate(store)

        assertEquals(REGION_CN, store.get(K_REGION))
        assertTrue(store.getBool(K_MIGRATED_V18_REGION, false))
        val cn = RegionProfiles.loadSnapshot(store, REGION_CN)!!
        assertEquals(Prefs.PROVIDER_BOCHA, cn.judgeProvider)
        assertEquals("bocha-key", cn.judgeKey)
        assertEquals(Prefs.DEFAULT_JUDGE_BASE_BOCHA, cn.judgeBase)
        assertEquals("ds-key", cn.replyKey)
        assertEquals(Prefs.DEEPSEEK_BASE, cn.replyBase)
        assertNull(RegionProfiles.loadSnapshot(store, REGION_INTL))
        // live keys untouched
        assertEquals(Prefs.PROVIDER_BOCHA, store.get(LIVE_JUDGE_PROVIDER))
        assertEquals("bocha-key", store.get(LIVE_JUDGE_KEY))
    }

    @Test fun migrateOpenRouterGoesToIntl() {
        val store = MapKvStore()
        seedLive(store, Prefs.PROVIDER_OPENROUTER, judgeKey = "or-key")
        RegionProfiles.migrate(store)

        assertEquals(REGION_INTL, store.get(K_REGION))
        val intl = RegionProfiles.loadSnapshot(store, REGION_INTL)!!
        assertEquals(Prefs.PROVIDER_OPENROUTER, intl.judgeProvider)
        assertEquals("or-key", intl.judgeKey)
        assertNull(RegionProfiles.loadSnapshot(store, REGION_CN))
        assertEquals("or-key", store.get(LIVE_JUDGE_KEY))
    }

    @Test fun migrateTypesafeGoesToIntl() {
        val store = MapKvStore()
        seedLive(store, Prefs.PROVIDER_TYPESAFE, judgeKey = "ts-key")
        RegionProfiles.migrate(store)

        assertEquals(REGION_INTL, store.get(K_REGION))
        val intl = RegionProfiles.loadSnapshot(store, REGION_INTL)!!
        assertEquals(Prefs.PROVIDER_TYPESAFE, intl.judgeProvider)
        assertEquals("ts-key", intl.judgeKey)
        assertNull(RegionProfiles.loadSnapshot(store, REGION_CN))
    }

    @Test fun migrateRunsOnlyOnce() {
        val store = MapKvStore()
        seedLive(store, Prefs.PROVIDER_BOCHA, judgeKey = "first")
        RegionProfiles.migrate(store)
        assertEquals(REGION_CN, store.get(K_REGION))

        store.put(LIVE_JUDGE_PROVIDER, Prefs.PROVIDER_TYPESAFE)
        store.put(LIVE_JUDGE_KEY, "second")
        RegionProfiles.migrate(store)

        assertEquals(REGION_CN, store.get(K_REGION))
        assertEquals("first", RegionProfiles.loadSnapshot(store, REGION_CN)!!.judgeKey)
        assertNull(RegionProfiles.loadSnapshot(store, REGION_INTL))
        assertEquals("second", store.get(LIVE_JUDGE_KEY))
    }

    @Test fun freshInstallLandsOnIntlWithEmptySnapshots() {
        val store = MapKvStore()
        val p = RegionProfiles(store)

        assertEquals(REGION_INTL, p.activeRegion)
        assertTrue(store.getBool(K_MIGRATED_V18_REGION, false))
        assertEquals(REGION_INTL, store.get(K_REGION))
        assertNull(store.get(LIVE_JUDGE_PROVIDER))
        assertFalse(store.contains(RegionProfiles.snapshotKey(REGION_INTL, "judge_provider")))
        assertFalse(store.contains(RegionProfiles.snapshotKey(REGION_CN, "judge_provider")))
        assertEquals(Prefs.PROVIDER_TYPESAFE, p.intl.judgeProvider)
        assertEquals(Prefs.PROVIDER_BOCHA, p.cn.judgeProvider)
    }

    @Test fun emptyIntlDefaultsAreTypesafePlusOpenRouterReply() {
        val d = RegionProfiles.defaults(REGION_INTL)
        assertEquals(Prefs.PROVIDER_TYPESAFE, d.judgeProvider)
        assertEquals(Prefs.DEFAULT_JUDGE_BASE_TYPESAFE, d.judgeBase)
        assertEquals(Prefs.DEFAULT_JUDGE_MODEL_TYPESAFE, d.judgeModel)
        assertEquals(Prefs.DEFAULT_REPLY_BASE, d.replyBase)
        assertEquals(Prefs.DEFAULT_REPLY_MODEL, d.replyModel)
        assertEquals("", d.judgeKey)
        assertEquals("", d.replyKey)
    }

    @Test fun emptyCnDefaultsAreBochaPlusDeepSeek() {
        val d = RegionProfiles.defaults(REGION_CN)
        assertEquals(Prefs.PROVIDER_BOCHA, d.judgeProvider)
        assertEquals(Prefs.DEFAULT_JUDGE_BASE_BOCHA, d.judgeBase)
        assertEquals(Prefs.DEFAULT_JUDGE_MODEL_BOCHA, d.judgeModel)
        assertEquals(Prefs.DEEPSEEK_BASE, d.replyBase)
        assertEquals(Prefs.DEEPSEEK_MODEL, d.replyModel)
        assertEquals("", d.judgeKey)
        assertEquals("", d.replyKey)
    }

    @Test fun tabSwitchKeepsKeysSeparateAndPersistWritesActive() {
        val store = MapKvStore()
        seedLive(store, Prefs.PROVIDER_TYPESAFE, judgeKey = "orig")
        val p = RegionProfiles(store)
        assertEquals(REGION_INTL, p.activeRegion)

        val cnShown = p.switchTo(REGION_CN, p.intl.copy(judgeKey = "key-A"))
        assertEquals(Prefs.PROVIDER_BOCHA, cnShown.judgeProvider)
        assertEquals("", cnShown.judgeKey)

        val back = p.switchTo(REGION_INTL, cnShown.copy(judgeKey = "key-B"))
        assertEquals("key-A", back.judgeKey)
        assertEquals(Prefs.PROVIDER_TYPESAFE, back.judgeProvider)

        p.persist()
        assertEquals("key-A", store.get(LIVE_JUDGE_KEY))
        assertEquals(Prefs.PROVIDER_TYPESAFE, store.get(LIVE_JUDGE_PROVIDER))
        assertEquals(REGION_INTL, store.get(K_REGION))
        assertEquals("key-A", store.get(RegionProfiles.snapshotKey(REGION_INTL, "judge_key")))
        assertEquals("key-B", store.get(RegionProfiles.snapshotKey(REGION_CN, "judge_key")))

        val p2 = RegionProfiles(store)
        val cn2 = p2.switchTo(REGION_CN, p2.intl)
        assertEquals("key-B", cn2.judgeKey)
        p2.persist()
        assertEquals("key-B", store.get(LIVE_JUDGE_KEY))
        assertEquals(Prefs.PROVIDER_BOCHA, store.get(LIVE_JUDGE_PROVIDER))
        assertEquals(REGION_CN, store.get(K_REGION))
        assertEquals("key-A", store.get(RegionProfiles.snapshotKey(REGION_INTL, "judge_key")))
    }

    @Test fun replyKeyFallbackStaysInsideTheSameSnapshot() {
        val intl = RegionProfiles.defaults(REGION_INTL).copy(judgeKey = "A", replyKey = "")
        val cn = RegionProfiles.defaults(REGION_CN).copy(judgeKey = "B", replyKey = "")
        assertEquals("A", intl.effectiveReplyKey())
        assertEquals("B", cn.effectiveReplyKey())

        val cnOwn = cn.copy(replyKey = "C")
        assertEquals("C", cnOwn.effectiveReplyKey())
        assertEquals("A", intl.effectiveReplyKey())
    }
}
