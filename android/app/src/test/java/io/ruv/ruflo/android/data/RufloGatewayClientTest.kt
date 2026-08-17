package io.ruv.ruflo.android.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RufloGatewayClientTest {
    private val client = RufloGatewayClient()

    @Test
    fun `adds agents path after normalized HTTPS base URL`() {
        assertEquals(
            "https://gateway.example.com/api/v1/agents",
            client.endpoint("https://gateway.example.com/", "/api/v1/agents")
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects HTTP endpoint`() {
        client.endpoint("http://gateway.example.com", "/api/v1/agents")
    }
}
