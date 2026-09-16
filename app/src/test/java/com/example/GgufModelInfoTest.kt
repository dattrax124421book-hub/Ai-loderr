package com.example

import com.example.engine.GgufModelInfo
import com.example.engine.HardwareProfiler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GgufModelInfoTest {

    @Test
    fun `test formatBytes formatting helper`() {
        assertEquals("2.00 GB", GgufModelInfo.formatBytes(2_147_483_648L))
        assertEquals("1.00 MB", GgufModelInfo.formatBytes(1024 * 1024L))
    }

    @Test
    fun `test recommended layers for Helio G100-Ultra with 12GB RAM`() {
        // For a 1B model (~980MB) on 12GB RAM, should recommend 24-28 layers
        val layers1B = HardwareProfiler.recommendLayersForModel(980, 12.0f)
        assertEquals(28, layers1B)

        // For a 3B model (~2300MB) on 12GB RAM, should recommend 24 layers
        val layers3B = HardwareProfiler.recommendLayersForModel(2300, 12.0f)
        assertEquals(24, layers3B)
    }
}
