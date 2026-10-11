package io.github.hazymorning.klangbild.setup

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.hazymorning.klangbild.config.Settings
import io.github.hazymorning.klangbild.data.ha.HaTokens
import io.github.hazymorning.klangbild.text.TestTexts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.IOException
import java.net.InetAddress
import java.net.Socket
import java.net.URI

/** The setup server on real sockets: what has arrived is answered, even when the answer ends the setup. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SetupServerTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var stored = Settings(haUrl = "http://192.168.1.5:8123", haToken = "secret-token", entityId = "media_player.alt")
    private lateinit var server: SetupServer

    private val handler = SetupHandler(
        settings = { stored },
        texts = { TestTexts.german },
        discover = { _, _ -> DiscoverResult.Ok(emptyList()) },
        // As on the TV: saving leaves the setup screen, which stops the server.
        save = { updated ->
            stored = updated
            server.stop()
        },
        clientId = { null },
        exchange = { _, _, _ -> HaTokens("a", "r", 1800) },
        wrongCodeDelayMs = 0L,
    )

    @After
    fun tearDown() {
        server.stop()
        scope.cancel()
    }

    private fun start(): URI {
        server = SetupServer(scope, handler, addressProvider = { InetAddress.getLoopbackAddress() })
        server.start()
        return URI(server.origin!!)
    }

    /** Sends [body] to [path] as the page does; the whole answer, or "" when the connection was cut. */
    private fun post(at: URI, path: String, body: String): String = Socket(at.host, at.port).use { s ->
        s.soTimeout = 10_000
        val bytes = body.toByteArray()
        val head = "POST $path HTTP/1.1\r\nHost: ${at.host}:${at.port}\r\nOrigin: http://${at.host}:${at.port}\r\n" +
            "Content-Type: application/json\r\nContent-Length: ${bytes.size}\r\n\r\n"
        s.getOutputStream().apply {
            write(head.toByteArray())
            write(bytes)
            flush()
        }
        try {
            String(s.getInputStream().readBytes())
        } catch (_: IOException) {
            ""
        }
    }

    @Test
    fun `the save that ends the setup still gets its answer`() {
        val at = start()
        val answer = post(at, "/api/save", """{"code":"${handler.code}","url":"192.168.1.5:8123","entity":"media_player.wz"}""")
        assertThat(answer).startsWith("HTTP/1.1 200")
        assertThat(answer).contains(""""ok":true""")
        assertThat(stored.entityId).isEqualTo("media_player.wz")
        // Serving has stopped: nobody else gets in.
        assertThat(server.origin).isNull()
        assertThat(runCatching { Socket(at.host, at.port).close() }.isFailure).isTrue()
    }

    @Test
    fun `a request still arriving when serving stops is cut off`() {
        val at = start()
        Socket(at.host, at.port).use { s ->
            s.soTimeout = 10_000
            s.getOutputStream().apply {
                write("POST /api/state HTTP/1.1\r\nHost: ${at.host}:${at.port}\r\n".toByteArray())
                flush()
            }
            // The server has the connection before it stops.
            Thread.sleep(200)
            server.stop()
            val read = try {
                s.getInputStream().read()
            } catch (_: IOException) {
                -1
            }
            assertThat(read).isEqualTo(-1)
        }
    }
}
