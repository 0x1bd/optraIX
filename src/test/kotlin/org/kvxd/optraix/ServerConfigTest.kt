package org.kvxd.optraix

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServerConfigTest {

    @Test
    fun `config round trips as properties`() {
        val directory = Files.createTempDirectory("optraix-config").toFile()
        val file = File(directory, "optraix.cfg")
        val expected = ServerConfig(
            host = "127.0.0.1",
            port = 25566,
            motd = "optraIX caf\u00e9",
            maxPlayers = 42,
            viewDistance = 7,
            tps = 30,
            compressionThreshold = -1,
            runDirectory = File("custom run"),
            autosaveSeconds = 90,
            viaversion = true,
        )

        expected.save(file)

        assertEquals(expected, ServerConfig.fromProperties(file))
    }

    @Test
    fun `command line overrides do not modify persisted config`() {
        val directory = Files.createTempDirectory("optraix-config-overrides").toFile()
        val file = File(directory, "optraix.cfg")
        ServerConfig(port = 25565, viaversion = false).save(file)
        val persisted = file.readText()

        val loaded = ServerConfig.load(arrayOf("--port", "25570", "--viaversion"), file)

        assertEquals(25570, loaded.port)
        assertTrue(loaded.viaversion)
        assertEquals(persisted, file.readText())
        assertEquals(25565, ServerConfig.fromProperties(file).port)
        assertFalse(ServerConfig.fromProperties(file).viaversion)
    }

    @Test
    fun `config file can be anchored to executable directory`() {
        val directory = Files.createTempDirectory("optraix-executable").toFile()
        val previous = System.getProperty("optraix.executable.dir")
        System.setProperty("optraix.executable.dir", directory.path)
        try {
            assertEquals(File(directory, "optraix.cfg").absoluteFile, ServerConfig.defaultConfigFile())
        } finally {
            if (previous == null) {
                System.clearProperty("optraix.executable.dir")
            } else {
                System.setProperty("optraix.executable.dir", previous)
            }
        }
    }

    @Test
    fun `first load creates default config`() {
        val directory = Files.createTempDirectory("optraix-config-default").toFile()
        val file = File(directory, "optraix.cfg")

        val loaded = ServerConfig.load(emptyArray(), file)

        assertTrue(file.isFile)
        assertEquals(loaded, ServerConfig.fromProperties(file))
    }
}