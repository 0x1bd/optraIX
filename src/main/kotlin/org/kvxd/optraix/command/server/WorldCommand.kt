package org.kvxd.optraix.command.server

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import org.kvxd.optraix.command.CommandSource
import org.kvxd.optraix.command.ServerCommand
import org.kvxd.optraix.command.argument
import org.kvxd.optraix.command.literal
import org.kvxd.optraix.command.runs
import org.kvxd.optraix.command.suggestMatching
import org.kvxd.optraix.world.management.DefaultWorldName

class WorldCommand : ServerCommand {

    override val name = "world"

    override val aliases = listOf("w")

    override val description = "create, delete, reset, join, and list worlds"

    override fun register(dispatcher: CommandDispatcher<CommandSource>) {
        for (root in listOf(name) + aliases) {
            dispatcher.register(
                literal(root)
                    .runs { list(it.source) }
                    .then(
                        literal("create").then(
                            argument("name", StringArgumentType.word())
                                .runs { create(it.source, StringArgumentType.getString(it, "name")) }
                        )
                    )
                    .then(
                        literal("delete").then(
                            argument("name", StringArgumentType.word())
                                .suggests { context, builder ->
                                    builder.suggestMatching(context.source.server.worlds.names())
                                }
                                .runs {
                                    val world = StringArgumentType.getString(it, "name")
                                    it.source.error("run /$root delete $world confirm to delete this world")
                                }
                                .then(
                                    literal("confirm").runs {
                                        delete(it.source, StringArgumentType.getString(it, "name"))
                                    }
                                )
                        )
                    )
                    .then(
                        literal("reset").then(
                            argument("name", StringArgumentType.word())
                                .suggests { context, builder ->
                                    builder.suggestMatching(context.source.server.worlds.names())
                                }
                                .runs {
                                    val world = StringArgumentType.getString(it, "name")
                                    it.source.error("run /$root reset $world confirm to erase all data in this world")
                                }
                                .then(
                                    literal("confirm").runs {
                                        reset(it.source, StringArgumentType.getString(it, "name"))
                                    }
                                )
                        )
                    )
                    .then(
                        literal("join").then(
                            argument("name", StringArgumentType.word())
                                .suggests { context, builder ->
                                    builder.suggestMatching(context.source.server.worlds.names())
                                }
                                .runs { join(it.source, StringArgumentType.getString(it, "name")) }
                        )
                    )
                    .then(literal("list").runs { list(it.source) })
            )
        }
    }

    private fun create(source: CommandSource, name: String) {
        val server = source.server
        if (!server.worlds.isValidName(name)) {
            source.error("invalid world name; use 1-32 letters, numbers, '.', '_' or '-'")
            return
        }
        if (server.worlds.find(name) != null) {
            source.error("world '$name' already exists")
            return
        }
        val created = server.createWorld(name)
        if (created == null) {
            source.error("could not create world '$name'")
            return
        }
        source.success("created world '${created.name}'")
    }

    private fun delete(source: CommandSource, name: String) {
        val server = source.server
        val runtime = server.worlds.find(name)
        if (runtime == null) {
            source.error("world '$name' does not exist")
            return
        }
        if (runtime.name.equals(DefaultWorldName, ignoreCase = true)) {
            source.error("the default '$DefaultWorldName' world cannot be deleted")
            return
        }
        val occupants = server.players.filter { server.runtimeFor(it) === runtime }
        if (occupants.isNotEmpty()) {
            source.error("world '${runtime.name}' has ${occupants.size} player(s); move them before deleting it")
            return
        }
        if (!server.deleteWorld(runtime.name)) {
            source.error("could not delete world '${runtime.name}'")
            return
        }
        source.success("deleted world '${runtime.name}'")
    }

    private fun reset(source: CommandSource, name: String) {
        val server = source.server
        val runtime = server.worlds.find(name)
        if (runtime == null) {
            source.error("world '$name' does not exist")
            return
        }
        val reset = server.resetWorld(runtime.name)
        if (reset == null) {
            source.error("could not reset world '${runtime.name}'")
            return
        }
        source.success("reset world '${reset.name}'")
    }

    private fun join(source: CommandSource, name: String) {
        val server = source.server
        val runtime = server.worlds.find(name)
        if (runtime == null) {
            source.error("world '$name' does not exist")
            return
        }
        if (server.runtimeFor(source.player) === runtime) {
            source.reply("you are already in '${runtime.name}'")
            return
        }
        if (!server.joinWorld(source.player, runtime.name)) {
            source.error("could not join world '${runtime.name}'")
            return
        }
        source.success("joined world '${runtime.name}'")
    }

    private fun list(source: CommandSource) {
        val server = source.server
        val current = server.runtimeFor(source.player)
        source.heading("worlds")
        for (runtime in server.worlds.all().sortedBy { it.name.lowercase() }) {
            val marker = if (runtime === current) "*" else "-"
            val players = server.players.count { server.runtimeFor(it) === runtime }
            source.reply("  $marker ${runtime.name}  (${runtime.world.loadedChunks} chunks, $players players)")
        }
    }
}
