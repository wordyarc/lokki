package io.lokki.gradle

import kotlin.test.Test
import kotlin.test.assertEquals

class KotlinCompatibilityTest {
    @Test
    fun testReleasesBetweenOldestAndLatestAreSupported() {
        for (version in listOf("2.4.0", "2.4.10", "2.4.20", "2.4.21")) {
            assertEquals(KotlinCompatibility.SUPPORTED, compatibility(version), version)
        }
    }

    @Test
    fun testPreReleasesUpToLatestTestedAreSupported() {
        for (version in listOf("2.4.20-Beta1", "2.4.20-RC2", "2.5.0-Beta2", "2.5.0-RC")) {
            assertEquals(KotlinCompatibility.SUPPORTED, compatibility(version), version)
        }
    }

    @Test
    fun testReleasesBeforeOldestAreOlderThanSupported() {
        for (version in listOf("1.9.25", "2.3.0", "2.3.21")) {
            assertEquals(KotlinCompatibility.OLDER_THAN_SUPPORTED, compatibility(version), version)
        }
    }

    @Test
    fun testPreReleasesOfOldestAreOlderThanSupported() {
        for (version in listOf("2.4.0-Beta1", "2.4.0-RC", "2.4.0-RC3")) {
            assertEquals(KotlinCompatibility.OLDER_THAN_SUPPORTED, compatibility(version), version)
        }
    }

    @Test
    fun testDevBuildIsTheReleaseItPrecedes() {
        assertEquals(KotlinCompatibility.SUPPORTED, compatibility("2.4.0-dev-1234"))
        assertEquals(KotlinCompatibility.OLDER_THAN_SUPPORTED, compatibility("2.3.20-dev-1234"))
        assertEquals(KotlinCompatibility.NEWER_THAN_TESTED, compatibility("2.5.0-dev-1234"))
    }

    @Test
    fun testReleaseOfLatestTestedPreReleaseIsNewerThanTested() {
        assertEquals(KotlinCompatibility.NEWER_THAN_TESTED, compatibility("2.5.0"))
        assertEquals(KotlinCompatibility.NEWER_THAN_TESTED, compatibility("2.5.0-RC2"))
    }

    @Test
    fun testLaterReleasesAreNewerThanTested() {
        for (version in listOf("2.5.20", "2.6.0-Beta1", "9.0.0")) {
            assertEquals(KotlinCompatibility.NEWER_THAN_TESTED, compatibility(version), version)
        }
    }

    @Test
    fun testLatestTestedReleaseBoundsItsPatches() {
        assertEquals(KotlinCompatibility.SUPPORTED, kotlinCompatibility("2.4.21", "2.4.0", "2.4.21"))
        assertEquals(KotlinCompatibility.NEWER_THAN_TESTED, kotlinCompatibility("2.4.30", "2.4.0", "2.4.21"))
    }

    @Test
    fun testVersionThatIsNotOfKotlinIsNotChecked() {
        for (version in listOf("", "main", "2.4")) {
            assertEquals(KotlinCompatibility.SUPPORTED, compatibility(version), version)
        }
    }

    private fun compatibility(version: String): KotlinCompatibility = kotlinCompatibility(version, "2.4.0", "2.5.0-RC")
}
