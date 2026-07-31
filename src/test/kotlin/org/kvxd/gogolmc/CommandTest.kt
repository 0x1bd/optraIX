package org.kvxd.gogolmc

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import org.kvxd.gogolmc.block.Blocks
import org.kvxd.gogolmc.command.CommandSource
import org.kvxd.gogolmc.command.CommandTree
import org.kvxd.gogolmc.command.argument
import org.kvxd.gogolmc.command.argument.BlockStateArgumentType
import org.kvxd.gogolmc.command.literal
import org.kvxd.gogolmc.command.runs
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.types.CommandNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CommandTest {

    private fun dispatcher(): CommandDispatcher<CommandSource> {
        val dispatcher = CommandDispatcher<CommandSource>()
        dispatcher.register(literal("tps").runs { }.then(literal("info").runs { }))
        dispatcher.register(
            literal("speed").then(argument("multiplier", IntegerArgumentType.integer(1, 10)).runs { })
        )
        dispatcher.register(
            literal("/set").then(argument("block", BlockStateArgumentType.blockState()).runs { })
        )
        dispatcher.register(
            literal("/load").then(argument("name", StringArgumentType.greedyString()).runs { })
        )
        return dispatcher
    }

    @Test
    fun worldEditLiteralsKeepTheirLeadingSlash() {
        val parsed = dispatcher().parse("/set stone", null as CommandSource?)
        assertTrue(parsed.exceptions.isEmpty(), "//set must parse: ${parsed.exceptions}")
        assertEquals(0, parsed.reader.remainingLength, "the whole line should be consumed")
    }

    @Test
    fun blockArgumentParsesStatesWithProperties() {
        val type = BlockStateArgumentType.blockState()
        val reader = com.mojang.brigadier.StringReader("repeater[delay=3,facing=east]")
        val state = type.parse(reader)
        assertEquals(
            Blocks.parse("minecraft:repeater[delay=3,facing=east]"),
            state,
        )
    }

    @Test
    fun blockArgumentSuggestsBlockNames() {
        val type = BlockStateArgumentType.blockState()
        val builder = com.mojang.brigadier.suggestion.SuggestionsBuilder("sandst", 0)
        val suggestions = type.listSuggestions(
            dispatcher().parse("", null as CommandSource?).context.build(""),
            builder,
        ).join()
        assertTrue(
            suggestions.list.any { it.text == "sandstone" },
            "expected sandstone in ${suggestions.list.map { it.text }}",
        )
    }

    @Test
    fun subcommandsCompleteFromPartialInput() {
        val dispatcher = dispatcher()
        val parsed = dispatcher.parse("tps in", null as CommandSource?)
        val suggestions = dispatcher.getCompletionSuggestions(parsed).join()
        assertEquals(listOf("info"), suggestions.list.map { it.text })
    }

    @Test
    fun rootCompletionListsEveryCommand() {
        val dispatcher = dispatcher()
        val suggestions = dispatcher
            .getCompletionSuggestions(dispatcher.parse("", null as CommandSource?))
            .join()
        val names = suggestions.list.map { it.text }.toSet()
        assertTrue(names.containsAll(setOf("tps", "speed", "/set", "/load")), "got $names")
    }

    @Test
    fun treeEncodesRootLiteralsAndArguments() {
        val packet = CommandTree.encode(dispatcher())
        assertEquals(0, packet.rootIndex)

        val root = packet.nodes[0]
        assertEquals(0, root.flags.command_node_type, "node 0 must be the root")
        assertTrue(root.children.isNotEmpty())

        val literals = packet.nodes.filter { it.flags.command_node_type == 1 }
        val names = literals.mapNotNull { it.extraNodeData.field_1?.name }.toSet()
        assertTrue(names.containsAll(setOf("tps", "info", "speed", "/set", "/load")), "got $names")

        val arguments = packet.nodes.filter { it.flags.command_node_type == 2 }
        assertEquals(3, arguments.size)

        val block = arguments.single { it.extraNodeData.field_2?.name == "block" }
        assertEquals(
            CommandNode.ExtraNodeData2.Parser.MinecraftBlockState,
            block.extraNodeData.field_2?.parser,
        )

        val name = arguments.single { it.extraNodeData.field_2?.name == "name" }
        assertEquals(
            CommandNode.ExtraNodeData2.PropertiesBrigadierString.GREEDYPHRASE,
            name.extraNodeData.field_2?.properties?.brigadier_string,
        )
    }

    @Test
    fun everyNodeChildIndexIsInRange() {
        val packet = CommandTree.encode(dispatcher())
        for (node in packet.nodes) {
            for (child in node.children) {
                assertTrue(child in packet.nodes.indices, "child index $child out of range")
            }
        }
    }

    @Test
    fun executableNodesAreFlagged() {
        val packet = CommandTree.encode(dispatcher())
        val info = packet.nodes.single { it.extraNodeData.field_1?.name == "info" }
        assertEquals(1, info.flags.has_command)

        val set = packet.nodes.single { it.extraNodeData.field_1?.name == "/set" }
        assertEquals(0, set.flags.has_command, "//set needs its block argument")
    }
}
