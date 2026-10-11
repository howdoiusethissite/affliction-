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

	// Phase 2: phobias
	/** Blocks within which a phobic player reacts to a feared mob. */
	public double phobiaRange = 16.0;
	/** Blocks scanned around the player for lava, fire and water phobias (scans are per-block, keep this small). */
	public int phobiaEnvironmentRange = 5;
	/** A fall phobia triggers when the drop under the player is at least this many blocks. */
	public int phobiaFallDropBlocks = 8;
	/** Ticks between proximity checks. */
	public int phobiaCheckIntervalTicks = 10;
	/** Length of the Nausea burst on first sighting, in ticks. */
	public int phobiaNauseaTicks = 100;
	/** Ticks that must pass out of sight before the same phobia can trigger another Nausea burst. */
	public int phobiaNauseaCooldownTicks = 600;
	/** Darkness is refreshed to at least this many ticks while a phobia trigger is near. */
	public int phobiaDarknessTicks = 60;
	/** Kills of the feared mob needed to cure a mob phobia by exposure. 0 disables exposure cures. */
	public int exposureKillsToCure = 10;

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
