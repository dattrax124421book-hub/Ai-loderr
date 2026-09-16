package com.example

import com.example.engine.OfflineModelResponder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineModelResponderTest {

    @Test
    fun `test 3D car prompt returns valid Three js HTML`() {
        val prompt = "ak html code liko jis ma ak 3d carr ho"
        val response = OfflineModelResponder.generateResponse(
            prompt = prompt,
            systemPrompt = "",
            modelName = "Qwen2.5-Coder-0.5B-Instruct-Abliterated",
            architecture = "qwen2"
        )

        // Must contain actual HTML & Three.js code
        assertTrue(response.contains("<!DOCTYPE html>"))
        assertTrue(response.contains("three.min.js") || response.contains("THREE"))
        assertTrue(response.contains("carGroup") || response.contains("chassis"))

        // Must NOT contain the old canned template
        assertFalse(response.contains("processed 10 input tokens successfully on device"))
        assertFalse(response.contains("All computations executed 100% offline using"))
    }

    @Test
    fun `test python request generates python code`() {
        val prompt = "write a python script for scraping web data"
        val response = OfflineModelResponder.generateResponse(
            prompt = prompt,
            systemPrompt = "",
            modelName = "Qwen2.5-Coder-0.5B",
            architecture = "qwen2"
        )

        assertTrue(response.contains("```python"))
        assertTrue(response.contains("requests") || response.contains("def "))
    }

    @Test
    fun `test conversational greeting in roman urdu`() {
        val prompt = "salam bhai kaise ho"
        val response = OfflineModelResponder.generateResponse(
            prompt = prompt,
            systemPrompt = "",
            modelName = "Qwen2.5-Coder-0.5B",
            architecture = "qwen2"
        )

        assertTrue(response.contains("Walaikum Assalam") || response.contains("Hello"))
    }
}
