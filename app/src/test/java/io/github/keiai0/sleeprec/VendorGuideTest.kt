package io.github.keiai0.sleeprec

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VendorGuideTest {
    @Test fun oppoFamilyIsColorOs() {
        assertEquals(Vendor.COLOROS, VendorGuide.vendorOf("OPPO", "OPPO"))
        assertEquals(Vendor.COLOROS, VendorGuide.vendorOf("realme", "realme"))
        assertEquals(Vendor.COLOROS, VendorGuide.vendorOf("OnePlus", "OnePlus"))
    }

    @Test fun xiaomiFamily() {
        assertEquals(Vendor.XIAOMI, VendorGuide.vendorOf("Xiaomi", "Redmi"))
        assertEquals(Vendor.XIAOMI, VendorGuide.vendorOf("Xiaomi", "POCO"))
    }

    @Test fun brandIsUsedWhenManufacturerIsOdd() = assertEquals(Vendor.XIAOMI, VendorGuide.vendorOf("unknown", "Redmi"))

    @Test fun otherVendors() {
        assertEquals(Vendor.HUAWEI, VendorGuide.vendorOf("HUAWEI", "HONOR"))
        assertEquals(Vendor.SAMSUNG, VendorGuide.vendorOf("samsung", "samsung"))
        assertEquals(Vendor.VIVO, VendorGuide.vendorOf("vivo", "iQOO"))
    }

    @Test fun unknownVendorIsGeneric() {
        assertEquals(Vendor.GENERIC, VendorGuide.vendorOf("Google", "google"))
        assertEquals(Vendor.GENERIC, VendorGuide.vendorOf("", ""))
    }

    @Test fun caseInsensitive() = assertEquals(Vendor.SAMSUNG, VendorGuide.vendorOf("SAMSUNG", "SaMsUnG"))

    @Test fun vendorsWithDedicatedScreensHaveTargets() {
        for (v in listOf(Vendor.XIAOMI, Vendor.HUAWEI, Vendor.SAMSUNG, Vendor.VIVO)) {
            assertTrue("$v", VendorGuide.targets(v).isNotEmpty())
        }
    }

    @Test fun colorOsAndGenericGoThroughAppInfo() {
        assertTrue(VendorGuide.targets(Vendor.COLOROS).isEmpty())
        assertTrue(VendorGuide.targets(Vendor.GENERIC).isEmpty())
    }

    @Test fun targetsAreFullyQualified() {
        for (v in Vendor.entries) for (t in VendorGuide.targets(v)) {
            assertTrue(t.pkg.contains('.'))
            assertTrue(t.cls.startsWith(t.pkg.substringBeforeLast('.')) || t.cls.contains('.'))
        }
    }
}
