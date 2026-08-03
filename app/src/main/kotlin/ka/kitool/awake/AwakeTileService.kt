package ka.kitool.awake

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import ka.kitool.R

class AwakeTileService : TileService() {
    override fun onStartListening() {
        updateTile(AwakeService.isEnabled)
    }

    override fun onClick() {
        updateTile(AwakeService.toggle(this))
    }

    override fun onTileRemoved() {
        AwakeService.setEnabled(this, false)
    }

    private fun updateTile(enabled: Boolean) {
        val tile = qsTile ?: return
        val label = getString(R.string.action_keep_awake)
        tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = label
        tile.contentDescription = label
        tile.updateTile()
    }
}
