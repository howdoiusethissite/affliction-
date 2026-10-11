package com.afflictions.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

import com.afflictions.Afflictions;

/** Plain JSON config at config/afflictions.json. Every tuning number lives here. */
public final class AfflictionsConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static AfflictionsConfig instance = new AfflictionsConfig();

	public boolean permanentConditionsSurviveDeath = true;

	public static AfflictionsConfig get() {
		return instance;
	}

	public static void load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve(Afflictions.MOD_ID + ".json");
		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path)) {
				AfflictionsConfig loaded = GSON.fromJson(reader, AfflictionsConfig.class);
				if (loaded != null) instance = loaded;
			} catch (Exception e) {
				Afflictions.LOGGER.error("Could not read {}, using defaults", path, e);
			}
		}
		// Write back so new keys added in later versions appear in the file.
		try (Writer writer = Files.newBufferedWriter(path)) {
			GSON.toJson(instance, writer);
		} catch (IOException e) {
			Afflictions.LOGGER.error("Could not write {}", path, e);
		}
	}

	private AfflictionsConfig() {
	}
}
