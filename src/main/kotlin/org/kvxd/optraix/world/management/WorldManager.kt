package org.kvxd.optraix.world.management

import java.io.File
import java.util.Locale
import org.kvxd.optraix.world.WorldStorage

const val DefaultWorldName = "optraix"

class WorldManager(private val directory: File) {

    private val worlds = LinkedHashMap<String, ManagedWorld>()

    init {
        directory.mkdirs()
        addRuntime(DefaultWorldName)
    }

    val default: ManagedWorld
        get() = worlds.getValue(key(DefaultWorldName))

    fun all(): Collection<ManagedWorld> = worlds.values

    fun names(): List<String> = worlds.values.map { it.name }.sortedWith(String.CASE_INSENSITIVE_ORDER)

    fun find(name: String): ManagedWorld? = worlds[key(name)]

    fun require(name: String): ManagedWorld = find(name) ?: error("unknown world $name")

    fun loadAll(): Map<String, Int> {
        directory.mkdirs()
        val restored = LinkedHashMap<String, Int>()
        val files = directory.listFiles { file -> file.isFile && file.extension.equals("world", ignoreCase = true) }
            ?.sortedBy { it.name.lowercase(Locale.ROOT) }
            .orEmpty()

        for (file in files) {
            val name = file.nameWithoutExtension
            if (!isValidName(name)) continue
            val runtime = find(name) ?: addRuntime(name)
            restored[runtime.name] = WorldStorage.load(runtime.world, file)
        }
        return restored
    }

    fun create(name: String): ManagedWorld? {
        if (!isValidName(name) || find(name) != null) return null
        val runtime = addRuntime(name)
        return try {
            WorldStorage.save(runtime.world, runtime.file)
            runtime
        } catch (cause: Throwable) {
            worlds.remove(key(runtime.name))
            throw cause
        }
    }

    fun delete(name: String): Boolean {
        val runtime = find(name) ?: return false
        if (runtime.name.equals(DefaultWorldName, ignoreCase = true)) return false
        worlds.remove(key(runtime.name))
        if (runtime.file.exists() && !runtime.file.delete()) {
            worlds[key(runtime.name)] = runtime
            return false
        }
        return true
    }

    fun reset(name: String): ManagedWorld? {
        val runtime = find(name) ?: return null
        val replacement = ManagedWorld(runtime.name, runtime.file)
        WorldStorage.save(replacement.world, replacement.file)
        worlds[key(runtime.name)] = replacement
        return replacement
    }

    fun isValidName(name: String): Boolean = NamePattern.matches(name) && name != "." && name != ".."

    fun fileFor(name: String): File = File(directory, "$name.world")

    private fun addRuntime(name: String): ManagedWorld {
        val runtime = ManagedWorld(name, fileFor(name))
        worlds[key(name)] = runtime
        return runtime
    }

    private fun key(name: String): String = name.lowercase(Locale.ROOT)

    private companion object {
        val NamePattern = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,31}")
    }
}
