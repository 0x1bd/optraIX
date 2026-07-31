package org.kvxd.optraix.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.FloatArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.LongArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.tree.ArgumentCommandNode
import com.mojang.brigadier.tree.LiteralCommandNode
import org.kvxd.optraix.command.argument.BlockStateArgumentType
import org.kvxd.optraix.command.argument.DirectionArgumentType
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.play.clientbound.ClientboundDeclareCommandsPacket
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.types.CommandNode
import com.mojang.brigadier.tree.CommandNode as BrigadierNode

object CommandTree {

    private const val TypeRoot = 0
    private const val TypeLiteral = 1
    private const val TypeArgument = 2

    fun encode(dispatcher: CommandDispatcher<CommandSource>): ClientboundDeclareCommandsPacket {
        val ordered = ArrayList<BrigadierNode<CommandSource>>()
        val indices = HashMap<BrigadierNode<CommandSource>, Int>()

        fun collect(node: BrigadierNode<CommandSource>) {
            if (indices.containsKey(node)) return
            indices[node] = ordered.size
            ordered.add(node)
            for (child in node.children) collect(child)
        }
        collect(dispatcher.root)

        val nodes = ordered.map { node ->
            CommandNode(
                flags = flagsFor(node),
                children = node.children.mapNotNull { indices[it] },
                redirectNode = null,
                extraNodeData = extraDataFor(node),
            )
        }
        return ClientboundDeclareCommandsPacket(nodes, 0)
    }

    private fun flagsFor(node: BrigadierNode<CommandSource>): CommandNode.Flags {
        val type = when (node) {
            is LiteralCommandNode -> TypeLiteral
            is ArgumentCommandNode<*, *> -> TypeArgument
            else -> TypeRoot
        }
        return CommandNode.Flags(
            unused = 0,
            has_custom_suggestions = 0,
            has_redirect_node = 0,
            has_command = if (node.command != null) 1 else 0,
            command_node_type = type,
        )
    }

    private fun extraDataFor(node: BrigadierNode<CommandSource>): CommandNode.ExtraNodeData = when (node) {
        is LiteralCommandNode -> CommandNode.ExtraNodeData(
            field_1 = CommandNode.ExtraNodeData1(node.literal),
            field_2 = null,
        )
        is ArgumentCommandNode<*, *> -> CommandNode.ExtraNodeData(
            field_1 = null,
            field_2 = CommandNode.ExtraNodeData2(
                name = node.name,
                parser = parserFor(node.type),
                properties = propertiesFor(node.type),
                suggestionType = null,
            ),
        )
        else -> CommandNode.ExtraNodeData(null, null)
    }

    private fun parserFor(type: ArgumentType<*>): CommandNode.ExtraNodeData2.Parser = when (type) {
        is BoolArgumentType -> CommandNode.ExtraNodeData2.Parser.BrigadierBool
        is IntegerArgumentType -> CommandNode.ExtraNodeData2.Parser.BrigadierInteger
        is LongArgumentType -> CommandNode.ExtraNodeData2.Parser.BrigadierLong
        is FloatArgumentType -> CommandNode.ExtraNodeData2.Parser.BrigadierFloat
        is DoubleArgumentType -> CommandNode.ExtraNodeData2.Parser.BrigadierDouble
        is StringArgumentType -> CommandNode.ExtraNodeData2.Parser.BrigadierString
        is BlockStateArgumentType -> CommandNode.ExtraNodeData2.Parser.MinecraftBlockState
        is DirectionArgumentType -> CommandNode.ExtraNodeData2.Parser.BrigadierString
        else -> CommandNode.ExtraNodeData2.Parser.BrigadierString
    }

    private fun propertiesFor(type: ArgumentType<*>): CommandNode.ExtraNodeData2.Properties {
        val stringType = when (type) {
            is StringArgumentType -> when (type.type) {
                StringArgumentType.StringType.SINGLE_WORD ->
                    CommandNode.ExtraNodeData2.PropertiesBrigadierString.SINGLEWORD
                StringArgumentType.StringType.QUOTABLE_PHRASE ->
                    CommandNode.ExtraNodeData2.PropertiesBrigadierString.QUOTABLEPHRASE
                else -> CommandNode.ExtraNodeData2.PropertiesBrigadierString.GREEDYPHRASE
            }
            is DirectionArgumentType -> CommandNode.ExtraNodeData2.PropertiesBrigadierString.SINGLEWORD
            else -> null
        }

        return CommandNode.ExtraNodeData2.Properties(
            brigadier_float = if (type is FloatArgumentType) unboundedFloat() else null,
            brigadier_double = if (type is DoubleArgumentType) unboundedDouble() else null,
            brigadier_integer = if (type is IntegerArgumentType) unboundedInteger() else null,
            brigadier_long = if (type is LongArgumentType) unboundedLong() else null,
            brigadier_string = stringType,
            minecraft_entity = null,
            minecraft_score_holder = null,
            minecraft_time = null,
            minecraft_resource_or_tag = null,
            minecraft_resource_or_tag_key = null,
            minecraft_resource = null,
            minecraft_resource_key = null,
        )
    }

    private fun unboundedInteger() = CommandNode.ExtraNodeData2.PropertiesBrigadierInteger(
        flags = CommandNode.ExtraNodeData2.PropertiesBrigadierInteger.Flags(0, 0, 0),
        min = null,
        max = null,
    )

    private fun unboundedLong() = CommandNode.ExtraNodeData2.PropertiesBrigadierLong(
        flags = CommandNode.ExtraNodeData2.PropertiesBrigadierLong.Flags(0, 0, 0),
        min = null,
        max = null,
    )

    private fun unboundedFloat() = CommandNode.ExtraNodeData2.PropertiesBrigadierFloat(
        flags = CommandNode.ExtraNodeData2.PropertiesBrigadierFloat.Flags(0, 0, 0),
        min = null,
        max = null,
    )

    private fun unboundedDouble() = CommandNode.ExtraNodeData2.PropertiesBrigadierDouble(
        flags = CommandNode.ExtraNodeData2.PropertiesBrigadierDouble.Flags(0, 0, 0),
        min = null,
        max = null,
    )
}
