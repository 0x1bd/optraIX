package org.kvxd.gogolmc.block


enum class BlockKind {
    Other,
    Air,
    RedstoneWire,
    RedstoneTorch,
    RedstoneWallTorch,
    RedstoneBlock,
    Lever,
    StoneButton,
    Repeater,
    Comparator,
    RedstoneLamp,
    Observer,
    TripwireHook,
    Target,
    PressurePlate,
    IronTrapdoor,
    NoteBlock,
    Barrel,
    Furnace,
    Hopper,
    Cauldron,
    WaterCauldron,
    Composter,
    Cake,
    EndPortalFrame,
    SeaPickle,
    Sign,
    WallSign,
    Slab;

    companion object {
        val Values: Array<BlockKind> = values()
    }
}
