package edu.cqwu.electricity.update.data

import android.content.Context
import edu.cqwu.electricity.settings.data.SettingsKeys
import edu.cqwu.electricity.settings.data.SettingsPreferences

sealed interface UpdateCheckResult {
    data class Found(
        val info: UpdateInfo,
        val channel: UpdateChannel,
    ) : UpdateCheckResult

    data object NoUpdate : UpdateCheckResult

    data object Failed : UpdateCheckResult
}

class UpdateCheckCoordinator(context: Context) {
    private val settingsPrefs = SettingsPreferences(context.applicationContext)

    suspend fun check(respectSkipped: Boolean = false): UpdateCheckResult {
        val channel = if (settingsPrefs.get(SettingsKeys.CHECK_CI_UPDATES)) {
            UpdateChannel.CI
        } else {
            UpdateChannel.STABLE
        }
        val repository = UpdateRepository(
            timeoutMs = settingsPrefs.get(SettingsKeys.UPDATE_TIMEOUT_MS).toLong(),
        )
        val info = repository.check(channel)
        val result = toUpdateCheckResult(
            info = info,
            channel = channel,
            needsUpdate = info?.let { repository.needsUpdate(it) } == true,
        )
        if (respectSkipped && result is UpdateCheckResult.Found && isSkipped(result.info)) {
            return UpdateCheckResult.NoUpdate
        }
        return result
    }

    fun isSkipped(info: UpdateInfo): Boolean =
        settingsPrefs.get(SettingsKeys.SKIPPED_UPDATE_VERSION) >= info.app.versionCode

    fun setSkipped(versionCode: Long, skipped: Boolean) {
        if (skipped) {
            settingsPrefs.set(SettingsKeys.SKIPPED_UPDATE_VERSION, versionCode)
        } else if (settingsPrefs.get(SettingsKeys.SKIPPED_UPDATE_VERSION) == versionCode) {
            settingsPrefs.set(SettingsKeys.SKIPPED_UPDATE_VERSION, 0L)
        }
    }
}

internal fun toUpdateCheckResult(
    info: UpdateInfo?,
    channel: UpdateChannel,
    needsUpdate: Boolean,
): UpdateCheckResult = when {
    info == null -> UpdateCheckResult.Failed
    needsUpdate -> UpdateCheckResult.Found(info, channel)
    else -> UpdateCheckResult.NoUpdate
}
