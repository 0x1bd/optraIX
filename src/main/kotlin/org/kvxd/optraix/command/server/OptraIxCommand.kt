package org.kvxd.optraix.command.server

import com.mojang.brigadier.CommandDispatcher
import org.kvxd.optraix.command.CommandSource
import org.kvxd.optraix.command.ServerCommand
import org.kvxd.optraix.command.literal
import org.kvxd.optraix.command.runs
import org.kvxd.optraix.redstone.optraix.NodeType
import org.kvxd.optraix.redstone.optraix.OptraIxEngine

class OptraIxCommand : ServerCommand {

    override val name = "optraix"

    override val description = "compile the world into the optraix redstone engine"

    override fun register(dispatcher: CommandDispatcher<CommandSource>) {
        dispatcher.register(
            literal("optraix")
                .runs { status(it.source) }
                .then(literal("compile").runs { compile(it.source) })
                .then(literal("pause").runs { pause(it.source) })
                .then(literal("resume").runs { resume(it.source) })
                .then(literal("status").runs { status(it.source) })
        )
    }

    private fun engineOf(source: CommandSource): OptraIxEngine {
        val server = source.server
        val existing = server.engineFor(source.player)
        if (existing is OptraIxEngine) return existing
        val engine = OptraIxEngine()
        server.useEngine(source.player, engine)
        return engine
    }

    private fun compile(source: CommandSource) {
        val engine = engineOf(source)
        val server = source.server
        source.reply("compiling...")
        server.submit {
            if (engine.compile(source.world)) {
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
            val engine = server.engineFor(source.player)
            if (engine !is OptraIxEngine) {
                source.reply("optraix is not the active engine")
                return@submit
            }
            if (engine.paused) {
                source.reply("optraix is already paused")
                return@submit
            }
            engine.pause(source.world)
            source.success("optraix paused, running interpreted until /optraix resume")
        }
    }

    private fun resume(source: CommandSource) {
        val engine = engineOf(source)
        val server = source.server
        server.submit {
            if (!engine.paused && engine.compiled) {
                source.reply("optraix is not paused")
                return@submit
            }
            engine.resume()
            if (engine.manualCompileRequired) {
                source.reply("bulk edits require an explicit /optraix compile")
                return@submit
            }
            server.compileRedstone(source.player, engine)
            if (engine.compiled) {
                source.success("optraix resumed (compiled in ${engine.compileMillis}ms)")
            } else {
                source.reply("compile failed: ${engine.lastError}")
            }
        }
    }

    private fun status(source: CommandSource) {
        val server = source.server
        val engine = server.engineFor(source.player)
        source.heading("optraix")
        source.reply("  engine:   ${engine.name}")
        if (engine !is OptraIxEngine) {
            source.reply("  run /optraix compile to switch to the compiled engine")
            return
        }
        val circuit = engine.circuit
        if (circuit == null) {
            val state = when {
                engine.paused -> "paused"
                engine.manualCompileRequired -> "manual compile required"
                else -> "not compiled"
            }
            source.reply("  state:    $state")
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
