package org.kvxd.gogolmc.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.exceptions.CommandSyntaxException
import org.kvxd.gogolmc.command.server.HelpCommand
import org.kvxd.gogolmc.command.server.Opt3xCommand
import org.kvxd.gogolmc.command.server.SaveCommand
import org.kvxd.gogolmc.command.server.SpeedCommand
import org.kvxd.gogolmc.command.server.StatsCommand
import org.kvxd.gogolmc.command.server.TeleportCommand
import org.kvxd.gogolmc.command.server.TpsCommand
import org.kvxd.gogolmc.command.worldedit.ContractCommand
import org.kvxd.gogolmc.command.worldedit.CopyCommand
import org.kvxd.gogolmc.command.worldedit.CountCommand
import org.kvxd.gogolmc.command.worldedit.CutCommand
import org.kvxd.gogolmc.command.worldedit.ExpandCommand
import org.kvxd.gogolmc.command.worldedit.FlipCommand
import org.kvxd.gogolmc.command.worldedit.LoadCommand
import org.kvxd.gogolmc.command.worldedit.MoveCommand
import org.kvxd.gogolmc.command.worldedit.PasteCommand
import org.kvxd.gogolmc.command.worldedit.Pos1Command
import org.kvxd.gogolmc.command.worldedit.Pos2Command
import org.kvxd.gogolmc.command.worldedit.RedoCommand
import org.kvxd.gogolmc.command.worldedit.ReplaceCommand
import org.kvxd.gogolmc.command.worldedit.RotateCommand
import org.kvxd.gogolmc.command.worldedit.SchematicCommand
import org.kvxd.gogolmc.command.worldedit.SelectionCommand
import org.kvxd.gogolmc.command.worldedit.SetCommand
import org.kvxd.gogolmc.command.worldedit.ShiftCommand
import org.kvxd.gogolmc.command.worldedit.SizeCommand
import org.kvxd.gogolmc.command.worldedit.StackCommand
import org.kvxd.gogolmc.command.worldedit.UndoCommand
import org.kvxd.gogolmc.command.worldedit.WandCommand
import org.kvxd.gogolmc.command.worldedit.WorldEdit
import org.kvxd.gogolmc.Log
import org.kvxd.gogolmc.net.GogolServer
import org.kvxd.gogolmc.player.Player
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.play.clientbound.ClientboundDeclareCommandsPacket
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.play.clientbound.ClientboundTabCompletePacket

class CommandRegistry(private val server: GogolServer) {

    val worldEdit = WorldEdit(server)

    val dispatcher = CommandDispatcher<CommandSource>()

    val commands: List<GogolCommand> = listOf(
        SpeedCommand(),
        TpsCommand(),
        StatsCommand(),
        Opt3xCommand(),
        SaveCommand(),
        TeleportCommand(),
        Pos1Command(worldEdit),
        Pos2Command(worldEdit),
        SelectionCommand(worldEdit),
        WandCommand(),
        SizeCommand(worldEdit),
        SetCommand(worldEdit),
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

    private val help: HelpCommand = HelpCommand { commands + listOf<GogolCommand>(help) }

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
