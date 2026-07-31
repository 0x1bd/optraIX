package org.kvxd.gogolmc.command

import com.mojang.brigadier.CommandDispatcher

interface GogolCommand {

    val name: String

    val aliases: List<String>
        get() = emptyList()

    val description: String

    fun register(dispatcher: CommandDispatcher<CommandSource>)
}
