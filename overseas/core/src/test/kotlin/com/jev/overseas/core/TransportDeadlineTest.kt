package com.jev.overseas.core

import com.jev.overseas.core.net.UrlConnectionTransport
import com.sun.net.httpserver.HttpServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.net.InetSocketAddress
import java.net.SocketTimeoutException

/** The request deadline holds even when the server keeps the connection busy with blank bytes. */
class TransportDeadlineTest {

    private fun server(handler: (com.sun.net.httpserver.HttpExchange) -> Unit): HttpServer =
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { ex -> runCatching { handler(ex) }; ex.close() }
            start()
        }

    @Test fun tricklingServerHitsTheDeadline() {
        val srv = server { ex ->
            ex.sendResponseHeaders(200, 0)
            repeat(30) { ex.responseBody.write(' '.code); ex.responseBody.flush(); Thread.sleep(100) }
        }
        try {
            val started = System.currentTimeMillis()
            try {
                UrlConnectionTransport().postJson("http://127.0.0.1:${srv.address.port}/", emptyMap(), "{}", 800)
                fail("expected a timeout")
            } catch (e: SocketTimeoutException) {
                val took = System.currentTimeMillis() - started
                assertTrue("took $took ms", took in 700..2000)
            }
        } finally {
            srv.stop(0)
        }
    }

    @Test fun normalResponseIsUnaffected() {
        val srv = server { ex ->
            val body = """{"ok":true}""".toByteArray()
            ex.sendResponseHeaders(200, body.size.toLong()); ex.responseBody.write(body)
        }
        try {
            val r = UrlConnectionTransport().postJson("http://127.0.0.1:${srv.address.port}/", emptyMap(), "{}", 2000)
            assertEquals(200, r.status)
            assertEquals("""{"ok":true}""", r.body)
        } finally {
            srv.stop(0)
        }
    }
}
