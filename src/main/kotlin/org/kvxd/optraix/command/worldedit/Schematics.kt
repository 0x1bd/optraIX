package org.kvxd.optraix.command.worldedit

import org.kvxd.optraix.Log
import org.kvxd.optraix.command.CommandSource
import org.kvxd.optraix.worldedit.schematic.Schematic
import org.kvxd.optraix.worldedit.schematic.SchematicException

object Schematics {

    fun load(source: CommandSource, rawName: String) {
        val directory = source.server.config.schematicsDirectory
        try {
            val file = SchematicFiles.resolve(directory, rawName)
            val clipboard = Schematic.load(file)
            source.player.clipboard = clipboard
            source.success(
                "loaded ${file.name} (${clipboard.sizeX}x${clipboard.sizeY}x${clipboard.sizeZ}), use //paste"
            )
        } catch (cause: SchematicException) {
            source.error(cause.message ?: "failed to load schematic")
        } catch (cause: Throwable) {
            Log.error("schematic", "loading $rawName", cause)
            source.error("failed to load $rawName: ${Log.describe(cause)}")
        }
    }

    fun export(worldEdit: WorldEdit, source: CommandSource, rawName: String) {
        val region = worldEdit.regionOf(source.player)
        if (region == null) {
            source.error("make a selection first")
            return
        }
        if (region.volume > Int.MAX_VALUE || maxOf(region.sizeX, region.sizeY, region.sizeZ) > 65_535) {
            source.error("selection is too large for a Sponge schematic")
            return
        }
        val file = try {
            SchematicFiles.resolveExport(source.server.config.schematicsDirectory, rawName)
        } catch (cause: SchematicException) {
            source.error(cause.message ?: "invalid schematic name")
            return
        }

        val submission = worldEdit.submitExport(
            source.player,
            region,
            file,
            source::reply,
            completion = { outcome ->
                when (outcome) {
                    is EditOutcome.Completed -> source.reply("captured ${outcome.changed} blocks; writing ${file.name}")
                    is EditOutcome.Cancelled -> source.reply("schematic export cancelled")
                    is EditOutcome.Failed -> source.error("schematic export failed: ${outcome.message}")
                }
            },
            written = { result ->
                result.onSuccess {
                    source.success("exported ${file.name} (${region.sizeX}x${region.sizeY}x${region.sizeZ})")
                }.onFailure { cause ->
                    Log.error("schematic", "exporting ${file.name}", cause)
                    source.error("failed to export ${file.name}: ${Log.describe(cause)}")
                }
            },
        )
        if (!submission.completed) {
            source.reply("schematic export #${submission.jobId} started; use //cancel to stop it")
        }
    }
}
