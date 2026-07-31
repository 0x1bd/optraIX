package org.kvxd.gogolmc.redstone.mchprs

import org.kvxd.gogolmc.block.property.BlockFace
import org.kvxd.gogolmc.block.BlockStates
import org.kvxd.gogolmc.block.Blocks
import org.kvxd.gogolmc.block.property.Instrument
import org.kvxd.gogolmc.world.BlockPos
import org.kvxd.gogolmc.world.World

object NoteBlock {

    private val soundIds = intArrayOf(
        945, 939, 948, 946, 940, 943, 941, 944, 942, 949, 950, 951,
        952, 953, 954, 947, 955, 956, 957, 958, 959, 960, 0,
    )

    fun isUnblocked(world: World, pos: BlockPos): Boolean =
        world.getBlock(pos.offset(BlockFace.Top)) == Blocks.airState

    fun instrumentAt(world: World, pos: BlockPos): Instrument =
        instrumentFromBlockBelow(world.getBlock(pos.offset(BlockFace.Bottom)))

    fun instrumentFromBlockBelow(state: Int): Instrument {
        val name = Blocks.nameOf(state)
        return when {
            BlockStates.stoneMaterial[state] -> Instrument.Basedrum
            name == "minecraft:sand" -> Instrument.Snare
            BlockStates.glassMaterial[state] -> Instrument.Hat
            BlockStates.woodMaterial[state] -> Instrument.Bass
            name == "minecraft:clay" -> Instrument.Flute
            name == "minecraft:gold_block" -> Instrument.Bell
            BlockStates.woolMaterial[state] -> Instrument.Guitar
            name == "minecraft:packed_ice" -> Instrument.Chime
            name == "minecraft:bone_block" -> Instrument.Xylophone
            name == "minecraft:iron_block" -> Instrument.IronXylophone
            name == "minecraft:soul_sand" -> Instrument.CowBell
            name == "minecraft:pumpkin" -> Instrument.Didgeridoo
            name == "minecraft:emerald_block" -> Instrument.Bit
            name == "minecraft:hay_block" -> Instrument.Banjo
            name == "minecraft:glowstone" -> Instrument.Pling
            else -> Instrument.Harp
        }
    }

    fun playNote(world: World, pos: BlockPos, instrument: Instrument, note: Int) {
        val pitch = Math.pow(2.0, ((note % 32) - 12.0) / 12.0).toFloat()
        world.playSound(pos, soundIds[instrument.ordinal], 2, 3.0f, pitch)
    }
}
