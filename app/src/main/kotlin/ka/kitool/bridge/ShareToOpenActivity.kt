package ka.kitool.bridge

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import ka.kitool.R

class ShareToOpenActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val input = BridgeIntents.shareToOpen(this, intent)
        if (input == null) {
            showAndFinish(R.string.bridge_error_no_content)
            return
        }

        val target = BridgeIntents.openIntent(input)
        if (!BridgeIntents.hasExternalHandler(this, target)) {
            showAndFinish(R.string.bridge_error_no_handler)
            return
        }

        try {
            startActivity(Intent.createChooser(target, getString(R.string.chooser_open_with)))
            finish()
        } catch (_: ActivityNotFoundException) {
            showAndFinish(R.string.bridge_error_no_handler)
        } catch (_: SecurityException) {
            showAndFinish(R.string.bridge_error_no_handler)
        }
    }

    private fun showAndFinish(message: Int) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        finish()
    }
}
