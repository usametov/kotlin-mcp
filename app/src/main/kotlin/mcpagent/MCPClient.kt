package mcpagent

import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import java.io.BufferedInputStream

class MCPClient {
    private var client: Client? = null

    fun connect() = runBlocking {
        // Create client with implementation info
        client = Client(
            clientInfo = Implementation(
                name = "MCPClient",
                version = "1.0.0"
            )
        )

        // Create stdio transport (using system stdio streams)
        val transport = StdioClientTransport(
            input = BufferedInputStream(System.`in`).asSource().buffered(),
            output = System.out.asSink().buffered()
        )

        // Connect to server
        client?.connect(transport)
    }

    suspend fun getAvailableTools(): List<Any> {
        val currentClient = client ?: throw IllegalStateException("Not connected to MCP server")

        // List available tools from server
        return currentClient.listTools()?.tools ?: emptyList()
    }

    fun createToolCaller(toolName: String): suspend (Map<String, Any?>) -> Any? {
        return { args ->
            val currentClient = client ?: throw IllegalStateException("Not connected to MCP server")

            // Call tool with provided arguments
            currentClient.callTool(
                name = toolName,
                arguments = args
            )
        }
    }
}


