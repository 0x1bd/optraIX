package org.kvxd.optraix.player

import org.kvxd.optraix.block.ItemStack
import org.kvxd.optraix.block.mcData
import org.kvxd.optraix.block.minecraftName
import org.kvxd.optraix.world.management.DefaultWorldName
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

class PlayerProfileStore(private val file: File) {

    private val profiles = HashMap<String, PlayerProfile>()

    val size: Int
        get() = profiles.size

    operator fun get(name: String): PlayerProfile? = profiles[name]

    fun put(player: Player) {
        profiles[player.name] = PlayerProfile.of(player)
    }

    fun load(): Int {
        if (!file.isFile) return 0
        profiles.clear()
        DataInputStream(GZIPInputStream(file.inputStream().buffered())).use { input ->
            if (input.readInt() != Magic) throw IllegalStateException("${file.name} is not a optraix player file")
            val version = input.readInt()
            if (version !in MinimumVersion..Version) {
                throw IllegalStateException("unsupported player file version $version")
            }
            repeat(input.readInt()) {
                val name = input.readUTF()
                val selectedSlot = input.readInt()
                val speed = input.readFloat()
                val x = input.readDouble()
                val y = input.readDouble()
                val z = input.readDouble()
                val yaw = input.readFloat()
                val pitch = input.readFloat()
                val flying = input.readBoolean()
                val showSelection = if (version >= 2) input.readBoolean() else true
                val showSidebar = if (version >= 2) input.readBoolean() else true
                val worldName = if (version >= 3) input.readUTF() else DefaultWorldName
                val inventory = arrayOfNulls<ItemStack>(InventorySize)
                repeat(input.readInt()) {
                    val slot = input.readInt()
                    val itemName = input.readUTF()
                    val count = input.readInt()
                    val item = mcData.item(itemName)
                    if (item != null && slot in inventory.indices) {
                        inventory[slot] = ItemStack(item, count, null)
                    }
                }
                profiles[name] = PlayerProfile(
                    inventory, worldName, selectedSlot, speed, x, y, z, yaw, pitch, flying,
                    showSelection, showSidebar,
                )
            }
        }
        return profiles.size
    }

    fun save(): Int {
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, file.name + ".tmp")
        DataOutputStream(GZIPOutputStream(temporary.outputStream().buffered())).use { output ->
            output.writeInt(Magic)
            output.writeInt(Version)
            output.writeInt(profiles.size)
            for ((name, profile) in profiles) {
                output.writeUTF(name)
                output.writeInt(profile.selectedSlot)
                output.writeFloat(profile.speedMultiplier)
                output.writeDouble(profile.x)
                output.writeDouble(profile.y)
                output.writeDouble(profile.z)
                output.writeFloat(profile.yaw)
                output.writeFloat(profile.pitch)
                output.writeBoolean(profile.flying)
                output.writeBoolean(profile.showSelection)
                output.writeBoolean(profile.showSidebar)
                output.writeUTF(profile.worldName)

                val filled = profile.inventory.withIndex().filter { it.value != null }
                output.writeInt(filled.size)
                for ((slot, stack) in filled) {
                    output.writeInt(slot)
                    output.writeUTF(stack!!.item.minecraftName)
                    output.writeInt(stack.count)
                }
            }
        }
        if (file.exists()) file.delete()
        temporary.renameTo(file)
        return profiles.size
    }

    companion object {
        private const val Magic = 0x47504C52
        private const val MinimumVersion = 1
        private const val Version = 3
        const val InventorySize = 46
    }
}
