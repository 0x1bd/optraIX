package org.kvxd.optraix.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext

fun literal(name: String): LiteralArgumentBuilder<CommandSource> =
    LiteralArgumentBuilder.literal(name)

fun <T> argument(name: String, type: ArgumentType<T>): RequiredArgumentBuilder<CommandSource, T> =
    RequiredArgumentBuilder.argument(name, type)

fun <B : ArgumentBuilder<CommandSource, B>> B.runs(
    action: (CommandContext<CommandSource>) -> Unit,
): B = executes { context ->
    action(context)
    Command.SINGLE_SUCCESS
}
