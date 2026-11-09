package mo.dev.ctrus.permissions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OemBackgroundGuideTest {
    private fun guide(manufacturer: String, brand: String = manufacturer) =
        OemBackgroundGuide.forBrand(manufacturer, brand)

    @Test
    fun newBrands() {
        assertEquals(OemBackgroundGuide.ASUS, guide("asus"))
        assertEquals(OemBackgroundGuide.TRANSSION, guide("TECNO MOBILE LIMITED", "TECNO"))
        assertEquals(OemBackgroundGuide.TRANSSION, guide("INFINIX MOBILITY LIMITED", "Infinix"))
        assertEquals(OemBackgroundGuide.TRANSSION, guide("itel"))
    }

    @Test
    fun otherBrandsGetTheGenericGuide() {
        listOf("motorola", "Sony", "Nothing", "HMD Global").forEach {
            assertEquals(it, OemBackgroundGuide.GENERIC, guide(it))
        }
    }

    @Test
    fun pixelNeedsNoGuide() {
        assertNull(guide("Google", "google"))
    }

    @Test
    fun existingBrandsAreUnchanged() {
        assertEquals(OemBackgroundGuide.XIAOMI, guide("Xiaomi", "POCO"))
        assertEquals(OemBackgroundGuide.SAMSUNG, guide("samsung"))
        assertEquals(OemBackgroundGuide.OPPO, guide("OnePlus"))
        assertEquals(OemBackgroundGuide.OPPO, guide("realme"))
        assertEquals(OemBackgroundGuide.HUAWEI, guide("HONOR"))
        assertEquals(OemBackgroundGuide.VIVO, guide("vivo"))
    }
}
