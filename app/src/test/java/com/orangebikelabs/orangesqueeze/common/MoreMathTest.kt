/*
 * Copyright (c) 2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MoreMathTest {
    // int tests
    @Test
    fun `Test MoreMath int in range`() {
        assertThat(MoreMath.coerceIn(5, 0, 10)).isEqualTo(5)
    }

    @Test
    fun `Test MoreMath int below range`() {
        assertThat(MoreMath.coerceIn(-5, 0, 10)).isEqualTo(0)
    }

    @Test
    fun `Test MoreMath int above range`() {
        assertThat(MoreMath.coerceIn(15, 0, 10)).isEqualTo(10)
    }

    @Test
    fun `Test MoreMath int no range`() {
        try {
            MoreMath.coerceIn(0, 10, 0)
        } catch (e: IllegalArgumentException) {
            assertThat(e).hasMessageThat().contains("Cannot coerce value to an empty range")
        }
    }

    // float tests
    @Test
    fun `Test MoreMath float in range`() {
        assertThat(MoreMath.coerceIn(5f, 0f, 10f)).isEqualTo(5)
    }

    @Test
    fun `Test MoreMath float below range`() {
        assertThat(MoreMath.coerceIn(-5f, 0f, 10f)).isEqualTo(0f)
    }

    @Test
    fun `Test MoreMath float above range`() {
        assertThat(MoreMath.coerceIn(15f, 0f, 10f)).isEqualTo(10f)
    }

    @Test
    fun `Test MoreMath float no range`() {
        try {
            MoreMath.coerceIn(0f, 10f, 0f)
        } catch (e: IllegalArgumentException) {
            assertThat(e).hasMessageThat().contains("Cannot coerce value to an empty range")
        }
    }
}