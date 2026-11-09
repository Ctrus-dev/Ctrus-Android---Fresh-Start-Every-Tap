package mo.dev.ctrus.blocking

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockSafetyPolicyTest {
    @Test
    fun guideAppsAreProtectedOnlyOutsideChildMode() {
        listOf("com.transsion.phonemaster", "com.asus.mobilemanager", "com.coloros.safecenter").forEach {
            assertTrue(it, BlockSafetyPolicy.isOemGuideAppProtected(it, isChildMode = false))
            assertFalse(it, BlockSafetyPolicy.isOemGuideAppProtected(it, isChildMode = true))
        }
    }

    @Test
    fun otherAppsAreNotAffected() {
        assertFalse(BlockSafetyPolicy.isOemGuideAppProtected("com.instagram.android", isChildMode = false))
    }
}
