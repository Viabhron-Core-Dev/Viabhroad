package com.example.keyboard

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class DictionaryTest {
    @Test
    fun testDictionaryLoad() {
        val context = RuntimeEnvironment.getApplication()
        val engine = DictionaryEngine(context)
        Thread.sleep(5000) // Wait for coroutine
        assert(engine.isReady)
    }
}
