package org.kvxd.gogolmc.command.server

import com.mojang.brigadier.CommandDispatcher
import org.kvxd.gogolmc.command.CommandSource
import org.kvxd.gogolmc.command.GogolCommand
import org.kvxd.gogolmc.command.literal
import org.kvxd.gogolmc.command.runs
import org.kvxd.gogolmc.redstone.opt3x.NodeType
import org.kvxd.gogolmc.redstone.opt3x.Opt3xEngine

class Opt3xCommand : GogolCommand {

    override val name = "opt3x"

    override val description = "compile the world into the opt3x redstone engine"

    override fun register(dispatcher: CommandDispatcher<CommandSource>) {
        dispatcher.register(
            literal("opt3x")
                .runs { status(it.source) }
                .then(literal("compile").runs { compile(it.source) })
                .then(literal("pause").runs { pause(it.source) })
                .then(literal("resume").runs { resume(it.source) })
                .then(literal("status").runs { status(it.source) })
        )
    }

    private fun engineOf(source: CommandSource): Opt3xEngine {
        val server = source.server
        val existing = server.engine
        if (existing is Opt3xEngine) return existing
        val engine = Opt3xEngine()
        server.useEngine(engine)
        return engine
    }

    private fun compile(source: CommandSource) {
        val engine = engineOf(source)
        val server = source.server
        source.reply("compiling...")
        server.submit {
            if (engine.compile(server.world)) {
                val circuit = engine.circuit ?: return@submit
                source.success("compile finished (${engine.compileMillis}ms)")
                source.reply("  nodes:   ${circuit.count}")
                source.reply("  edges:   ${circuit.edgeCount}")
                source.reply("  pending: ${circuit.pendingTicks}")
            } else {
                source.reply("compile failed: ${engine.lastError}")
            }
        }
    }

    private fun pause(source: CommandSource) {
        val server = source.server
        server.submit {
            val engine = server.engine
            if (engine !is Opt3xEngine) {
                source.reply("opt3x is not the active engine")
                return@submit
            }
            if (engine.paused) {
                source.reply("opt3x is already paused")
                return@submit
            }
            engine.pause(server.world)
            source.success("opt3x paused, running interpreted until /opt3x resume")
        }
    }

    private fun resume(source: CommandSource) {
        val engine = engineOf(source)
        val server = source.server
        server.submit {
            if (!engine.paused && engine.compiled) {
                source.reply("opt3x is not paused")
                return@submit
            }
            engine.resume()
            server.compileRedstone(engine)
            if (engine.compiled) {
                source.success("opt3x resumed (compiled in ${engine.compileMillis}ms)")
            } else {
                source.reply("compile failed: ${engine.lastError}")
            }
        }
    }

    private fun status(source: CommandSource) {
        val server = source.server
        val engine = server.engine
        source.heading("opt3x")
        source.reply("  engine:   ${engine.name}")
        if (engine !is Opt3xEngine) {
            source.reply("  run /opt3x compile to switch to the compiled engine")
            return
        }
        val circuit = engine.circuit
        if (circuit == null) {
            source.reply(if (engine.paused) "  state:    paused" else "  state:    not compiled")
            engine.lastError?.let { source.reply("  error:    $it") }
            return
        }
        val histogram = IntArray(NodeType.Count)
        for (node in 0 until circuit.count) histogram[circuit.typeOf(node)]++
        source.reply("  state:    compiled in ${engine.compileMillis}ms")
        source.reply("  nodes:    ${circuit.count}")
        source.reply("  edges:    ${circuit.edgeCount}")
        source.reply("  pending:  ${circuit.pendingTicks}")
        for (type in 0 until NodeType.Count) {
            if (histogram[type] > 0) source.reply("    ${NodeType.names[type]}: ${histogram[type]}")
        }
    }
}
