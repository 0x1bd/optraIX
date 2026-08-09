package org.kvxd.optraix.redstone.mchprs

import org.kvxd.optraix.block.property.BlockFace
import org.kvxd.optraix.block.BlockStates
import org.kvxd.optraix.world.BlockPos
import org.kvxd.optraix.world.World
import org.kvxd.optraix.mcdata.v1_20_4.Blocks
import org.kvxd.optraix.block.mcData
import org.kvxd.optraix.block.property.Instrument

object NoteBlock {

    private val soundIds = intArrayOf(
        945, 939, 948, 946, 940, 943, 941, 944, 942, 949, 950, 951,
        952, 953, 954, 947, 955, 956, 957, 958, 959, 960, 0,
    )

    fun isUnblocked(world: World, pos: BlockPos): Boolean =
        world.getBlock(pos.offset(BlockFace.Top)) == Blocks.Air.defaultState

    fun instrumentAt(world: World, pos: BlockPos): Instrument =
        instrumentFromBlockBelow(world.getBlock(pos.offset(BlockFace.Bottom)))

    fun instrumentFromBlockBelow(state: Int): Instrument {
        val name = mcData.requireBlockByStateId(state).name
        return when {
            BlockStates.stoneMaterial[state] -> Instrument.Basedrum
            name == "sand" -> Instrument.Snare
            BlockStates.glassMaterial[state] -> Instrument.Hat
            BlockStates.woodMaterial[state] -> Instrument.Bass
            name == "clay" -> Instrument.Flute
            name == "gold_block" -> Instrument.Bell
            BlockStates.woolMaterial[state] -> Instrument.Guitar
            name == "packed_ice" -> Instrument.Chime
            name == "bone_block" -> Instrument.Xylophone
            name == "iron_block" -> Instrument.IronXylophone
            name == "soul_sand" -> Instrument.CowBell
            name == "pumpkin" -> Instrument.Didgeridoo
            name == "emerald_block" -> Instrument.Bit
            name == "hay_block" -> Instrument.Banjo
            name == "glowstone" -> Instrument.Pling
            else -> Instrument.Harp
        }
    }

    fun playNote(world: World, pos: BlockPos, instrument: Instrument, note: Int) {
        val pitch = Math.pow(2.0, ((note % 32) - 12.0) / 12.0).toFloat()
        world.playSound(pos, soundIds[instrument.ordinal], 2, 3.0f, pitch)
    }
}
