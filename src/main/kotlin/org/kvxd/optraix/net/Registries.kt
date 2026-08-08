package org.kvxd.optraix.net

import net.lenni0451.mcstructs.nbt.NbtTag
import net.lenni0451.mcstructs.nbt.tags.ByteTag
import net.lenni0451.mcstructs.nbt.tags.CompoundTag
import net.lenni0451.mcstructs.nbt.tags.FloatTag
import net.lenni0451.mcstructs.nbt.tags.IntTag
import net.lenni0451.mcstructs.nbt.tags.LongTag
import net.lenni0451.mcstructs.nbt.tags.StringTag
import org.kvxd.optraix.nbt.compoundOf
import org.kvxd.optraix.nbt.listOfTags
import org.kvxd.optraix.world.WORLD_HEIGHT
import org.kvxd.optraix.world.WORLD_MIN_Y

object Registries {

    private val damageTypes = listOf(
        "in_fire", "lightning_bolt", "on_fire", "lava", "hot_floor", "in_wall", "cramming",
        "drown", "starve", "cactus", "fall", "fly_into_wall", "out_of_world", "fell_out_of_world",
        "generic", "magic", "wither", "dragon_breath", "dry_out", "sweet_berry_bush", "freeze",
        "stalagmite", "outside_border", "generic_kill", "player_attack",
    )

    private fun entry(name: String, id: Int, element: NbtTag): CompoundTag = compoundOf(
        "name" to StringTag(name),
        "id" to IntTag(id),
        "element" to element,
    )

    private fun registry(type: String, values: List<CompoundTag>): CompoundTag = compoundOf(
        "type" to StringTag(type),
        "value" to listOfTags(values),
    )

    private val overworld = compoundOf(
        "fixed_time" to LongTag(6000L),
        "has_skylight" to ByteTag(1),
        "has_ceiling" to ByteTag(0),
        "ultrawarm" to ByteTag(0),
        "natural" to ByteTag(1),
        "coordinate_scale" to FloatTag(1.0f),
        "bed_works" to ByteTag(0),
        "respawn_anchor_works" to ByteTag(0),
        "min_y" to IntTag(WORLD_MIN_Y),
        "height" to IntTag(WORLD_HEIGHT),
        "logical_height" to IntTag(WORLD_HEIGHT),
        "infiniburn" to StringTag("#minecraft:infiniburn_overworld"),
        "effects" to StringTag("minecraft:overworld"),
        "ambient_light" to FloatTag(1.0f),
        "piglin_safe" to ByteTag(0),
        "has_raids" to ByteTag(0),
        "monster_spawn_light_level" to ByteTag(0),
        "monster_spawn_block_light_limit" to ByteTag(0),
    )

    private fun biome(
        temperature: Float,
        downfall: Float,
        fogColor: Int,
        waterColor: Int,
        waterFogColor: Int,
        skyColor: Int,
    ): CompoundTag = compoundOf(
        "has_precipitation" to ByteTag(0),
        "temperature" to FloatTag(temperature),
        "downfall" to FloatTag(downfall),
        "effects" to compoundOf(
            "fog_color" to IntTag(fogColor),
            "water_color" to IntTag(waterColor),
            "water_fog_color" to IntTag(waterFogColor),
            "sky_color" to IntTag(skyColor),
        ),
    )

    val codec: NbtTag = compoundOf(
        "minecraft:dimension_type" to registry(
            "minecraft:dimension_type",
            listOf(entry("minecraft:overworld", 0, overworld)),
        ),
        "minecraft:worldgen/biome" to registry(
            "minecraft:worldgen/biome",
            listOf(
                entry(
                    "minecraft:plains", 0,
                    biome(0.8f, 0.4f, 12638463, 4159204, 329011, 0x7BA4FF),
                )
            ),
        ),
        "minecraft:damage_type" to registry(
            "minecraft:damage_type",
            damageTypes.mapIndexed { index, name ->
                entry(
                    "minecraft:$name", index,
                    compoundOf(
                        "message_id" to StringTag("generic"),
                        "scaling" to StringTag("always"),
                        "exhaustion" to FloatTag(0.0f),
                    ),
                )
            },
        ),
    )
}
