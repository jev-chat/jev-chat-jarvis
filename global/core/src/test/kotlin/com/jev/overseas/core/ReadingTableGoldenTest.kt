package com.jev.overseas.core

import com.jev.overseas.core.scene.SceneCatalog
import com.jev.overseas.core.scene.Selection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReadingTableGoldenTest {

    private fun selections(): List<Selection> = SceneCatalog.specs.values.flatMap { spec ->
        (spec.relationships + listOf(null)).flatMap { rel -> listOf(false, true).map { Selection(spec.scene, rel, it) } }
    }

    private fun name(sel: Selection) = "${sel.scene.id}/${sel.relationship?.id ?: "none"}/${if (sel.conflict) "conflict" else "calm"}"


    /**
     * Pins how every behaviour is read for every relationship: position and whether
     * the user must choose first. A change here should be a decision, not an accident.
     * Regenerate with JEV_UPDATE_GOLDEN=1 after an intended change.
     *
     * This is only a snapshot of the code. It cannot show that the code matches what
     * the user approved; ApprovedMatrixTest does that from a hand-written table.
     */
    @Test fun readingTableMatchesGolden() {
        val table = buildString {
            for (sel in selections()) {
                val spec = SceneCatalog.spec(sel.scene)
                for (b in spec.behaviors(sel)) {
                    val reading = spec.reading(b.id, sel, setOf(b.id))
                    val choose = spec.mustChoose(b.id, sel, reading)
                    append("${name(sel)} ${b.id} ${b.role.name.lowercase()} ")
                    append(reading?.position?.name?.lowercase() ?: "-")
                    append(if (choose) " choose" else " direct")
                    append('\n')
                }
            }
        }
        val file = File("src/test/resources/golden/reading-table.txt")
        if (System.getenv("JEV_UPDATE_GOLDEN") == "1") {
            file.parentFile.mkdirs()
            file.writeText(table)
        }
        assertTrue("Missing $file (run once with JEV_UPDATE_GOLDEN=1)", file.exists())
        assertEquals(file.readText(), table)
    }
}
