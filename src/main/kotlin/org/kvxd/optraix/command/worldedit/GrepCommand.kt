package org.kvxd.optraix.command.worldedit

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import java.util.UUID
import net.lenni0451.mcstructs.nbt.tags.CompoundTag
import net.lenni0451.mcstructs.nbt.tags.StringTag
import org.kvxd.optraix.command.CommandSource
import org.kvxd.optraix.command.ServerCommand
import org.kvxd.optraix.command.argument
import org.kvxd.optraix.command.literal
import org.kvxd.optraix.command.runs
import org.kvxd.optraix.nbt.compoundOf
import org.kvxd.optraix.net.Text
import org.kvxd.optraix.world.Chunk
import org.kvxd.optraix.world.search.WorldBlockSearch

class GrepCommand : ServerCommand {

    override val name = "/grep"

    override val description = "search every stored block in the world"

    private val sessions = HashMap<UUID, SearchSession>()

    override fun register(dispatcher: CommandDispatcher<CommandSource>) {
        dispatcher.register(
            literal(name)
                .then(
                    literal("page").then(
                        argument("page", IntegerArgumentType.integer(1, MaxPage)).then(
                            argument("query", StringArgumentType.greedyString()).runs { context ->
                                show(
                                    context.source,
                                    StringArgumentType.getString(context, "query"),
                                    IntegerArgumentType.getInteger(context, "page"),
                                    refresh = false,
                                )
                            }
                        )
                    )
                )
                .then(
                    argument("query", StringArgumentType.greedyString()).runs { context ->
                        show(
                            context.source,
                            StringArgumentType.getString(context, "query"),
                            1,
                            refresh = true,
                        )
                    }
                )
        )
    }

    private fun show(source: CommandSource, rawQuery: String, pageNumber: Int, refresh: Boolean) {
        val query = rawQuery.trim()
        if (query.isEmpty()) {
            source.error("provide a block name or state to search for")
            return
        }
        val player = source.player
        val world = source.server.runtimeFor(player)
        val existing = sessions[player.uuid]?.takeIf {
            it.query.equals(query, ignoreCase = true) && it.worldName == world.name
        }
        val session = if (!refresh && existing != null) {
            existing
        } else {
            SearchSession(
                query,
                world.name,
                world.world.snapshotChunks(),
                existing?.matchingStates ?: WorldBlockSearch.matchingStates(query),
            ).also { sessions[player.uuid] = it }
        }

        if (!session.matchingStates.any()) {
            source.error("no block states match '$query'")
            return
        }
        val page = WorldBlockSearch.page(session.chunks, session.matchingStates, pageNumber, PageSize)
        if (page.matches.isEmpty()) {
            source.error("no results on page $pageNumber for '$query'")
            return
        }

        source.heading("grep '$query' page $pageNumber")
        for (match in page.matches) {
            val pos = match.position
            val teleport = "/tp ${pos.x + 0.5} ${pos.y + 1.0} ${pos.z + 0.5}"
            source.reply(
                Text.join(
                    listOf(
                        Text.clickable("[/tp]", Text.Green, teleport, teleport),
                        component("  ${pos.x}, ${pos.y}, ${pos.z}  ", Text.Aqua),
                        component(WorldBlockSearch.describe(match.state), Text.Gray),
                    )
                )
            )
        }
        navigation(source, query, pageNumber, page.hasNext)
    }

    private fun navigation(source: CommandSource, query: String, page: Int, hasNext: Boolean) {
        val parts = ArrayList<CompoundTag>(3)
        if (page > 1) {
            val command = "//grep page ${page - 1} $query"
            parts += Text.clickable("« Previous", Text.Yellow, command, "Show page ${page - 1}")
        }
        if (page > 1 && hasNext) parts += component("   ", Text.Gray)
        if (hasNext) {
            val command = "//grep page ${page + 1} $query"
            parts += Text.clickable("Next »", Text.Yellow, command, "Show page ${page + 1}")
        }
        if (parts.isNotEmpty()) source.reply(Text.join(parts))
    }

    private fun component(text: String, color: String): CompoundTag =
        compoundOf("text" to StringTag(text), "color" to StringTag(color))

    private class SearchSession(
        val query: String,
        val worldName: String,
        val chunks: List<Chunk>,
        val matchingStates: BooleanArray,
    )

    private companion object {
        const val PageSize = 8
        const val MaxPage = 1_000_000
    }
}
