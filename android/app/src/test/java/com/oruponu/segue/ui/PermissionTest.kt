package com.oruponu.segue.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PermissionTest {
    @Test
    fun firstRequestAsks() {
        assertEquals(
            PermissionAction.Request,
            permissionAction(requestedBefore = false, shouldShowRationale = false),
        )
    }

    @Test
    fun deniedOnceAsksAgain() {
        assertEquals(
            PermissionAction.Request,
            permissionAction(requestedBefore = true, shouldShowRationale = true),
        )
    }

    @Test
    fun deniedWithoutRationaleOpensSettings() {
        assertEquals(
            PermissionAction.OpenSettings,
            permissionAction(requestedBefore = true, shouldShowRationale = false),
        )
    }
}
