package pt.studiomesa.app

/**
 * A cost analysis is current only when it was created from the latest operational
 * cache version. A missing version is never treated as current.
 */
internal object MenuRecipeCostSnapshotVersion {
    fun isCurrent(snapshotVersion: Long, currentOperationsVersion: Long): Boolean =
        snapshotVersion >= 0L && snapshotVersion == currentOperationsVersion
}
