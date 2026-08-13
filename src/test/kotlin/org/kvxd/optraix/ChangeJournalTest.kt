package org.kvxd.optraix

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.kvxd.optraix.world.BlockEntity
import org.kvxd.optraix.worldedit.history.ChangeJournal
import org.kvxd.optraix.worldedit.history.FileUndoEntry

class ChangeJournalTest {
    @Test
    fun largeJournalSpillsAndRetainsBlockEntities() {
        val directory = File("build/tmp/change-journal-test").apply { mkdirs() }
        val journal = ChangeJournal(directory, ChangeJournal.SpillThreshold + 1)
        val entity = BlockEntity.Comparator(11)
        journal.add(123L, 456, entity)

        val entry = journal.finish()
        assertTrue(entry is FileUndoEntry)
        assertEquals(1, entry.size)
        assertEquals(123L, entry.entryAt(0).position)
        assertEquals(456, entry.entryAt(0).state)
        assertEquals(entity, entry.entryAt(0).entity)

        val file = entry.file
        entry.close()
        assertTrue(!file.exists())
    }
}
