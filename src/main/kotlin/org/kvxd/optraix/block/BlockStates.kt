package org.kvxd.optraix.block

import org.kvxd.kmcprotocol.data.BlockData
import org.kvxd.optraix.block.property.BlockDirection
import org.kvxd.optraix.block.property.BlockFacing
import org.kvxd.optraix.block.property.ComparatorMode
import org.kvxd.optraix.block.property.HopperFacing
import org.kvxd.optraix.block.property.Instrument
import org.kvxd.optraix.block.property.LeverFace
import org.kvxd.optraix.block.property.SlabType
import org.kvxd.optraix.block.property.TrapdoorHalf
import org.kvxd.optraix.block.property.WireSide
import org.kvxd.optraix.mcdata.v1_20_4.BlockStates as GeneratedBlockStates
import org.kvxd.optraix.mcdata.v1_20_4.Blocks
import org.kvxd.optraix.mcdata.v1_20_4.East2
import org.kvxd.optraix.mcdata.v1_20_4.South2
import org.kvxd.optraix.mcdata.v1_20_4.West2

object BlockStates {

    private val signBlockNames = mcData.blocks.asSequence()
        .filter {
            it.name.endsWith("_sign") &&
                !it.name.endsWith("_wall_sign") &&
                !it.name.endsWith("_hanging_sign") &&
                it.property("rotation") != null
        }
        .mapTo(HashSet()) { it.name }
    private val wallSignNames = mcData.blocks.asSequence()
        .filter { it.name.endsWith("_wall_sign") && it.property("facing") != null }
        .mapTo(HashSet()) { it.name }
    val signNames = signBlockNames.mapTo(HashSet()) { it.removeSuffix("_sign") }
    private val shortButtonNames = setOf("stone_button", "polished_blackstone_button")
    private val pressurePlateNames = mcData.blocks.asSequence()
        .filter { it.name.endsWith("_pressure_plate") && it.property("powered") != null }
        .mapTo(HashSet()) { it.name }
    private val placeableInNames = hashSetOf(
        "air", "void_air", "cave_air",
        "water", "lava",
        "short_grass", "fern", "dead_bush",
        "seagrass", "tall_seagrass",
        "tall_grass", "large_fern",
    )

    private val count = mcData.blockStateCount

    val solid = BooleanArray(count)
    val cube = BooleanArray(count)
    val transparent = BooleanArray(count)
    val placeableIn = BooleanArray(count)
    val stoneMaterial = BooleanArray(count)
    val woodMaterial = BooleanArray(count)
    val woolMaterial = BooleanArray(count)
    val glassMaterial = BooleanArray(count)

    val powered = BooleanArray(count)
    val lit = BooleanArray(count)
    val locked = BooleanArray(count)
    val open = BooleanArray(count)
    val eye = BooleanArray(count)
    val direction = ByteArray(count) { -1 }
    val facing = ByteArray(count) { -1 }
    val hopperFacing = ByteArray(count) { -1 }
    val leverFace = ByteArray(count) { -1 }
    val trapdoorHalf = ByteArray(count) { -1 }
    val slabType = ByteArray(count) { -1 }
    val comparatorMode = ByteArray(count) { -1 }
    val delay = ByteArray(count)
    val level = ByteArray(count)
    val note = ByteArray(count)
    val instrument = ByteArray(count) { -1 }
    val signRotation = ByteArray(count)
    val wirePower = ByteArray(count)
    val wireNorth = ByteArray(count)
    val wireSouth = ByteArray(count)
    val wireEast = ByteArray(count)
    val wireWest = ByteArray(count)

    val airState = Blocks.Air.defaultState

    init {
        for (type in mcData.blocks) {
            val name = type.name
            val staticSolid = name in BlockAttributes.solid
            val staticCube = name in BlockAttributes.cube
            val staticTransparent = name in BlockAttributes.transparent
            val placeable = name in placeableInNames
            val slab = type == Blocks.SmoothStoneSlab || type == Blocks.QuartzSlab
            val slabTypeProp = if (slab) type.requireProperty("type") else null
            val poweredProp = type.property("powered")
            val litProp = type.property("lit")
            val lockedProp = type.property("locked")
            val openProp = type.property("open")
            val eyeProp = type.property("eye")
            val delayProp = type.property("delay")
            val rotationProp = type.property("rotation")
            val modeProp = type.property("mode")
            val halfProp = type.property("half")
            val faceProp = type.property("face")
            val instrumentProp = type.property("instrument")
            val noteProp = type.property("note")
            val facingProp = type.property("facing")
            val levelProp = type.property("level")
            val bitesProp = type.property("bites")
            val picklesProp = type.property("pickles")

            for (state in type.minStateId..type.maxStateId) {
                placeableIn[state] = placeable
                stoneMaterial[state] = name in BlockAttributes.stone
                woodMaterial[state] = name in BlockAttributes.wood
                woolMaterial[state] = name in BlockAttributes.wool
                glassMaterial[state] = name in BlockAttributes.glass

                if (slab && slabTypeProp != null) {
                    val value = SlabType.entries[type.valueIndex(state, slabTypeProp)]
                    slabType[state] = value.ordinal.toByte()
                    solid[state] = value == SlabType.Double
                    transparent[state] = value != SlabType.Double
                    cube[state] = value == SlabType.Top
                } else {
                    solid[state] = staticSolid
                    cube[state] = staticCube
                    transparent[state] = staticTransparent
                }

                if (poweredProp != null) powered[state] = type.value(state, poweredProp) == "true"
                if (litProp != null) lit[state] = type.value(state, litProp) == "true"
                if (lockedProp != null) locked[state] = type.value(state, lockedProp) == "true"
                if (openProp != null) open[state] = type.value(state, openProp) == "true"
                if (eyeProp != null) eye[state] = type.value(state, eyeProp) == "true"
                if (delayProp != null) delay[state] = type.value(state, delayProp).toInt().toByte()
                if (rotationProp != null) signRotation[state] = type.value(state, rotationProp).toInt().toByte()
                if (modeProp != null) comparatorMode[state] = type.valueIndex(state, modeProp).toByte()
                if (halfProp != null && type.value(state, halfProp) in setOf("top", "bottom")) {
                    trapdoorHalf[state] = type.valueIndex(state, halfProp).toByte()
                }
                if (faceProp != null) leverFace[state] = type.valueIndex(state, faceProp).toByte()
                if (instrumentProp != null) {
                    val index = type.valueIndex(state, instrumentProp)
                    if (index < Instrument.entries.size) instrument[state] = index.toByte()
                }
                if (noteProp != null) note[state] = type.value(state, noteProp).toInt().toByte()

                when (type) {
                    Blocks.Target -> level[state] = GeneratedBlockStates.targetPower(state).toByte()
                    Blocks.WaterCauldron, Blocks.Composter ->
                        if (levelProp != null) level[state] = type.value(state, levelProp).toInt().toByte()
                    Blocks.Cake -> if (bitesProp != null) level[state] = type.value(state, bitesProp).toInt().toByte()
                    Blocks.SeaPickle -> if (picklesProp != null) level[state] = type.value(state, picklesProp).toInt().toByte()
                }

                if (facingProp != null) {
                    val value = type.value(state, facingProp)
                    if (type == Blocks.Hopper) {
                        hopperFacing[state] = HopperFacing.entries[type.valueIndex(state, facingProp)].ordinal.toByte()
                    } else {
                        when (value) {
                            "north" -> {
                                direction[state] = BlockDirection.North.ordinal.toByte()
                                facing[state] = BlockFacing.North.ordinal.toByte()
                            }
                            "south" -> {
                                direction[state] = BlockDirection.South.ordinal.toByte()
                                facing[state] = BlockFacing.South.ordinal.toByte()
                            }
                            "west" -> {
                                direction[state] = BlockDirection.West.ordinal.toByte()
                                facing[state] = BlockFacing.West.ordinal.toByte()
                            }
                            "east" -> {
                                direction[state] = BlockDirection.East.ordinal.toByte()
                                facing[state] = BlockFacing.East.ordinal.toByte()
                            }
                            "up" -> facing[state] = BlockFacing.Up.ordinal.toByte()
                            "down" -> facing[state] = BlockFacing.Down.ordinal.toByte()
                        }
                    }
                }

                if (type == Blocks.RedstoneWire) {
                    wirePower[state] = GeneratedBlockStates.redstoneWirePower(state).toByte()
                    wireNorth[state] = GeneratedBlockStates.redstoneWireNorth(state).ordinal.toByte()
                    wireSouth[state] = GeneratedBlockStates.redstoneWireSouth(state).ordinal.toByte()
                    wireEast[state] = GeneratedBlockStates.redstoneWireEast(state).ordinal.toByte()
                    wireWest[state] = GeneratedBlockStates.redstoneWireWest(state).ordinal.toByte()
                }
            }
        }
    }

    fun typeOf(state: Int): BlockData = mcData.blockByStateId(state) ?: Blocks.Air

    fun isType(state: Int, type: BlockData): Boolean = typeOf(state) == type

    fun isButton(state: Int): Boolean = typeOf(state).name.endsWith("_button")

    fun isPressurePlate(state: Int): Boolean = typeOf(state).name in pressurePlateNames

    fun isSolid(state: Int): Boolean = state in 0 until count && solid[state]

    fun isCube(state: Int): Boolean = state in 0 until count && cube[state]

    fun isTransparent(state: Int): Boolean = state in 0 until count && transparent[state]

    fun canPlaceIn(state: Int): Boolean = state in 0 until count && placeableIn[state]

    fun isSign(state: Int): Boolean = typeOf(state).name in signBlockNames

    fun isWallSign(state: Int): Boolean = typeOf(state).name in wallSignNames

    fun hasBlockEntity(state: Int): Boolean {
        val type = typeOf(state)
        return type == Blocks.Comparator || type == Blocks.Barrel || type == Blocks.Chest ||
            type == Blocks.Furnace || type == Blocks.Hopper || isSign(state) || isWallSign(state)
    }

    fun wallSignFacing(state: Int): BlockDirection? =
        if (isWallSign(state)) directionOf(state) else null

    fun pressurePlatePowered(state: Int): Boolean? =
        if (isPressurePlate(state)) powered[state] else null

    fun directionOf(state: Int): BlockDirection? {
        if (state !in 0 until count) return null
        val ordinal = direction[state].toInt()
        return if (ordinal < 0) null else BlockDirection.entries[ordinal]
    }

    fun facingOf(state: Int): BlockFacing? {
        if (state !in 0 until count) return null
        val ordinal = facing[state].toInt()
        return if (ordinal < 0) null else BlockFacing.entries[ordinal]
    }

    fun leverFaceOf(state: Int): LeverFace = LeverFace.entries[leverFace[state].toInt().coerceAtLeast(0)]

    fun comparatorModeOf(state: Int): ComparatorMode =
        ComparatorMode.entries[comparatorMode[state].toInt().coerceAtLeast(0)]

    fun wireSideOf(value: Byte): WireSide = WireSide.entries[value.toInt()]

    fun instrumentOf(state: Int): Instrument =
        Instrument.entries[instrument[state].toInt().coerceAtLeast(0)]

    fun wireState(
        north: WireSide,
        south: WireSide,
        east: WireSide,
        west: WireSide,
        power: Int,
    ): Int = GeneratedBlockStates.redstoneWire(
        east = East2.entries[east.ordinal],
        north = north,
        power = power,
        south = South2.entries[south.ordinal],
        west = West2.entries[west.ordinal],
    )

    fun wireWithPower(state: Int, power: Int): Int = GeneratedBlockStates.redstoneWire(
        east = GeneratedBlockStates.redstoneWireEast(state),
        north = GeneratedBlockStates.redstoneWireNorth(state),
        power = power,
        south = GeneratedBlockStates.redstoneWireSouth(state),
        west = GeneratedBlockStates.redstoneWireWest(state),
    )

    fun repeaterState(delay: Int, facing: BlockDirection, locked: Boolean, powered: Boolean): Int =
        GeneratedBlockStates.repeater(delay, facing, locked, powered)

    fun comparatorState(facing: BlockDirection, mode: ComparatorMode, powered: Boolean): Int =
        GeneratedBlockStates.comparator(facing, mode, powered)

    fun leverState(face: LeverFace, facing: BlockDirection, powered: Boolean): Int =
        GeneratedBlockStates.lever(face, facing, powered)

    fun buttonState(face: LeverFace, facing: BlockDirection, powered: Boolean): Int =
        GeneratedBlockStates.stoneButton(face, facing, powered)

    fun torchState(lit: Boolean): Int = GeneratedBlockStates.redstoneTorch(lit)

    fun wallTorchState(lit: Boolean, facing: BlockDirection): Int =
        GeneratedBlockStates.redstoneWallTorch(facing, lit)

    fun lampState(lit: Boolean): Int = GeneratedBlockStates.redstoneLamp(lit)

    fun noteBlockState(instrument: Instrument, note: Int, powered: Boolean): Int =
        GeneratedBlockStates.noteBlock(instrument, note, powered)

    fun trapdoorState(
        facing: BlockDirection,
        half: TrapdoorHalf,
        open: Boolean,
        powered: Boolean,
        waterlogged: Boolean,
    ): Int = GeneratedBlockStates.ironTrapdoor(facing, half, open, powered, waterlogged)

    fun buttonDuration(state: Int): Int =
        if (typeOf(state).name in shortButtonNames) 10 else 15

    fun buttonStateFor(type: BlockData, face: LeverFace, facing: BlockDirection, powered: Boolean): Int =
        type.stateOf(
            mapOf(
                "face" to face.name.lowercase(),
                "facing" to facing.name.lowercase(),
                "powered" to powered.toString(),
            )
        )

    fun withPowered(state: Int, value: Boolean): Int {
        val type = mcData.requireBlockByStateId(state)
        val property = type.property("powered") ?: return state
        return type.withValue(state, property, value.toString())
    }

    fun withDirection(state: Int, value: BlockDirection): Int {
        val type = mcData.requireBlockByStateId(state)
        val property = type.property("facing") ?: return state
        return type.withValue(state, property, value.name.lowercase())
    }

    fun withFacing(state: Int, value: BlockFacing): Int {
        val type = mcData.requireBlockByStateId(state)
        val property = type.property("facing") ?: return state
        return type.withValue(state, property, value.name.lowercase())
    }
}
