package org.kvxd.gogolmc.command

import net.benwoodworth.knbt.NbtTag
import org.kvxd.gogolmc.net.GogolServer
import org.kvxd.gogolmc.net.Text
import org.kvxd.gogolmc.player.Player
import org.kvxd.gogolmc.world.GameWorld

class CommandSource(val server: GogolServer, val player: Player) {

    val world: GameWorld
        get() = server.world

    fun reply(text: String) = player.connection.sendMessage(text)

    fun reply(content: NbtTag) = player.connection.sendMessage(content)

    fun success(text: String) = player.connection.sendMessage(Text.colored(text, Text.Green))

    fun error(text: String) = player.connection.sendMessage(Text.colored(text, Text.Red))

    fun heading(text: String) = player.connection.sendMessage(Text.bold(text, Text.Gold))

    fun detail(text: String) = player.connection.sendMessage(Text.colored(text, Text.Aqua))
}
