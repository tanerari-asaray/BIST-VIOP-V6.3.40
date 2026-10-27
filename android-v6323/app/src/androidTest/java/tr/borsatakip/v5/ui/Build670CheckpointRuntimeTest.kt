package tr.borsatakip.v5.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import tr.borsatakip.v5.data.FullUniversePolicy
import tr.borsatakip.v5.data.ObservationCheckpoint
import java.io.File

/** Real Android AtomicFile serialization; uses an isolated directory, never user checkpoints. */
@RunWith(AndroidJUnit4::class)
class Build670CheckpointRuntimeTest {
    private fun isolated(block: (ObservationCheckpoint, File) -> Unit) {
        val app=ApplicationProvider.getApplicationContext<Context>()
        val dir=File(app.cacheDir,"build670-checkpoint-${System.nanoTime()}").apply { mkdirs() }
        try {
            val context=object:ContextWrapper(app) {
                override fun getFilesDir(): File = dir
            }
            block(ObservationCheckpoint(context),dir)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun runIdAndBothMarketObservationsSurviveAtomicCheckpoint() = isolated { store,_ ->
        val t=1_800_000_000_000L
        val bist=FullUniversePolicy.Entry(
            FullUniversePolicy.Market.BIST,"ASELS","Test BIST",51.0,"TRY",
            FullUniversePolicy.Trend.UNKNOWN,FullUniversePolicy.Quality.DELAYED,
            "TEST_FIXTURE",t,t,"Kontrollü test")
        val viop=FullUniversePolicy.Entry(
            FullUniversePolicy.Market.VIOP,"F_XU0301026","Test VIOP",null,"TRY",
            FullUniversePolicy.Trend.UNKNOWN,FullUniversePolicy.Quality.NO_DATA,
            "TEST_FIXTURE",t,t,"Quote hatası",reasonCode="SERVICE_UNAVAILABLE")
        store.write(false,listOf("ASELS"),listOf(viop,bist),"BACKEND|fixture","run-670-abc")
        val saved=store.read()
        assertNotNull(saved)
        assertEquals("run-670-abc",saved!!.runId)
        assertEquals("BACKEND|fixture",saved.sourceIdentity)
        assertEquals(listOf("ASELS"),saved.universe)
        assertEquals(2,saved.rows.size)
        assertNull(saved.rows.single{it.market==FullUniversePolicy.Market.VIOP}.price)
        assertEquals("SERVICE_UNAVAILABLE",saved.rows.single{it.market==FullUniversePolicy.Market.VIOP}.reasonCode)
    }

    @Test fun malformedAndUnsupportedCheckpointAreNeverAccepted() = isolated { store,dir ->
        val file=File(dir,"universe-observation-v648.json")
        file.writeText("{partial")
        assertNull(store.read())
        file.writeText("""{"schemaVersion":999,"local":true,"rows":[],"universe":[],"savedAt":123}""")
        assertNull(store.read())
    }
    @Test fun viopOnlyCheckpointSurvivesBeforeBistDiscovery() = isolated { store,_ ->
        val t=1_800_000_000_000L
        val quote=FullUniversePolicy.Entry(
            FullUniversePolicy.Market.VIOP,"F_XU0301026","VIOP only",14200.0,"TRY",
            FullUniversePolicy.Trend.UNKNOWN,FullUniversePolicy.Quality.UNVERIFIED,
            "BACKEND_TEST",t,t,"VIOP was fetched independently")
        store.write(false,emptyList(),listOf(quote),"BACKEND|fixture","run-viop-only")
        val saved=store.read()
        assertNotNull(saved)
        assertEquals("run-viop-only",saved!!.runId)
        assertTrue(saved.universe.isEmpty())
        assertEquals(1,saved.rows.size)
        assertEquals(14200.0,saved.rows.single().price!!,0.001)
    }

}
