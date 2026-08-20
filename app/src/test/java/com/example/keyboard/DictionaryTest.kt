package com.example.keyboard

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class DictionaryTest {
    @Test
    fun testDictionaryLoad() {
        val context = RuntimeEnvironment.getApplication()
        val engine = DictionaryEngine(context)
        Thread.sleep(5000) // Wait for coroutine
        assert(engine.isReady)
    }

    @Test
    fun testWordExistsGiveWithLoadedDictionary() {
        val context = RuntimeEnvironment.getApplication()
        val importsDir = File(context.filesDir, "imported_dicts")
        if (!importsDir.exists()) importsDir.mkdirs()

        // Populate a sample dictionary with "give" and "given" in combined format
        val sampleDict = """
            dictionary=main_en
            word=give,f=15000
            word=given,f=12000
            word=receive,f=10000
            word=the,f=50000
        """.trimIndent()
        val dictFile = File(importsDir, "sample_dict.txt")
        dictFile.writeText(sampleDict)

        val engine = DictionaryEngine(context)
        
        // Wait 5 seconds plus extra buffer for loading and caching
        Thread.sleep(6000)

        val giveExists = engine.wordExists("give")
        val yoExists = engine.wordExists("yo")
        val givenExists = engine.wordExists("given")
        
        println("TEST_RESULT: wordExists(\"give\") = $giveExists")
        println("TEST_RESULT: wordExists(\"yo\") = $yoExists")
        println("TEST_RESULT: wordExists(\"given\") = $givenExists")

        assert(giveExists)
    }
}

