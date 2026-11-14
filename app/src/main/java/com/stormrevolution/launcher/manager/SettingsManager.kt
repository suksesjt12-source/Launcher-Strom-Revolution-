package com.stormrevolution.launcher.manager

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.stormrevolution.launcher.model.ControllerConfig
import com.stormrevolution.launcher.model.CpuMode
import com.stormrevolution.launcher.model.GamePreset
import com.stormrevolution.launcher.model.GameProfile
import com.stormrevolution.launcher.model.GraphicsBackend
import com.stormrevolution.launcher.model.TextureFilteringMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

private val Context.stormDataStore: DataStore<Preferences> by preferencesDataStore(name = "storm_launcher_prefs")

class SettingsManager(
    private val context: Context,
    private val storageManager: StorageManager
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    private object Keys {
        val GAME_PROFILE_JSON = stringPreferencesKey("game_profile_json")
        val CONTROLLER_CONFIG_JSON = stringPreferencesKey("controller_config_json")
        val GAME_TREE_URI = stringPreferencesKey("game_tree_uri")
        val COPY_GAME_TO_PRIVATE = booleanPreferencesKey("copy_game_to_private")
    }

    val gameProfileFlow: Flow<GameProfile> = context.stormDataStore.data.map { prefs ->
        val raw = prefs[Keys.GAME_PROFILE_JSON]
        if (raw.isNullOrBlank()) {
            GameProfile()
        } else {
            runCatching { json.decodeFromString<GameProfile>(raw) }.getOrElse { GameProfile() }
        }
    }

    val controllerConfigFlow: Flow<ControllerConfig> = context.stormDataStore.data.map { prefs ->
        val raw = prefs[Keys.CONTROLLER_CONFIG_JSON]
        if (raw.isNullOrBlank()) {
            ControllerConfig()
        } else {
            runCatching { json.decodeFromString<ControllerConfig>(raw) }.getOrElse { ControllerConfig() }
        }
    }

    val persistedGameUriFlow: Flow<String?> = context.stormDataStore.data.map { prefs ->
        prefs[Keys.GAME_TREE_URI]
    }

    val copyToPrivateFlow: Flow<Boolean> = context.stormDataStore.data.map { prefs ->
        prefs[Keys.COPY_GAME_TO_PRIVATE] ?: true
    }

    suspend fun saveGameProfile(profile: GameProfile) {
        context.stormDataStore.edit { prefs ->
            prefs[Keys.GAME_PROFILE_JSON] = json.encodeToString(profile)
        }
        writeGameConfigIni(profile)
    }

    suspend fun saveControllerConfig(config: ControllerConfig) {
        context.stormDataStore.edit { prefs ->
            prefs[Keys.CONTROLLER_CONFIG_JSON] = json.encodeToString(config)
        }
    }

    suspend fun saveGameUri(uriString: String?) {
        context.stormDataStore.edit { prefs ->
            if (uriString == null) prefs.remove(Keys.GAME_TREE_URI)
            else prefs[Keys.GAME_TREE_URI] = uriString
        }
    }

    suspend fun saveCopyToPrivate(copy: Boolean) {
        context.stormDataStore.edit { prefs ->
            prefs[Keys.COPY_GAME_TO_PRIVATE] = copy
        }
    }

    fun applyPreset(current: GameProfile, preset: GamePreset): GameProfile {
        return when (preset) {
            GamePreset.LOW -> current.copy(
                preset = GamePreset.LOW,
                resolutionWidth = 854,
                resolutionHeight = 480,
                fpsLimit = 30,
                vsyncEnabled = false,
                textureFiltering = TextureFilteringMode.BILINEAR,
                cpuMode = CpuMode.BALANCED,
                shaderCacheEnabled = true
            )
            GamePreset.BALANCED -> current.copy(
                preset = GamePreset.BALANCED,
                resolutionWidth = 960,
                resolutionHeight = 540,
                fpsLimit = 30,
                vsyncEnabled = false,
                graphicsBackend = GraphicsBackend.AUTOMATIC,
                textureFiltering = TextureFilteringMode.ANISOTROPIC_4X,
                cpuMode = CpuMode.BALANCED,
                shaderCacheEnabled = true
            )
            GamePreset.HIGH -> current.copy(
                preset = GamePreset.HIGH,
                resolutionWidth = 1280,
                resolutionHeight = 720,
                fpsLimit = 60,
                vsyncEnabled = false,
                textureFiltering = TextureFilteringMode.ANISOTROPIC_8X,
                cpuMode = CpuMode.PERFORMANCE,
                shaderCacheEnabled = true
            )
            GamePreset.CUSTOM -> current.copy(preset = GamePreset.CUSTOM)
        }
    }

    /**
     * Writes NSUNSR config.ini inside the game working directory or Wine prefix if present.
     */
    fun writeGameConfigIni(profile: GameProfile) {
        val targetDirs = buildList {
            if (storageManager.gameDir.exists()) add(storageManager.gameDir)
            val appDataIniDir = File(storageManager.prefixDir, "drive_c/users/storm/AppData/Local/BANDAI NAMCO Games/NSUNSR")
            if (storageManager.prefixDir.exists()) {
                appDataIniDir.mkdirs()
                add(appDataIniDir)
            }
        }
        val iniBody = buildString {
            appendLine("[screenSetting]")
            appendLine("width=${profile.resolutionWidth}")
            appendLine("height=${profile.resolutionHeight}")
            appendLine("aspectx=16")
            appendLine("aspecty=9")
            appendLine("windowed=0")
            appendLine("vsync=${if (profile.vsyncEnabled) 1 else 0}")
            appendLine("fxaa=${if (profile.preset == GamePreset.HIGH) 1 else 0}")
            appendLine("shadowQuality=${if (profile.preset == GamePreset.LOW) 0 else 1}")
            appendLine("motionBlur=0")
        }
        targetDirs.forEach { dir ->
            runCatching {
                File(dir, "config.ini").writeText(iniBody)
            }
        }
    }
}
