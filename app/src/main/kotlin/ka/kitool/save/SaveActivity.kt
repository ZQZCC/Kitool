package ka.kitool.save

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import ka.kitool.R

class SaveActivity : Activity() {
    private var saveStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uris = IncomingFiles.from(intent)
        if (uris.isEmpty()) {
            showAndFinish(R.string.save_error_no_files)
            return
        }

        if (hasExternalHandler(intent)) {
            startSave(uris)
        } else {
            showConfirmation(uris)
        }
    }

    private fun hasExternalHandler(source: Intent): Boolean {
        val queryIntent =
            Intent(source).apply {
                component = null
                `package` = null
                selector = null
            }
        return packageManager
            .queryIntentActivities(queryIntent, PackageManager.MATCH_DEFAULT_ONLY)
            .any { it.activityInfo?.packageName != packageName }
    }

    private fun showConfirmation(uris: List<Uri>) {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.save_confirm_message, uris.size))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ -> startSave(uris) }
            .setOnDismissListener { finish() }
            .show()
    }

    private fun startSave(uris: List<Uri>) {
        if (saveStarted) return
        saveStarted = true
        try {
            startForegroundService(SaveService.createStartIntent(this, uris))
        } catch (_: RuntimeException) {
            Toast.makeText(this, R.string.save_error_start, Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    private fun showAndFinish(messageRes: Int) {
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
        finish()
    }
}
