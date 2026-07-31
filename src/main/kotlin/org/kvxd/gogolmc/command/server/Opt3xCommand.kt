package org.kvxd.gogolmc.command.server

import com.mojang.brigadier.CommandDispatcher
import org.kvxd.gogolmc.command.CommandSource
import org.kvxd.gogolmc.command.GogolCommand
import org.kvxd.gogolmc.command.literal
import org.kvxd.gogolmc.command.runs
import org.kvxd.gogolmc.redstone.mchprs.MchprsRedstone
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
                .then(literal("reset").runs { reset(it.source) })
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

    private fun reset(source: CommandSource) {
        val server = source.server
        server.submit {
            val engine = server.engine
            if (engine is Opt3xEngine) engine.decompile(server.world)
            server.useEngine(MchprsRedstone)
            source.success("redstone engine reset to mchprs")
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
            source.reply("  state:    not compiled")
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
