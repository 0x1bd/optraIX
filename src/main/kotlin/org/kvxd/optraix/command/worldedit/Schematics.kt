package org.kvxd.optraix.command.worldedit

import org.kvxd.optraix.Log
import org.kvxd.optraix.command.CommandSource
import org.kvxd.optraix.worldedit.schematic.Schematic
import org.kvxd.optraix.worldedit.schematic.SchematicException
import java.io.File

object Schematics {

    fun load(source: CommandSource, rawName: String) {
        val directory = source.server.config.schematicsDirectory
        val name = if (
            rawName.endsWith(".schem", ignoreCase = true) ||
            rawName.endsWith(".schematic", ignoreCase = true)
        ) rawName
        else "$rawName.schem"

        try {
            val clipboard = Schematic.load(File(directory, name))
            source.player.clipboard = clipboard
            source.success(
                "loaded $name (${clipboard.sizeX}x${clipboard.sizeY}x${clipboard.sizeZ}), use //paste"
            )
        } catch (cause: SchematicException) {
            source.error(cause.message ?: "failed to load schematic")
        } catch (cause: Throwable) {
            Log.error("schematic", "loading $name", cause)
            source.error("failed to load $name: ${Log.describe(cause)}")
        }
    }
}