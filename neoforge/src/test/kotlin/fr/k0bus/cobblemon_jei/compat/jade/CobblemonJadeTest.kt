package fr.k0bus.cobblemon_jei.compat.jade

import net.minecraft.nbt.CompoundTag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CobblemonJadeTest {

    @Test
    fun `test UIDs are correctly defined`() {
        assertEquals("cobblemon_jei:pasture_data", PasturePokemonDataProvider.UID.toString())
        assertEquals("cobblemon_jei:pasture_fullness", PastureFullnessProvider.UID.toString())
        assertEquals("cobblemon_jei:pasture_drop_timer", PastureDropTimerProvider.UID.toString())
    }

    @Test
    fun `test PastureData parsing from CompoundTag`() {
        val tag = CompoundTag()
        tag.putBoolean("CobblemonPastured", true)
        tag.putInt("CobblemonFullness", 80)
        tag.putInt("CobblemonMaxFullness", 100)
        tag.putInt("CobblemonMetabolismCycle", 2400)
        tag.putInt("CobblemonMetabolismRate", 6000)

        val accessor = createDummyAccessor(tag)
        val data = PastureData.from(accessor)

        assertNotNull(data)
        assertEquals(80, data?.fullness)
        assertEquals(100, data?.maxFullness)
        assertEquals(2400, data?.cycle)
        assertEquals(6000, data?.rate)
    }

    @Test
    fun `test PastureData returns null if not pastured`() {
        val tag = CompoundTag()
        tag.putBoolean("CobblemonPastured", false)

        val accessor = createDummyAccessor(tag)
        val data = PastureData.from(accessor)

        assertNull(data)
    }

    private fun createDummyAccessor(tag: CompoundTag): snownee.jade.api.EntityAccessor {
        val handler = java.lang.reflect.InvocationHandler { _, method, _ ->
            when (method.name) {
                "getServerData" -> tag
                else -> null
            }
        }
        return java.lang.reflect.Proxy.newProxyInstance(
            snownee.jade.api.EntityAccessor::class.java.classLoader,
            arrayOf(snownee.jade.api.EntityAccessor::class.java),
            handler
        ) as snownee.jade.api.EntityAccessor
    }
}
