package mcpagent

import AIProvider
import io.github.vyfor.groqkt.GroqClient
import io.github.vyfor.groqkt.GroqModel
import io.github.vyfor.groqkt.api.chat.CompletionFunction
import io.github.vyfor.groqkt.api.chat.CompletionTool
import kotlinx.serialization.json.JsonObject

class GroqProvider(
    private val apiKey: String, // API key passed through constructor
    private val url: String
) : AIProvider {

    private val client = GroqClient(apiKey)

    override suspend fun chatCompletion(
        model: String,
        messages: List<Any>,
        tools: List<Any>?,
        maxTokens: Int?,
        temperature: Double? ): Any {

        return client.chat {
            this.model = GroqModel.valueOf(model.replace("-", "_").uppercase())

            messages {
                messages.forEach { message ->
                    when (message) {
                        is Map<*, *> -> {
                            val role = message["role"] as? String
                            val content = message["content"] as? String
                            if (role != null && content != null) {
                                when (role) {
                                    "system" -> system(content)
                                    "user" -> user(content, null)
                                    "assistant" -> assistant(content)
                                }
                            }
                        }
                    }
                }
            }

            // Convert tools using CompletionToolCall
            tools?.let { toolList ->
                this.tools = toolList.mapNotNull { tool ->
                    when (tool) {
                        is Map<*, *> -> {
                            CompletionTool(
                                function = CompletionFunction(
                                    name = tool["name"] as? String ?: "",
                                    parameters = tool["parameters"] as? JsonObject,
                                    description = tool["id"] as? String ?: ""
                                ),

                            )
                        }
                        else -> null
                    }
                }.toMutableList()
            }

            maxTokens?.let { this.maxTokens = it }
            temperature?.let { this.temperature = it }
        }
    }
}