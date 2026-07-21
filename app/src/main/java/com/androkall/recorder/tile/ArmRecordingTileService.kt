package com.androkall.recorder.tile

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.androkall.recorder.CallRecorderApp
import com.androkall.recorder.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Quick Settings tile: tap to arm recording for the next call
 * (works without overlay permission).
 */
class ArmRecordingTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        val app = applicationContext as CallRecorderApp
        scope.launch {
            val current = app.settingsRepository.settings.first().armedForNextCall
            app.settingsRepository.setArmedForNextCall(!current)
            refresh()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun refresh() {
        val app = applicationContext as? CallRecorderApp ?: return
        scope.launch {
            val armed = app.settingsRepository.settings.first().armedForNextCall
            qsTile?.apply {
                state = if (armed) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                label = getString(R.string.tile_arm_label)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    subtitle = if (armed) {
                        getString(R.string.tile_arm_on)
                    } else {
                        getString(R.string.tile_arm_off)
                    }
                }
                updateTile()
            }
        }
    }
}
