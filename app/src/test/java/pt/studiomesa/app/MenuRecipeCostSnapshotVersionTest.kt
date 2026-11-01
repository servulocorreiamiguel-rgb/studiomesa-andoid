package pt.studiomesa.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MenuRecipeCostSnapshotVersionTest {
    @Test
    fun matchingOperationsVersionKeepsSnapshotCurrent() {
        assertTrue(MenuRecipeCostSnapshotVersion.isCurrent(1234L, 1234L))
    }

    @Test
    fun changedOperationsVersionMakesSnapshotStale() {
        assertFalse(MenuRecipeCostSnapshotVersion.isCurrent(1234L, 1235L))
    }

    @Test
    fun missingSnapshotVersionIsNeverCurrent() {
        assertFalse(MenuRecipeCostSnapshotVersion.isCurrent(-1L, 0L))
    }
}
