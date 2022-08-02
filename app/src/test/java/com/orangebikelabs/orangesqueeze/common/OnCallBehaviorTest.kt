/*
 * Copyright (c) 2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.common

import com.google.common.collect.ImmutableList
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.ListeningExecutorService
import com.orangebikelabs.orangesqueeze.app.PhoneStateReceiver
import com.orangebikelabs.orangesqueeze.common.PlayerStatus.Mode
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import io.mockk.mockk
import io.mockk.verify
import org.junit.Rule
import org.junit.Test

class OnCallBehaviorTest {
    companion object {
        val PLAYERID_ONE = PlayerId("one")
        val PLAYERID_TWO = PlayerId("two")
    }

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK(relaxUnitFun = true)
    lateinit var preferences: SBPreferences

    @MockK(relaxUnitFun = true)
    lateinit var context: SBContext

    @MockK(relaxUnitFun = true)
    lateinit var serverStatus: ServerStatus

    @MockK(relaxUnitFun = true)
    lateinit var executor: ListeningExecutorService

    private fun buildPlayerStatus(playerId: PlayerId, playerMode: Mode): PlayerStatus {
        return mockk {
            every { id } returns playerId
            every { mode } returns playerMode
        }
    }

    @Test
    fun `Tests behavior of NOTHING mode for OnCallMuteBehavior with no players`() {
        every { preferences.onCallBehavior } returns OnCallMuteBehavior.NOTHING
        assertThat(PhoneStateReceiver.doMute(preferences, context, executor)).isEmpty()
        verify { preferences.onCallBehavior }
    }

    @Test
    fun `Tests behavior of NOTHING mode for OnCallMuteBehavior with two players, the current player playing`() {
        every { preferences.onCallBehavior } returns OnCallMuteBehavior.NOTHING
        every { context.serverStatus } returns serverStatus
        every { serverStatus.availablePlayers } returns ImmutableList.of(
                buildPlayerStatus(PLAYERID_ONE, Mode.PLAYING),
                buildPlayerStatus(PLAYERID_TWO, Mode.STOPPED),
        )
        assertThat(PhoneStateReceiver.doMute(preferences, context, executor)).hasSize(0)
    }

    @Test
    fun `Tests behavior of MUTE mode for OnCallMuteBehavior with the current player playing`() {
        every { preferences.onCallBehavior } returns OnCallMuteBehavior.MUTE
        every { context.serverStatus } returns serverStatus
        every { context.newRequest("mixer", "muting", "1") } returns mockk(relaxed = true)
        every { context.playerId } returns PLAYERID_ONE
        every { serverStatus.availablePlayers } returns ImmutableList.of(
                buildPlayerStatus(PLAYERID_ONE, Mode.PLAYING),
                buildPlayerStatus(PLAYERID_TWO, Mode.STOPPED),
        )
        assertThat(PhoneStateReceiver.doMute(preferences, context, executor)).hasSize(1)
        verify(exactly = 1) { context.newRequest("mixer", "muting", "1") }
    }

    @Test
    fun `Tests behavior of MUTE mode for OnCallMuteBehavior with the current player not playing`() {
        every { preferences.onCallBehavior } returns OnCallMuteBehavior.MUTE
        every { context.serverStatus } returns serverStatus
        every { context.newRequest("mixer", "muting", "1") } returns mockk(relaxed = true)
        every { context.playerId } returns PLAYERID_TWO
        every { serverStatus.availablePlayers } returns ImmutableList.of(
                buildPlayerStatus(PLAYERID_ONE, Mode.PLAYING),
                buildPlayerStatus(PLAYERID_TWO, Mode.STOPPED),
        )
        assertThat(PhoneStateReceiver.doMute(preferences, context, executor)).hasSize(1)
        verify(exactly = 1) { context.newRequest("mixer", "muting", "1") }
    }

    @Test
    fun `Tests behavior of MUTE mode for OnCallMuteBehavior with multiple playing players`() {
        every { preferences.onCallBehavior } returns OnCallMuteBehavior.MUTE
        every { context.serverStatus } returns serverStatus
        every { context.newRequest("mixer", "muting", "1") } returns mockk(relaxed = true)
        every { context.playerId } returns PLAYERID_TWO
        every { serverStatus.availablePlayers } returns ImmutableList.of(
                buildPlayerStatus(PLAYERID_ONE, Mode.PLAYING),
                buildPlayerStatus(PLAYERID_TWO, Mode.PLAYING),
        )
        assertThat(PhoneStateReceiver.doMute(preferences, context, executor)).hasSize(2)
        verify(exactly = 2) { context.newRequest("mixer", "muting", "1") }
    }

    @Test
    fun `Tests behavior of PAUSE mode for OnCallMuteBehavior with the current player playing`() {
        every { preferences.onCallBehavior } returns OnCallMuteBehavior.PAUSE
        every { context.serverStatus } returns serverStatus
        every { context.newRequest("pause", "1") } returns mockk(relaxed = true)
        every { context.playerId } returns PLAYERID_ONE
        every { serverStatus.availablePlayers } returns ImmutableList.of(
                buildPlayerStatus(PLAYERID_ONE, Mode.PLAYING),
                buildPlayerStatus(PLAYERID_TWO, Mode.STOPPED),
        )
        assertThat(PhoneStateReceiver.doMute(preferences, context, executor)).hasSize(1)
        verify(exactly = 1) { context.newRequest("pause", "1") }
    }

    @Test
    fun `Tests behavior of PAUSE mode for OnCallMuteBehavior with the current player not playing`() {
        every { preferences.onCallBehavior } returns OnCallMuteBehavior.PAUSE
        every { context.serverStatus } returns serverStatus
        every { context.newRequest("pause", "1") } returns mockk(relaxed = true)
        every { context.playerId } returns PLAYERID_TWO
        every { serverStatus.availablePlayers } returns ImmutableList.of(
                buildPlayerStatus(PLAYERID_ONE, Mode.PLAYING),
                buildPlayerStatus(PLAYERID_TWO, Mode.STOPPED),
        )
        assertThat(PhoneStateReceiver.doMute(preferences, context, executor)).hasSize(1)
        verify(exactly = 1) { context.newRequest("pause", "1") }
    }

    @Test
    fun `Tests behavior of PAUSE mode for OnCallMuteBehavior with multiple playing players`() {
        every { preferences.onCallBehavior } returns OnCallMuteBehavior.PAUSE
        every { context.serverStatus } returns serverStatus
        every { context.newRequest("pause", "1") } returns mockk(relaxed = true)
        every { context.playerId } returns PLAYERID_TWO
        every { serverStatus.availablePlayers } returns ImmutableList.of(
                buildPlayerStatus(PLAYERID_ONE, Mode.PLAYING),
                buildPlayerStatus(PLAYERID_TWO, Mode.PLAYING),
        )
        assertThat(PhoneStateReceiver.doMute(preferences, context, executor)).hasSize(2)
        verify(exactly = 2) { context.newRequest("pause", "1") }
    }

    @Test
    fun `Tests behavior of MUTE_CURRENT mode for OnCallMuteBehavior with the current player playing`() {
        every { preferences.onCallBehavior } returns OnCallMuteBehavior.MUTE_CURRENT
        every { context.serverStatus } returns serverStatus
        every { context.newRequest("mixer", "muting", "1") } returns mockk(relaxed = true)
        every { context.playerId } returns PLAYERID_ONE
        every { serverStatus.availablePlayers } returns ImmutableList.of(
                buildPlayerStatus(PLAYERID_ONE, Mode.PLAYING),
                buildPlayerStatus(PLAYERID_TWO, Mode.PLAYING),
        )
        assertThat(PhoneStateReceiver.doMute(preferences, context, executor)).hasSize(1)
        verify(exactly = 1) { context.newRequest("mixer", "muting", "1") }
    }

    @Test
    fun `Tests behavior of MUTE_CURRENT mode for OnCallMuteBehavior with the current player NOT playing`() {
        every { preferences.onCallBehavior } returns OnCallMuteBehavior.MUTE_CURRENT
        every { context.serverStatus } returns serverStatus
        every { context.newRequest("mixer", "muting", "1") } returns mockk(relaxed = true)
        every { context.playerId } returns PLAYERID_ONE
        every { serverStatus.availablePlayers } returns ImmutableList.of(
                buildPlayerStatus(PLAYERID_ONE, Mode.STOPPED),
                buildPlayerStatus(PLAYERID_TWO, Mode.PLAYING),
        )
        assertThat(PhoneStateReceiver.doMute(preferences, context, executor)).isEmpty()
        verify(exactly = 0) { context.newRequest("mixer", "muting", "1") }
    }

    @Test
    fun `Tests behavior of PAUSE_CURRENT mode for OnCallMuteBehavior with the current player playing`() {
        every { preferences.onCallBehavior } returns OnCallMuteBehavior.PAUSE_CURRENT
        every { context.serverStatus } returns serverStatus
        every { context.newRequest("pause", "1") } returns mockk(relaxed = true)
        every { context.playerId } returns PLAYERID_ONE
        every { serverStatus.availablePlayers } returns ImmutableList.of(
                buildPlayerStatus(PLAYERID_ONE, Mode.PLAYING),
                buildPlayerStatus(PLAYERID_TWO, Mode.PLAYING),
        )
        assertThat(PhoneStateReceiver.doMute(preferences, context, executor)).hasSize(1)
        verify(exactly = 1) { context.newRequest("pause", "1") }
    }

    @Test
    fun `Tests behavior of PAUSE_CURRENT mode for OnCallMuteBehavior with the current player NOT playing`() {
        every { preferences.onCallBehavior } returns OnCallMuteBehavior.PAUSE_CURRENT
        every { context.serverStatus } returns serverStatus
        every { context.newRequest("pause", "1") } returns mockk(relaxed = true)
        every { context.playerId } returns PLAYERID_ONE
        every { serverStatus.availablePlayers } returns ImmutableList.of(
                buildPlayerStatus(PLAYERID_ONE, Mode.STOPPED),
                buildPlayerStatus(PLAYERID_TWO, Mode.PLAYING),
        )
        assertThat(PhoneStateReceiver.doMute(preferences, context, executor)).isEmpty()
        verify(exactly = 0) { context.newRequest("pause", "1") }
    }
}