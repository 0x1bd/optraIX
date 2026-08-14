package org.kvxd.optraix.command.worldedit

import java.io.File
import org.kvxd.optraix.worldedit.schematic.SchematicException

object SchematicFiles {

    fun resolveExport(directory: File, rawName: String): File {
        val file = resolve(directory, rawName)
        if (!file.name.endsWith(".schem", ignoreCase = true)) {
            throw SchematicException("Sponge v3 exports must use the .schem extension")
        }
        return file
    }

    fun resolve(directory: File, rawName: String): File {
        val requested = rawName.trim()
        if (requested.isEmpty()) throw SchematicException("schematic name cannot be empty")
        if (
            '/' in requested || '\\' in requested ||
            requested != File(requested).name || requested == "." || requested == ".."
        ) {
            throw SchematicException("schematic name must not contain a path")
        }
        if ('\u0000' in requested) throw SchematicException("schematic name contains an invalid character")
        val name = if (SchematicSuggestions.isSchematicName(requested)) requested else "$requested.schem"
        return File(directory, name)
    }
}
