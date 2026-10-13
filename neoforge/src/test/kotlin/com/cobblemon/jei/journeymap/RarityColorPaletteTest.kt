package com.cobblemon.jei.journeymap

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RarityColorPaletteTest {

    @Test
    fun `test rarity classification from bucket string and weight`() {
        assertEquals(SpawnRarity.COMMON, SpawnRarity.fromBucketOrWeight("common"))
        assertEquals(SpawnRarity.UNCOMMON, SpawnRarity.fromBucketOrWeight("uncommon"))
        assertEquals(SpawnRarity.RARE, SpawnRarity.fromBucketOrWeight("rare"))
        assertEquals(SpawnRarity.ULTRA_RARE, SpawnRarity.fromBucketOrWeight("ultra-rare"))
        assertEquals(SpawnRarity.ULTRA_RARE, SpawnRarity.fromBucketOrWeight("legendary"))

        // Fallback par poids si bucket inconnu
        assertEquals(SpawnRarity.ULTRA_RARE, SpawnRarity.fromBucketOrWeight("custom", 0.1f))
        assertEquals(SpawnRarity.RARE, SpawnRarity.fromBucketOrWeight("custom", 1.5f))
        assertEquals(SpawnRarity.UNCOMMON, SpawnRarity.fromBucketOrWeight("custom", 5.0f))
        assertEquals(SpawnRarity.COMMON, SpawnRarity.fromBucketOrWeight("custom", 20.0f))
    }

    @Test
    fun `test shape properties match specified color palette`() {
        // 1. Commun : #2ECC71 (0.25f fill), #27AE60 (0.80f stroke), 1.5f width
        val commonShape = RarityColorPalette.createShapeProperties(SpawnRarity.COMMON)
        assertEquals(0x2ECC71, commonShape.fillColor and 0x00FFFFFF)
        assertEquals(0.25f, commonShape.fillOpacity)
        assertEquals(0x27AE60, commonShape.strokeColor and 0x00FFFFFF)
        assertEquals(0.80f, commonShape.strokeOpacity)
        assertEquals(1.5f, commonShape.strokeWidth)

        // 2. Peu commun : #3498DB (0.25f fill), #2980B9 (0.80f stroke), 1.5f width
        val uncommonShape = RarityColorPalette.createShapeProperties(SpawnRarity.UNCOMMON)
        assertEquals(0x3498DB, uncommonShape.fillColor and 0x00FFFFFF)
        assertEquals(0.25f, uncommonShape.fillOpacity)
        assertEquals(0x2980B9, uncommonShape.strokeColor and 0x00FFFFFF)
        assertEquals(0.80f, uncommonShape.strokeOpacity)
        assertEquals(1.5f, uncommonShape.strokeWidth)

        // 3. Rare : #9B59B6 (0.30f fill), #8E44AD (0.85f stroke), 2.0f width
        val rareShape = RarityColorPalette.createShapeProperties(SpawnRarity.RARE)
        assertEquals(0x9B59B6, rareShape.fillColor and 0x00FFFFFF)
        assertEquals(0.30f, rareShape.fillOpacity)
        assertEquals(0x8E44AD, rareShape.strokeColor and 0x00FFFFFF)
        assertEquals(0.85f, rareShape.strokeOpacity)
        assertEquals(2.0f, rareShape.strokeWidth)

        // 4. Ultra-rare : #F1C40F (0.35f fill), #F39C12 (0.90f stroke), 2.5f width
        val ultraShape = RarityColorPalette.createShapeProperties(SpawnRarity.ULTRA_RARE)
        assertEquals(0xF1C40F, ultraShape.fillColor and 0x00FFFFFF)
        assertEquals(0.35f, ultraShape.fillOpacity)
        assertEquals(0xF39C12, ultraShape.strokeColor and 0x00FFFFFF)
        assertEquals(0.90f, ultraShape.strokeOpacity)
        assertEquals(2.5f, ultraShape.strokeWidth)
    }
}
