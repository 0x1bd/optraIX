package org.kvxd.optraix.block

import org.kvxd.optraix.block.property.BlockDirection
import org.kvxd.optraix.block.property.BlockFacing
import org.kvxd.optraix.block.property.ComparatorMode
import org.kvxd.optraix.block.property.HopperFacing
import org.kvxd.optraix.block.property.Instrument
import org.kvxd.optraix.block.property.LeverFace
import org.kvxd.optraix.block.property.SlabType
import org.kvxd.optraix.block.property.TrapdoorHalf
import org.kvxd.optraix.block.property.WireSide
import org.kvxd.optraix.mcdata.v1_20_4.Blocks
import org.kvxd.kmcprotocol.data.BlockData

object BlockStates {

    val signNames = listOf(
        "oak", "spruce", "birch", "acacia", "jungle", "dark_oak",
        "crimson", "warped", "bamboo", "cherry", "mangrove",
    )

    private val wallSignFacingNames = listOf(
        "oak", "spruce", "birch", "acacia", "jungle", "dark_oak", "crimson", "warped",
    ).map { "${it}_wall_sign" }.toHashSet()

    private val shortButtonNames = setOf("stone_button", "polished_blackstone_button")

    private val pressurePlateNames = listOf(
        "oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry",
        "bamboo", "crimson", "warped", "polished_blackstone", "stone",
    ).map { "${it}_pressure_plate" }.toHashSet()

    private val placeableInNames = hashSetOf(
        "air", "void_air", "cave_air",
        "water", "lava",
        "short_grass", "fern", "dead_bush",
        "seagrass", "tall_seagrass",
        "tall_grass", "large_fern",
    )

    private val count = mcData.blockStateCount

    val kind = ByteArray(count)
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

    val wireType = Blocks.RedstoneWire
    val repeaterType = Blocks.Repeater
    val comparatorType = Blocks.Comparator
    val leverType = Blocks.Lever
    val buttonType = Blocks.StoneButton
    val torchType = Blocks.RedstoneTorch
    val wallTorchType = Blocks.RedstoneWallTorch
    val lampType = Blocks.RedstoneLamp
    val noteBlockType = Blocks.NoteBlock
    val trapdoorType = Blocks.IronTrapdoor
    val observerType = Blocks.Observer
    val tripwireHookType = Blocks.TripwireHook
    val targetType = Blocks.Target
    val seaPickleType = Blocks.SeaPickle
    val endPortalFrameType = Blocks.EndPortalFrame
    val furnaceType = Blocks.Furnace
    val hopperType = Blocks.Hopper
    val barrelType = Blocks.Barrel

    private val wireNorthProp = wireType.requireProperty("north")
    private val wireSouthProp = wireType.requireProperty("south")
    private val wireEastProp = wireType.requireProperty("east")
    private val wireWestProp = wireType.requireProperty("west")
    private val wirePowerProp = wireType.requireProperty("power")

    private val repeaterDelayProp = repeaterType.requireProperty("delay")
    private val repeaterFacingProp = repeaterType.requireProperty("facing")
    private val repeaterLockedProp = repeaterType.requireProperty("locked")
    private val repeaterPoweredProp = repeaterType.requireProperty("powered")

    private val comparatorFacingProp = comparatorType.requireProperty("facing")
    private val comparatorModeProp = comparatorType.requireProperty("mode")
    private val comparatorPoweredProp = comparatorType.requireProperty("powered")

    private val leverFaceProp = leverType.requireProperty("face")
    private val leverFacingProp = leverType.requireProperty("facing")
    private val leverPoweredProp = leverType.requireProperty("powered")

    private val buttonFaceProp = buttonType.requireProperty("face")
    private val buttonFacingProp = buttonType.requireProperty("facing")
    private val buttonPoweredProp = buttonType.requireProperty("powered")

    private val torchLitProp = torchType.requireProperty("lit")
    private val wallTorchFacingProp = wallTorchType.requireProperty("facing")
    private val wallTorchLitProp = wallTorchType.requireProperty("lit")
    private val lampLitProp = lampType.requireProperty("lit")

    private val noteInstrumentProp = noteBlockType.requireProperty("instrument")
    private val noteNoteProp = noteBlockType.requireProperty("note")
    private val notePoweredProp = noteBlockType.requireProperty("powered")

    private val trapdoorFacingProp = trapdoorType.requireProperty("facing")
    private val trapdoorHalfProp = trapdoorType.requireProperty("half")
    private val trapdoorOpenProp = trapdoorType.requireProperty("open")
    private val trapdoorPoweredProp = trapdoorType.requireProperty("powered")
    private val trapdoorWaterloggedProp = trapdoorType.requireProperty("waterlogged")

    private val targetPowerProp = targetType.requireProperty("power")

    val airState = Blocks.Air.defaultState

    init {
        for (type in mcData.blocks) {
            val name = type.name
            val isSign = name.endsWith("_sign") && !name.endsWith("_wall_sign") &&
                signNames.any { name == "${it}_sign" }
            val isWallSign = signNames.any { name == "${it}_wall_sign" }
            val isPlate = name in pressurePlateNames
            val typeKind = when {
                name == "air" -> BlockKind.Air
                name == "redstone_wire" -> BlockKind.RedstoneWire
                name == "redstone_torch" -> BlockKind.RedstoneTorch
                name == "redstone_wall_torch" -> BlockKind.RedstoneWallTorch
                name == "redstone_block" -> BlockKind.RedstoneBlock
                name == "lever" -> BlockKind.Lever
                name.endsWith("_button") -> BlockKind.Button
                name == "repeater" -> BlockKind.Repeater
                name == "comparator" -> BlockKind.Comparator
                name == "redstone_lamp" -> BlockKind.RedstoneLamp
                name == "observer" -> BlockKind.Observer
                name == "tripwire_hook" -> BlockKind.TripwireHook
                name == "target" -> BlockKind.Target
                name == "iron_trapdoor" -> BlockKind.IronTrapdoor
                name == "note_block" -> BlockKind.NoteBlock
                name == "barrel" -> BlockKind.Barrel
                name == "chest" -> BlockKind.Chest
                name == "furnace" -> BlockKind.Furnace
                name == "hopper" -> BlockKind.Hopper
                name == "cauldron" -> BlockKind.Cauldron
                name == "water_cauldron" -> BlockKind.WaterCauldron
                name == "composter" -> BlockKind.Composter
                name == "cake" -> BlockKind.Cake
                name == "end_portal_frame" -> BlockKind.EndPortalFrame
                name == "sea_pickle" -> BlockKind.SeaPickle
                name == "smooth_stone_slab" || name == "quartz_slab" -> BlockKind.Slab
                isPlate -> BlockKind.PressurePlate
                isSign -> BlockKind.Sign
                isWallSign -> BlockKind.WallSign
                else -> BlockKind.Other
            }

            val staticSolid = name in BlockAttributes.solid
            val staticCube = name in BlockAttributes.cube
            val staticTransparent = name in BlockAttributes.transparent
            val placeable = name in placeableInNames
            val slabTypeProp = type.property("type")

            for (state in type.minStateId..type.maxStateId) {
                kind[state] = typeKind.ordinal.toByte()
                placeableIn[state] = placeable
                stoneMaterial[state] = name in BlockAttributes.stone
                woodMaterial[state] = name in BlockAttributes.wood
                woolMaterial[state] = name in BlockAttributes.wool
                glassMaterial[state] = name in BlockAttributes.glass

                if (typeKind == BlockKind.Slab && slabTypeProp != null) {
                    val slab = SlabType.valueOf(
                        type.value(state, slabTypeProp).replaceFirstChar { it.uppercase() }
                    )
                    slabType[state] = slab.ordinal.toByte()
                    solid[state] = slab == SlabType.Double
                    transparent[state] = slab != SlabType.Double
                    cube[state] = slab == SlabType.Top
                } else {
                    solid[state] = staticSolid
                    cube[state] = staticCube
                    transparent[state] = staticTransparent
                }

                type.property("powered")?.let { powered[state] = type.value(state, it) == "true" }
                type.property("lit")?.let { lit[state] = type.value(state, it) == "true" }
                type.property("locked")?.let { locked[state] = type.value(state, it) == "true" }
                type.property("open")?.let { open[state] = type.value(state, it) == "true" }
                type.property("eye")?.let { eye[state] = type.value(state, it) == "true" }
                type.property("delay")?.let { delay[state] = type.value(state, it).toInt().toByte() }
                type.property("rotation")?.let { signRotation[state] = type.value(state, it).toInt().toByte() }
                type.property("mode")?.let {
                    comparatorMode[state] =
                        (if (type.value(state, it) == "compare") ComparatorMode.Compare else ComparatorMode.Subtract)
                            .ordinal.toByte()
                }
                type.property("half")?.let {
                    val value = type.value(state, it)
                    if (value == "top" || value == "bottom") {
                        trapdoorHalf[state] =
                            (if (value == "top") TrapdoorHalf.Top else TrapdoorHalf.Bottom).ordinal.toByte()
                    }
                }
                type.property("face")?.let {
                    leverFace[state] = when (type.value(state, it)) {
                        "floor" -> LeverFace.Floor
                        "ceiling" -> LeverFace.Ceiling
                        else -> LeverFace.Wall
                    }.ordinal.toByte()
                }
                type.property("instrument")?.let {
                    val index = type.valueIndex(state, it)
                    if (index < Instrument.Values.size) instrument[state] = index.toByte()
                }
                type.property("note")?.let { note[state] = type.value(state, it).toInt().toByte() }

                when (typeKind) {
                    BlockKind.Target -> level[state] = type.value(state, targetPowerProp).toInt().toByte()
                    BlockKind.WaterCauldron, BlockKind.Composter ->
                        type.property("level")?.let { level[state] = type.value(state, it).toInt().toByte() }
                    BlockKind.Cake ->
                        type.property("bites")?.let { level[state] = type.value(state, it).toInt().toByte() }
                    BlockKind.SeaPickle ->
                        type.property("pickles")?.let { level[state] = type.value(state, it).toInt().toByte() }
                    else -> Unit
                }

                val facingProp = type.property("facing")
                if (facingProp != null) {
                    val value = type.value(state, facingProp)
                    if (typeKind == BlockKind.Hopper) {
                        hopperFacing[state] = when (value) {
                            "down" -> HopperFacing.Down
                            "north" -> HopperFacing.North
                            "south" -> HopperFacing.South
                            "west" -> HopperFacing.West
                            else -> HopperFacing.East
                        }.ordinal.toByte()
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

                if (typeKind == BlockKind.RedstoneWire) {
                    wirePower[state] = type.value(state, wirePowerProp).toInt().toByte()
                    wireNorth[state] = type.valueIndex(state, wireNorthProp).toByte()
                    wireSouth[state] = type.valueIndex(state, wireSouthProp).toByte()
                    wireEast[state] = type.valueIndex(state, wireEastProp).toByte()
                    wireWest[state] = type.valueIndex(state, wireWestProp).toByte()
                }
            }
        }
    }

    fun kindOf(state: Int): BlockKind =
        if (state in 0 until count) BlockKind.Values[kind[state].toInt()] else BlockKind.Air

    fun isSolid(state: Int): Boolean = state in 0 until count && solid[state]

    fun isCube(state: Int): Boolean = state in 0 until count && cube[state]

    fun isTransparent(state: Int): Boolean = state in 0 until count && transparent[state]

    fun canPlaceIn(state: Int): Boolean = state in 0 until count && placeableIn[state]

    fun isSign(state: Int): Boolean = kindOf(state) == BlockKind.Sign

    fun isWallSign(state: Int): Boolean = kindOf(state) == BlockKind.WallSign

    fun hasBlockEntity(state: Int): Boolean = when (kindOf(state)) {
        BlockKind.Sign, BlockKind.WallSign, BlockKind.Comparator,
        BlockKind.Barrel, BlockKind.Chest, BlockKind.Furnace, BlockKind.Hopper -> true
        else -> false
    }

    fun wallSignFacing(state: Int): BlockDirection? =
        if (kindOf(state) == BlockKind.WallSign && mcData.requireBlockByStateId(state).name in wallSignFacingNames)
            directionOf(state) else null

    fun pressurePlatePowered(state: Int): Boolean? =
        if (kindOf(state) == BlockKind.PressurePlate) powered[state] else null

    fun directionOf(state: Int): BlockDirection? {
        val ordinal = direction[state].toInt()
        return if (ordinal < 0) null else BlockDirection.Values[ordinal]
    }

    fun facingOf(state: Int): BlockFacing? {
        val ordinal = facing[state].toInt()
        return if (ordinal < 0) null else BlockFacing.Values[ordinal]
    }

    fun leverFaceOf(state: Int): LeverFace = LeverFace.entries[leverFace[state].toInt().coerceAtLeast(0)]

    fun comparatorModeOf(state: Int): ComparatorMode =
        ComparatorMode.entries[comparatorMode[state].toInt().coerceAtLeast(0)]

    fun wireSideOf(value: Byte): WireSide = WireSide.entries[value.toInt()]

    fun instrumentOf(state: Int): Instrument =
        Instrument.Values[instrument[state].toInt().coerceAtLeast(0)]

    fun wireState(
        north: WireSide,
        south: WireSide,
        east: WireSide,
        west: WireSide,
        power: Int,
    ): Int = wireType.minStateId +
        north.ordinal * wireNorthProp.stride +
        south.ordinal * wireSouthProp.stride +
        east.ordinal * wireEastProp.stride +
        west.ordinal * wireWestProp.stride +
        power * wirePowerProp.stride

    fun wireWithPower(state: Int, power: Int): Int =
        state + (power - wirePower[state]) * wirePowerProp.stride

    fun repeaterState(delay: Int, facing: BlockDirection, locked: Boolean, powered: Boolean): Int =
        repeaterType.minStateId +
            (delay - 1) * repeaterDelayProp.stride +
            facing.ordinal * repeaterFacingProp.stride +
            (if (locked) 0 else 1) * repeaterLockedProp.stride +
            (if (powered) 0 else 1) * repeaterPoweredProp.stride

    fun comparatorState(facing: BlockDirection, mode: ComparatorMode, powered: Boolean): Int =
        comparatorType.minStateId +
            facing.ordinal * comparatorFacingProp.stride +
            mode.ordinal * comparatorModeProp.stride +
            (if (powered) 0 else 1) * comparatorPoweredProp.stride

    fun leverState(face: LeverFace, facing: BlockDirection, powered: Boolean): Int =
        leverType.minStateId +
            face.ordinal * leverFaceProp.stride +
            facing.ordinal * leverFacingProp.stride +
            (if (powered) 0 else 1) * leverPoweredProp.stride

    fun buttonState(face: LeverFace, facing: BlockDirection, powered: Boolean): Int =
        buttonType.minStateId +
            face.ordinal * buttonFaceProp.stride +
            facing.ordinal * buttonFacingProp.stride +
            (if (powered) 0 else 1) * buttonPoweredProp.stride

    fun torchState(lit: Boolean): Int =
        torchType.minStateId + (if (lit) 0 else 1) * torchLitProp.stride

    fun wallTorchState(lit: Boolean, facing: BlockDirection): Int =
        wallTorchType.minStateId +
            facing.ordinal * wallTorchFacingProp.stride +
            (if (lit) 0 else 1) * wallTorchLitProp.stride

    fun lampState(lit: Boolean): Int =
        lampType.minStateId + (if (lit) 0 else 1) * lampLitProp.stride

    fun noteBlockState(instrument: Instrument, note: Int, powered: Boolean): Int =
        noteBlockType.minStateId +
            instrument.ordinal * noteInstrumentProp.stride +
            note * noteNoteProp.stride +
            (if (powered) 0 else 1) * notePoweredProp.stride

    fun trapdoorState(
        facing: BlockDirection,
        half: TrapdoorHalf,
        open: Boolean,
        powered: Boolean,
        waterlogged: Boolean,
    ): Int = trapdoorType.minStateId +
        facing.ordinal * trapdoorFacingProp.stride +
        half.ordinal * trapdoorHalfProp.stride +
        (if (open) 0 else 1) * trapdoorOpenProp.stride +
        (if (powered) 0 else 1) * trapdoorPoweredProp.stride +
        (if (waterlogged) 0 else 1) * trapdoorWaterloggedProp.stride

    fun buttonDuration(state: Int): Int =
        if (mcData.requireBlockByStateId(state).name in shortButtonNames) 10 else 15

    fun buttonStateFor(type: BlockData, face: LeverFace, facing: BlockDirection, powered: Boolean): Int =
        type.stateOf(
            mapOf(
                "face" to face.name.lowercase(),
                "facing" to facing.name.lowercase(),
                "powered" to if (powered) "true" else "false",
            )
        )

    fun withPowered(state: Int, value: Boolean): Int {
        val type = mcData.requireBlockByStateId(state)
        val property = type.property("powered") ?: return state
        return type.withValue(state, property, if (value) "true" else "false")
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
