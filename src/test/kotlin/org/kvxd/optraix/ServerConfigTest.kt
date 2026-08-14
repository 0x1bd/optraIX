package org.kvxd.optraix

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
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
            clientUpdateRate = 750,
            compressionThreshold = -1,
            runDirectory = File("custom run"),
            autosaveSeconds = 90,
            viaversion = true,
        )

        ServerConfigCodec.write(expected, file)

        assertEquals(expected, ServerConfigCodec.read(file))
    }

    @Test
    fun `command line overrides do not modify persisted config`() {
        val directory = Files.createTempDirectory("optraix-config-overrides").toFile()
        val file = File(directory, "optraix.cfg")
        ServerConfigCodec.write(ServerConfig(port = 25565, viaversion = false), file)
        val persisted = file.readText()

        val loaded = ServerConfigLoader.load(
            arrayOf("--port", "25570", "--client-update-rate", "750", "--viaversion"),
            file,
        )

        assertEquals(25570, loaded.port)
        assertEquals(750, loaded.clientUpdateRate)
        assertTrue(loaded.viaversion)
        assertEquals(persisted, file.readText())
        assertEquals(25565, ServerConfigCodec.read(file).port)
        assertFalse(ServerConfigCodec.read(file).viaversion)
    }

    @Test
    fun `config file can be anchored to executable directory`() {
        val directory = Files.createTempDirectory("optraix-executable").toFile()
        val previous = System.getProperty("optraix.executable.dir")
        System.setProperty("optraix.executable.dir", directory.path)
        try {
            assertEquals(File(directory, "optraix.cfg").absoluteFile, ServerConfigLoader.defaultFile())
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

        val loaded = ServerConfigLoader.load(emptyArray(), file)

        assertTrue(file.isFile)
        assertEquals(loaded, ServerConfigCodec.read(file))
    }

    @Test
    fun `schema drives uniform command line syntax`() {
        val loaded = ServerConfigCodec.applyArguments(
            ServerConfig(viaversion = true),
            arrayOf("--port=25570", "--motd=hello", "--no-viaversion"),
        )

        assertEquals(25570, loaded.port)
        assertEquals("hello", loaded.motd)
        assertFalse(loaded.viaversion)
    }

    @Test
    fun `schema validation applies to files and arguments`() {
        val directory = Files.createTempDirectory("optraix-config-invalid").toFile()
        val file = File(directory, "optraix.cfg")
        file.writeText("client-update-rate=-1\n")

        assertFailsWith<IllegalArgumentException> { ServerConfigCodec.read(file) }
        assertFailsWith<IllegalArgumentException> {
            ServerConfigCodec.applyArguments(ServerConfig(), arrayOf("--port", "not-a-number"))
        }
    }
}
