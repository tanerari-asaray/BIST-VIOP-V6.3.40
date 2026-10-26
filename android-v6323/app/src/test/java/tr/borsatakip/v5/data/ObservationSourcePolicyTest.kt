package tr.borsatakip.v5.data

import org.junit.Assert.*
import org.junit.Test

class ObservationSourcePolicyTest {
    private val now = 1_800_000_000_000L
    private fun config(base: String = "https://a.example", revision: String = "a1") =
        BackendConnectionConfig(base, "SYNTHETIC_SECRET", revision)
    private fun resumable(saved: String, current: String, savedAt: Long = now - 1000) =
        ObservationSourcePolicy.canResume(false, false, saved, current, savedAt, now)

    @Test fun sameConfigurationResumes() {
        assertTrue(resumable(config().sourceIdentity, config().sourceIdentity))
    }

    @Test fun differentOriginCannotReuseRowsOrUniverse() {
        assertFalse(resumable(config().sourceIdentity, config("https://b.example", "b1").sourceIdentity))
    }

    @Test fun sameOriginKeyRotationCannotReuseCheckpoint() {
        assertFalse(resumable(config().sourceIdentity, config(revision = "a2").sourceIdentity))
    }

    @Test fun returningToOldOriginDoesNotReviveOldGeneration() {
        assertFalse(resumable(config().sourceIdentity, config(revision = "a3").sourceIdentity))
    }

    @Test fun legacyCheckpointWithoutIdentityIsRejected() {
        assertFalse(resumable("", config().sourceIdentity))
    }

    @Test fun expiredAndFutureCheckpointsAreRejected() {
        val id = config().sourceIdentity
        assertFalse(resumable(id, id, now - ObservationSourcePolicy.MAX_AGE_MS - 1))
        assertFalse(resumable(id, id, now + 1))
    }

    @Test fun localAndBackendModesCannotShareCheckpoint() {
        val local = ObservationSourcePolicy.identity(true, config())
        assertFalse(ObservationSourcePolicy.canResume(false, true, local, config().sourceIdentity, now, now))
        assertTrue(ObservationSourcePolicy.canResume(true, true, local,
            ObservationSourcePolicy.identity(true, config("https://b.example", "b1")), now, now))
    }
}
