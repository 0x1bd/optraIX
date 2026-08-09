package org.kvxd.optraix.command.worldedit

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import org.kvxd.optraix.command.CommandSource
import org.kvxd.optraix.command.ServerCommand
import org.kvxd.optraix.command.argument
import org.kvxd.optraix.command.argument.BlockStateArgumentType
import org.kvxd.optraix.command.literal
import org.kvxd.optraix.command.runs
import org.kvxd.optraix.world.BlockPos
import org.kvxd.optraix.worldedit.Region

class SetblockCommand(private val worldEdit: WorldEdit) : ServerCommand {

    override val name = "/setblock"

    override val description = "set a single block"

    override fun register(dispatcher: CommandDispatcher<CommandSource>) {
        dispatcher.register(
            literal("/setblock").then(
                argument("block", BlockStateArgumentType.blockState()).runs { context ->
                    set(
                        context.source,
                        context.source.player.blockPos,
                        BlockStateArgumentType.blockState(context, "block"),
                    )
                }
            ).then(
                argument("x", IntegerArgumentType.integer()).then(
                    argument("y", IntegerArgumentType.integer()).then(
                        argument("z", IntegerArgumentType.integer()).then(
                            argument("block", BlockStateArgumentType.blockState()).runs { context ->
                                set(
                                    context.source,
                                    BlockPos(
                                        IntegerArgumentType.getInteger(context, "x"),
                                        IntegerArgumentType.getInteger(context, "y"),
                                        IntegerArgumentType.getInteger(context, "z"),
                                    ),
                                    BlockStateArgumentType.blockState(context, "block"),
                                )
                            }
                        )
                    )
                )
            )
        )
    }

    private fun set(source: CommandSource, pos: BlockPos, state: Int) {
        val changed = worldEdit.apply(source.player, Region(pos, pos)) { state }
        if (changed == 0) source.error("block at ${pos.x} ${pos.y} ${pos.z} is already that")
        else source.success("block set at ${pos.x} ${pos.y} ${pos.z}")
    }
}
