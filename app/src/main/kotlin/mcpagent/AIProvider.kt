
interface AIProvider {
    suspend fun chatCompletion(
        model: String,
        messages: List<Any>,
        tools: List<Any>? = null,
        maxTokens: Int? = null,
        temperature: Double? = null
    ): Any
}