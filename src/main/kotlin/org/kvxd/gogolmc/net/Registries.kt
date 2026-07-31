package org.kvxd.gogolmc.net

import net.benwoodworth.knbt.NbtByte
import net.benwoodworth.knbt.NbtCompound
import net.benwoodworth.knbt.NbtFloat
import net.benwoodworth.knbt.NbtInt
import net.benwoodworth.knbt.NbtList
import net.benwoodworth.knbt.NbtLong
import net.benwoodworth.knbt.NbtString
import net.benwoodworth.knbt.NbtTag
import org.kvxd.gogolmc.world.WORLD_HEIGHT
import org.kvxd.gogolmc.world.WORLD_MIN_Y

object Registries {

    private val damageTypes = listOf(
        "in_fire", "lightning_bolt", "on_fire", "lava", "hot_floor", "in_wall", "cramming",
        "drown", "starve", "cactus", "fall", "fly_into_wall", "out_of_world", "fell_out_of_world",
        "generic", "magic", "wither", "dragon_breath", "dry_out", "sweet_berry_bush", "freeze",
        "stalagmite", "outside_border", "generic_kill", "player_attack",
    )

    private fun entry(name: String, id: Int, element: NbtTag): NbtCompound = NbtCompound(
        mapOf(
            "name" to NbtString(name),
            "id" to NbtInt(id),
            "element" to element,
        )
    )

    private fun registry(type: String, values: List<NbtCompound>): NbtCompound = NbtCompound(
        mapOf(
            "type" to NbtString(type),
            "value" to NbtList(values),
        )
    )

    private val overworld = NbtCompound(
        mapOf(
            "fixed_time" to NbtLong(6000L),
            "has_skylight" to NbtByte(1),
            "has_ceiling" to NbtByte(0),
            "ultrawarm" to NbtByte(0),
            "natural" to NbtByte(1),
            "coordinate_scale" to NbtFloat(1.0f),
            "bed_works" to NbtByte(0),
            "respawn_anchor_works" to NbtByte(0),
            "min_y" to NbtInt(WORLD_MIN_Y),
            "height" to NbtInt(WORLD_HEIGHT),
            "logical_height" to NbtInt(WORLD_HEIGHT),
            "infiniburn" to NbtString("#minecraft:infiniburn_overworld"),
            "effects" to NbtString("minecraft:overworld"),
            "ambient_light" to NbtFloat(1.0f),
            "piglin_safe" to NbtByte(0),
            "has_raids" to NbtByte(0),
            "monster_spawn_light_level" to NbtByte(0),
            "monster_spawn_block_light_limit" to NbtByte(0),
        )
    )

    private fun biome(
        temperature: Float,
        downfall: Float,
        fogColor: Int,
        waterColor: Int,
        waterFogColor: Int,
        skyColor: Int,
    ): NbtCompound = NbtCompound(
        mapOf(
            "has_precipitation" to NbtByte(0),
            "temperature" to NbtFloat(temperature),
            "downfall" to NbtFloat(downfall),
            "effects" to NbtCompound(
                mapOf(
                    "fog_color" to NbtInt(fogColor),
                    "water_color" to NbtInt(waterColor),
                    "water_fog_color" to NbtInt(waterFogColor),
                    "sky_color" to NbtInt(skyColor),
                )
            ),
        )
    )

    val codec: NbtTag = NbtCompound(
        mapOf(
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
                        NbtCompound(
                            mapOf(
                                "message_id" to NbtString("generic"),
                                "scaling" to NbtString("always"),
                                "exhaustion" to NbtFloat(0.0f),
                            )
                        ),
                    )
                },
            ),
        )
    )
}
