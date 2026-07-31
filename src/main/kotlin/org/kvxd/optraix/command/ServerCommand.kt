package org.kvxd.optraix.command

import com.mojang.brigadier.CommandDispatcher

interface ServerCommand {

    val name: String

    val aliases: List<String>
        get() = emptyList()

    val description: String

    fun register(dispatcher: CommandDispatcher<CommandSource>)
}
