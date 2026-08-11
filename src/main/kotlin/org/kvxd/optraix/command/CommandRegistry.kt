package org.kvxd.optraix.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.exceptions.CommandSyntaxException
import org.kvxd.optraix.command.server.CalcCommand
import org.kvxd.optraix.command.server.HelpCommand
import org.kvxd.optraix.command.server.OptraIxCommand
import org.kvxd.optraix.command.server.SaveCommand
import org.kvxd.optraix.command.server.SidebarCommand
import org.kvxd.optraix.command.server.SpeedCommand
import org.kvxd.optraix.command.server.StatsCommand
import org.kvxd.optraix.command.server.StopCommand
import org.kvxd.optraix.command.server.TeleportCommand
import org.kvxd.optraix.command.server.TpsCommand
import org.kvxd.optraix.command.server.WorldCommand
import org.kvxd.optraix.command.worldedit.ContractCommand
import org.kvxd.optraix.command.worldedit.CopyCommand
import org.kvxd.optraix.command.worldedit.CountCommand
import org.kvxd.optraix.command.worldedit.CutCommand
import org.kvxd.optraix.command.worldedit.ExpandCommand
import org.kvxd.optraix.command.worldedit.FlipCommand
import org.kvxd.optraix.command.worldedit.LoadCommand
import org.kvxd.optraix.command.worldedit.MoveCommand
import org.kvxd.optraix.command.worldedit.PasteCommand
import org.kvxd.optraix.command.worldedit.Pos1Command
import org.kvxd.optraix.command.worldedit.Pos2Command
import org.kvxd.optraix.command.worldedit.RedoCommand
import org.kvxd.optraix.command.worldedit.ReplaceCommand
import org.kvxd.optraix.command.worldedit.RotateCommand
import org.kvxd.optraix.command.worldedit.SchematicCommand
import org.kvxd.optraix.command.worldedit.SelectionCommand
import org.kvxd.optraix.command.worldedit.SetCommand
import org.kvxd.optraix.command.worldedit.SetblockCommand
import org.kvxd.optraix.command.worldedit.ShiftCommand
import org.kvxd.optraix.command.worldedit.SizeCommand
import org.kvxd.optraix.command.worldedit.StackCommand
import org.kvxd.optraix.command.worldedit.UndoCommand
import org.kvxd.optraix.command.worldedit.WandCommand
import org.kvxd.optraix.command.worldedit.WorldEdit
import org.kvxd.optraix.Log
import org.kvxd.optraix.net.OptraIxServer
import org.kvxd.optraix.player.Player
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.play.clientbound.ClientboundDeclareCommandsPacket
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.play.clientbound.ClientboundTabCompletePacket

class CommandRegistry(private val server: OptraIxServer) {

    val worldEdit = WorldEdit(server)

    val dispatcher = CommandDispatcher<CommandSource>()

    val commands: List<ServerCommand> = listOf(
        CalcCommand(),
        SpeedCommand(),
        SidebarCommand(),
        TpsCommand(),
        StatsCommand(),
        OptraIxCommand(),
        SaveCommand(),
        StopCommand(),
        TeleportCommand(),
        WorldCommand(),
        Pos1Command(worldEdit),
        Pos2Command(worldEdit),
        SelectionCommand(worldEdit),
        WandCommand(),
        SizeCommand(worldEdit),
        SetCommand(worldEdit),
        SetblockCommand(worldEdit),
        ReplaceCommand(worldEdit),
        CountCommand(worldEdit),
        CopyCommand(worldEdit),
        CutCommand(worldEdit),
        PasteCommand(worldEdit),
        StackCommand(worldEdit),
        MoveCommand(worldEdit),
        ExpandCommand(worldEdit),
        ContractCommand(worldEdit),
        ShiftCommand(worldEdit),
        RotateCommand(worldEdit),
        FlipCommand(worldEdit),
        UndoCommand(worldEdit),
        RedoCommand(worldEdit),
        LoadCommand(worldEdit),
        SchematicCommand(worldEdit),
    )

    val declarePacket: ClientboundDeclareCommandsPacket by lazy { CommandTree.encode(dispatcher) }

    private val help: HelpCommand = HelpCommand { commands + listOf<ServerCommand>(help) }

    init {
        for (command in commands) command.register(dispatcher)
        help.register(dispatcher)
    }

    fun execute(player: Player, line: String) {
        val source = CommandSource(server, player)
        val input = line.trim()
        if (input.isEmpty()) return
        try {
            dispatcher.execute(input, source)
        } catch (cause: CommandSyntaxException) {
            source.error(cause.message ?: "invalid command")
        } catch (cause: Throwable) {
            Log.error("command", "${player.name} ran '/$input'", cause)
            source.error("command failed: ${Log.describe(cause)}")
        }
    }

    fun complete(player: Player, transactionId: Int, text: String) {
        val source = CommandSource(server, player)
        val input = if (text.startsWith("/")) text.substring(1) else text
        val offset = text.length - input.length
        val parsed = dispatcher.parse(input, source)
        val suggestions = dispatcher.getCompletionSuggestions(parsed).join()
        if (suggestions.isEmpty) return

        player.connection.send(
            ClientboundTabCompletePacket(
                transactionId = transactionId,
                start = suggestions.range.start + offset,
                length = suggestions.range.length,
                matches = suggestions.list.map {
                    ClientboundTabCompletePacket.Match(it.text, null)
                },
            )
        )
    }
}
