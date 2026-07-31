package org.kvxd.optraix.command

import net.benwoodworth.knbt.NbtTag
import org.kvxd.optraix.net.OptraIxServer
import org.kvxd.optraix.net.Text
import org.kvxd.optraix.player.Player
import org.kvxd.optraix.world.GameWorld

class CommandSource(val server: OptraIxServer, val player: Player) {

    val world: GameWorld
        get() = server.world

    fun reply(text: String) = player.connection.sendMessage(text)

    fun reply(content: NbtTag) = player.connection.sendMessage(content)

    fun success(text: String) = player.connection.sendMessage(Text.colored(text, Text.Green))

    fun error(text: String) = player.connection.sendMessage(Text.colored(text, Text.Red))

    fun heading(text: String) = player.connection.sendMessage(Text.bold(text, Text.Gold))

    fun detail(text: String) = player.connection.sendMessage(Text.colored(text, Text.Aqua))
}
