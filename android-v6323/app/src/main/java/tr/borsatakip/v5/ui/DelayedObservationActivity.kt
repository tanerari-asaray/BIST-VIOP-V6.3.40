package tr.borsatakip.v5.ui

import android.content.Intent
import android.os.Bundle

/** Retained only as a redirect; the 30-symbol legacy screen cannot be shown. */
class DelayedObservationActivity : BaseActivity() {
    companion object {
        const val EXTRA_AUTO_START = "tr.borsatakip.v5.EXTRA_AUTO_START_DELAYED_OBSERVATION"
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, FullUniverseScanActivity::class.java)
            .putExtra(FullUniverseScanActivity.EXTRA_LOCAL_DELAYED_MODE, true)
            .putExtra(FullUniverseScanActivity.EXTRA_AUTO_START,
                intent.getBooleanExtra(EXTRA_AUTO_START, false)))
        finish()
    }
}
