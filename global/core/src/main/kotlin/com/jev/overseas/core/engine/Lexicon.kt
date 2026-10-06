package com.jev.overseas.core.engine

/**
 * Common chat and workplace abbreviations with their expansions.
 *
 * The expansions found in a conversation are given to both models as a
 * glossary, so neither has to rely on what it happens to know.
 */
object Lexicon {

    val EXPANSIONS: Map<String, String> = linkedMapOf(
        // chat
        "thx" to "thanks", "ty" to "thank you", "tysm" to "thank you so much", "pls" to "please", "plz" to "please",
        "lol" to "laughing out loud (often just softens the tone)", "lmao" to "laughing a lot", "rofl" to "laughing a lot",
        "omg" to "oh my god", "wtf" to "what the f***", "idk" to "I don't know", "idc" to "I don't care",
        "imo" to "in my opinion", "imho" to "in my humble opinion", "tbh" to "to be honest", "ngl" to "not gonna lie",
        "btw" to "by the way", "fyi" to "for your information", "brb" to "be right back",
        "gtg" to "got to go", "g2g" to "got to go", "ttyl" to "talk to you later", "ttys" to "talk to you soon",
        "lmk" to "let me know", "hmu" to "hit me up (contact me)", "np" to "no problem", "ofc" to "of course",
        "rn" to "right now", "nvm" to "never mind", "jk" to "just kidding", "smh" to "shaking my head",
        "ikr" to "I know, right", "iirc" to "if I remember correctly", "afaik" to "as far as I know",
        "wyd" to "what are you doing", "wbu" to "what about you", "hbu" to "how about you",
        "k" to "OK", "kk" to "OK", "ily" to "I love you", "ilysm" to "I love you so much",
        "gn" to "good night", "gm" to "good morning", "ur" to "your / you're", "bc" to "because", "cuz" to "because",
        "tho" to "though", "b4" to "before", "tmrw" to "tomorrow", "2moro" to "tomorrow", "irl" to "in real life",
        "dm" to "direct message", "tmi" to "too much information", "fomo" to "fear of missing out",
        "atm" to "at the moment", "bf" to "boyfriend", "gf" to "girlfriend", "bff" to "best friend",
        "omw" to "on my way", "ffs" to "for f***'s sake", "tbf" to "to be fair", "dw" to "don't worry",
        "wdym" to "what do you mean", "istg" to "I swear to god", "nbd" to "no big deal",
        "yw" to "you're welcome", "xoxo" to "hugs and kisses",
        // work
        "asap" to "as soon as possible", "eod" to "end of day", "cob" to "close of business", "eow" to "end of week",
        "ooo" to "out of office", "wfh" to "working from home", "tbc" to "to be confirmed", "tbd" to "to be decided",
        "lgtm" to "looks good to me", "eta" to "estimated time of arrival", "w/c" to "week commencing",
        "fwiw" to "for what it's worth", "pto" to "paid time off",
        "kpi" to "key performance indicator", "wip" to "work in progress", "cc" to "copied in",
        "pcm" to "per calendar month", "tba" to "to be announced", "rsvp" to "please reply", "aka" to "also known as",
    )

    private val TOKEN = Regex("[A-Za-z0-9/!']+")

    /** Expansions for the abbreviations that occur as whole words in [texts]. */
    fun glossaryFor(texts: List<String>): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (text in texts) {
            for (match in TOKEN.findAll(text)) {
                val token = match.value.lowercase().trimEnd('!')
                // A capitalised "K" or "OK" style reply still counts; a single letter inside a sentence does not.
                if (token.length == 1 && text.trim().length > 2) continue
                EXPANSIONS[token]?.let { out[token] = it }
            }
        }
        return out
    }
}
