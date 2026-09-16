path = "app/src/main/java/com/example/engine/LlamaEngine.kt"
with open(path, "r") as f:
    code = f.read()

target = r"""        // Offline high-performance reasoning & code synthesis engine
        val responseText = OfflineModelResponder.generateResponse("""

replacement = r"""        // --- HACKER MODE: Localhost Bridge to Termux llama-server ---
        try {
            val client = OkHttpClient()
            val jsonPayload = JSONObject().apply {
                put("model", "qwen-local")
                put("messages", JSONArray().apply {
                    put(JSONObject().apply { put("role", "system"); put("content", systemPrompt) })
                    put(JSONObject().apply { put("role", "user"); put("content", prompt) })
                })
                put("temperature", 0.7)
                put("max_tokens", 1024)
            }
            
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = jsonPayload.toString().toRequestBody(mediaType)
            val request = Request.Builder()
                .url("http://127.0.0.1:8086/v1/chat/completions")
                .post(body)
                .build()
                
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val resBody = response.body?.string() ?: ""
                val resJson = JSONObject(resBody)
                val content = resJson.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                return content.split(Regex("(?<=\\s)|(?=\\s)|(?<=\n)|(?=\n)"))
            }
        } catch (e: Exception) {
            // Server offline, fallback
        }

        // Fallback (Agar Termux server band ho)
        val responseText = OfflineModelResponder.generateResponse("""

if target in code:
    code = code.replace(target, replacement)
    with open(path, "w") as f:
        f.write(code)
    print("✅ Patch Successful! LlamaEngine.kt updated.")
else:
    print("❌ Target block not found.")
