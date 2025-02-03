package mcpagent

class MCPAgent(private val groqProvider: GroqProvider) {
    private val MODEL_ID = "llama-3.3-70b-versatile"
    
    private val SYSTEM_PROMPT = """
        You are a helpful assistant capable of accessing external functions and engaging in casual chat. 
        Use the responses from these function calls to provide accurate and informative answers. 
        The answers should be natural and hide the fact that you are using tools to access real-time information. 
        Guide the user about available tools and their capabilities. Always utilize tools to access real-time information when required. 
        Engage in a friendly manner to enhance the chat experience.

        # Tools
        {tools}

        # Notes
        - Ensure responses are based on the latest information available from function calls.
        - Maintain an engaging, supportive, and friendly tone throughout the dialogue.
        - Always highlight the potential of available tools to assist users comprehensively.
    """.trimIndent()

    suspend fun agentLoop(
        query: String,
        tools: Map<String, ToolInfo>,
        messages: MutableList<Map<String, Any>> = mutableListOf()
    ): Pair<String, List<Map<String, Any>>> {
        // Initialize system prompt if no messages provided
        if (messages.isEmpty()) {
            val toolDescriptions = tools.values.joinToString("\n- ") { 
                "${it.name}: ${it.schema["function"]?.toString()}" 
            }
            
            messages.add(mapOf(
                "role" to "system",
                "content" to SYSTEM_PROMPT.replace("{tools}", toolDescriptions)
            ))
        }

        // Add user query
        messages.add(mapOf("role" to "user", "content" to query))

        // Query LLM
        val firstResponse = groqProvider.chatCompletion(
            model = MODEL_ID,
            messages = messages,
            tools = tools.values.map { it.schema }
        ) as Map<String, Any>

        val stopReason = (firstResponse["choices"] as List<*>).firstOrNull()
            ?.let { (it as Map<String, Any>)["finish_reason"] as? String }

        return when (stopReason) {
            "tool_calls" -> handleToolCalls(firstResponse, tools, messages)
            "stop" -> handleStop(firstResponse, messages)
            else -> throw Exception("Unknown stop reason: $stopReason")
        }
    }

    private suspend fun handleToolCalls(
        response: Map<String, Any>,
        tools: Map<String, ToolInfo>,
        messages: MutableList<Map<String, Any>>
    ): Pair<String, List<Map<String, Any>>> {
        val toolCalls = (response["choices"] as? List<*>)?.firstOrNull()
            ?.let { (it as? Map<String, Any>)?.get("message") as? Map<String, Any> }
            ?.get("tool_calls") as? List<Map<String, Any>> ?: emptyList()

        for (toolCall in toolCalls) {
            val function = toolCall["function"] as? Map<String, Any>
            val toolName = function?.get("name") as? String
            val arguments = function?.get("arguments") as? String

            val tool = tools[toolName] ?: continue
            val args = arguments?.let {
                try {
                    it
                } catch (e: Exception) {
                    it
                }
            } ?: continue

            val toolResult = tool.callable(args)

            messages.add(mapOf(
                "role" to "tool",
                "tool_call_id" to toolCall["id"]!!,
                "name" to toolName!!,
                "content" to (toolResult ?: "Default Content")
            ))
        }

        val newResponse = groqProvider.chatCompletion(
            model = MODEL_ID,
            messages = messages
        ) as? Map<String, Any> ?: throw IllegalStateException("Invalid response from Groq")

        val content = (newResponse["choices"] as? List<*>)?.firstOrNull()
            ?.let { (it as? Map<String, Any>)?.get("message") as? Map<String, Any> }
            ?.get("content") as? String ?: ""

        messages.add(mapOf(
            "role" to "assistant",
            "content" to content
        ))

        return Pair(content, messages)
    }

    private fun handleStop(
        response: Map<String, Any>,
        messages: MutableList<Map<String, Any>>
    ): Pair<String, List<Map<String, Any>>> {

        val content = (response["choices"] as? List<*>)?.firstOrNull()
            ?.let { (it as? Map<String, Any>)?.get("message") as? Map<String, Any> }
            ?.get("content") as? String ?: ""

        messages.add(mapOf(
            "role" to "assistant",
            "content" to content
        ))

        return Pair(
            (response["choices"] as List<*>).firstOrNull()
                ?.let { (it as Map<String, Any>)["message"] as? Map<String, Any> }
                ?.let { it["content"] as? String } ?: "",
            messages
        )
    }
}

data class ToolInfo(
    val name: String,
    val callable: suspend (Any) -> Any?,
    val schema: Map<String, Any>
)
