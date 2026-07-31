package org.kvxd.optraix.command.server

import com.mojang.brigadier.CommandDispatcher
import org.kvxd.optraix.command.CommandSource
import org.kvxd.optraix.command.ServerCommand
import org.kvxd.optraix.command.literal
import org.kvxd.optraix.command.runs
import java.lang.management.ManagementFactory

class StatsCommand : ServerCommand {

    override val name = "stats"

    override val description = "server and redstone statistics"


    override fun register(dispatcher: CommandDispatcher<CommandSource>) {
        dispatcher.register(literal("stats").runs { report(it.source) })
    }

    private fun report(source: CommandSource) {
        val server = source.server
        val runtime = Runtime.getRuntime()
        val usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val maxMb = runtime.maxMemory() / (1024 * 1024)
        val uptime = (System.currentTimeMillis() - server.startedAt) / 1000
        val stats = server.engine.stats

        source.heading("optraix")
        source.reply("  uptime:        ${duration(uptime)}")
        source.reply("  players:       ${server.players.size}")
        source.reply("  tick:          ${server.currentTick}")
        source.reply("  tps:           ${format(server.measuredTps)} / ${server.tpsLabel()}")
        source.reply("  mspt:          ${format(server.averageMspt)}")

        source.heading("redstone")
        source.reply("  engine:        ${server.engine.name}")
        source.reply("  pending ticks: ${server.world.scheduledTicks}")
        source.reply("  block updates: ${stats.blockUpdates}")
        source.reply("  wire updates:  ${stats.wireUpdates}")
        source.reply("  ticks queued:  ${stats.scheduledTicks}")

        source.heading("world")
        source.reply("  chunks:        ${server.world.loadedChunks}")
        source.reply("  profiles:      ${server.profiles.size}")

        source.heading("jvm")
        source.reply("  heap:          ${usedMb}mb / ${maxMb}mb")
        source.reply("  threads:       ${ManagementFactory.getThreadMXBean().threadCount}")
    }

    private fun format(value: Double) = String.format("%.2f", value)

    private fun duration(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (hours > 0) "${hours}h ${minutes}m ${secs}s" else "${minutes}m ${secs}s"
    }
}
