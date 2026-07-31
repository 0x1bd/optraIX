package org.kvxd.gogolmc.command

import com.mojang.brigadier.tree.CommandNode

object CommandUsage {

    fun render(node: CommandNode<CommandSource>): String = render(node, node.usageText)

    private fun render(node: CommandNode<CommandSource>, self: String): String {
        val children = node.children
        if (children.isEmpty()) return self

        val branches = children.map { render(it, it.usageText) }
        val tail = branches.joinToString(" | ")

        return when {
            node.command != null -> "$self [$tail]"
            branches.size > 1 -> "$self ($tail)"
            else -> "$self $tail"
        }
    }
}
