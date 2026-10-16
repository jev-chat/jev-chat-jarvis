package com.jev.probe.core

/**
 * Two complete API profiles (overseas / mainland) plus the live "currently
 * active" keys. Pure Kotlin: the settings screen and [Prefs] both talk to a
 * [KvStore], so the switch / migrate / default logic is unit-testable without
 * Android.
 *
 * Runtime clients keep reading the live keys (`judge_provider`, `judge_key`,
 * …). This class never changes those keys' *meaning* — it only copies a
 * snapshot onto them when the user saves.
 */
data class RouteSnapshot(
    val judgeProvider: String,
    val judgeBase: String,
    val judgeKey: String,
    val judgeModel: String,
    val replyBase: String,
    val replyKey: String,
    val replyModel: String,
) {
    /** Reply key falls back to the judge key *inside this snapshot only*. */
    fun effectiveReplyKey(): String = replyKey.ifBlank { judgeKey }
}

interface KvStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun getBool(key: String, default: Boolean = false): Boolean
    fun putBool(key: String, value: Boolean)
    fun contains(key: String): Boolean
}

/** In-memory store for tests. Booleans are kept as `"true"` / `"false"`. */
class MapKvStore(val data: MutableMap<String, String> = mutableMapOf()) : KvStore {
    override fun get(key: String): String? = data[key]
    override fun put(key: String, value: String) { data[key] = value }
    override fun getBool(key: String, default: Boolean): Boolean =
        data[key]?.let { it == "true" } ?: default
    override fun putBool(key: String, value: Boolean) { data[key] = if (value) "true" else "false" }
    override fun contains(key: String): Boolean = data.containsKey(key)
}

class RegionProfiles(private val store: KvStore) {

    var activeRegion: String
        private set
    var intl: RouteSnapshot
        private set
    var cn: RouteSnapshot
        private set

    init {
        migrate(store)
        val stored = store.get(K_REGION)
        activeRegion = if (stored == REGION_CN) REGION_CN else REGION_INTL
        intl = loadSnapshot(store, REGION_INTL) ?: defaults(REGION_INTL)
        cn = loadSnapshot(store, REGION_CN) ?: defaults(REGION_CN)
    }

    fun current(): RouteSnapshot = if (activeRegion == REGION_CN) cn else intl

    fun stash(form: RouteSnapshot) {
        if (activeRegion == REGION_CN) cn = form else intl = form
    }

    /**
     * Park [currentForm] on the tab being left, then return the snapshot the
     * new tab should show (stored snapshot, or that tab's defaults).
     */
    fun switchTo(newRegion: String, currentForm: RouteSnapshot): RouteSnapshot {
        stash(currentForm)
        activeRegion = if (newRegion == REGION_CN) REGION_CN else REGION_INTL
        return current()
    }

    /** Write both snapshots, copy the active one onto the live keys, record region. */
    fun persist() {
        writeSnapshot(store, REGION_INTL, intl)
        writeSnapshot(store, REGION_CN, cn)
        writeLive(store, current())
        store.put(K_REGION, activeRegion)
    }

    companion object {
        const val REGION_INTL = "intl"
        const val REGION_CN = "cn"

        const val K_REGION = "region"
        const val K_MIGRATED_V18_REGION = "migrated_v18_region"

        const val LIVE_JUDGE_PROVIDER = "judge_provider"
        const val LIVE_JUDGE_BASE = "judge_base_url"
        const val LIVE_JUDGE_KEY = "judge_key"
        const val LIVE_JUDGE_MODEL = "judge_model"
        const val LIVE_REPLY_BASE = "reply_base_url"
        const val LIVE_REPLY_KEY = "reply_key"
        const val LIVE_REPLY_MODEL = "reply_model"

        fun snapshotKey(region: String, field: String): String = "${region}_$field"

        fun defaults(region: String): RouteSnapshot = if (region == REGION_CN) {
            RouteSnapshot(
                judgeProvider = Prefs.PROVIDER_BOCHA,
                judgeBase = Prefs.DEFAULT_JUDGE_BASE_BOCHA,
                judgeKey = "",
                judgeModel = Prefs.DEFAULT_JUDGE_MODEL_BOCHA,
                replyBase = Prefs.DEEPSEEK_BASE,
                replyKey = "",
                replyModel = Prefs.DEEPSEEK_MODEL,
            )
        } else {
            RouteSnapshot(
                judgeProvider = Prefs.PROVIDER_TYPESAFE,
                judgeBase = Prefs.DEFAULT_JUDGE_BASE_TYPESAFE,
                judgeKey = "",
                judgeModel = Prefs.DEFAULT_JUDGE_MODEL_TYPESAFE,
                replyBase = Prefs.DEFAULT_REPLY_BASE,
                replyKey = "",
                replyModel = Prefs.DEFAULT_REPLY_MODEL,
            )
        }

        /**
         * First launch of the region-tab build. Copies the live keys into
         * whichever tab they belong to; the other tab stays empty (defaults
         * show in the UI). Fresh installs (nothing ever saved) land on
         * overseas with empty snapshots, so the settings page can preselect
         * TypeSafe without rewriting the live getter defaults.
         */
        fun migrate(store: KvStore) {
            if (store.getBool(K_MIGRATED_V18_REGION, false)) return
            store.putBool(K_MIGRATED_V18_REGION, true)

            val provider = store.get(LIVE_JUDGE_PROVIDER)
            val hasConfig = provider != null ||
                !store.get(LIVE_JUDGE_KEY).isNullOrBlank() ||
                !store.get(LIVE_REPLY_KEY).isNullOrBlank() ||
                store.contains(LIVE_JUDGE_BASE) ||
                store.contains(LIVE_REPLY_BASE)

            if (!hasConfig) {
                store.put(K_REGION, REGION_INTL)
                return
            }

            val live = readLive(store)
            if (provider == Prefs.PROVIDER_BOCHA) {
                store.put(K_REGION, REGION_CN)
                writeSnapshot(store, REGION_CN, live)
            } else {
                store.put(K_REGION, REGION_INTL)
                writeSnapshot(store, REGION_INTL, live)
            }
        }

        fun loadSnapshot(store: KvStore, region: String): RouteSnapshot? {
            if (!store.contains(snapshotKey(region, "judge_provider"))) return null
            val d = defaults(region)
            return RouteSnapshot(
                judgeProvider = store.get(snapshotKey(region, "judge_provider")) ?: d.judgeProvider,
                judgeBase = store.get(snapshotKey(region, "judge_base")) ?: d.judgeBase,
                judgeKey = store.get(snapshotKey(region, "judge_key")).orEmpty(),
                judgeModel = store.get(snapshotKey(region, "judge_model")) ?: d.judgeModel,
                replyBase = store.get(snapshotKey(region, "reply_base")) ?: d.replyBase,
                replyKey = store.get(snapshotKey(region, "reply_key")).orEmpty(),
                replyModel = store.get(snapshotKey(region, "reply_model")) ?: d.replyModel,
            )
        }

        fun writeSnapshot(store: KvStore, region: String, snap: RouteSnapshot) {
            store.put(snapshotKey(region, "judge_provider"), snap.judgeProvider)
            store.put(snapshotKey(region, "judge_base"), snap.judgeBase)
            store.put(snapshotKey(region, "judge_key"), snap.judgeKey)
            store.put(snapshotKey(region, "judge_model"), snap.judgeModel)
            store.put(snapshotKey(region, "reply_base"), snap.replyBase)
            store.put(snapshotKey(region, "reply_key"), snap.replyKey)
            store.put(snapshotKey(region, "reply_model"), snap.replyModel)
        }

        fun readLive(store: KvStore): RouteSnapshot = RouteSnapshot(
            judgeProvider = store.get(LIVE_JUDGE_PROVIDER) ?: Prefs.PROVIDER_OPENROUTER,
            judgeBase = store.get(LIVE_JUDGE_BASE) ?: Prefs.DEFAULT_JUDGE_BASE_OPENROUTER,
            judgeKey = store.get(LIVE_JUDGE_KEY).orEmpty(),
            judgeModel = store.get(LIVE_JUDGE_MODEL) ?: Prefs.DEFAULT_JUDGE_MODEL_OPENROUTER,
            replyBase = store.get(LIVE_REPLY_BASE) ?: Prefs.DEFAULT_REPLY_BASE,
            replyKey = store.get(LIVE_REPLY_KEY).orEmpty(),
            replyModel = store.get(LIVE_REPLY_MODEL) ?: Prefs.DEFAULT_REPLY_MODEL,
        )

        fun writeLive(store: KvStore, snap: RouteSnapshot) {
            store.put(LIVE_JUDGE_PROVIDER, snap.judgeProvider)
            store.put(LIVE_JUDGE_BASE, snap.judgeBase)
            store.put(LIVE_JUDGE_KEY, snap.judgeKey)
            store.put(LIVE_JUDGE_MODEL, snap.judgeModel)
            store.put(LIVE_REPLY_BASE, snap.replyBase)
            store.put(LIVE_REPLY_KEY, snap.replyKey)
            store.put(LIVE_REPLY_MODEL, snap.replyModel)
        }
    }
}
