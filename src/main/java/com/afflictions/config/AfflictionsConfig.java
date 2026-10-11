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

	// Phase 3: fear meter. Rates are fear points per second; fear runs from 0 to 100.
	/** Ticks between fear updates. Rates are scaled so this only changes granularity, not speed. */
	public int fearCheckIntervalTicks = 20;
	/** Fear drains while the light at the player's head is at or below this level (0 to 15). */
	public int fearDarkLightLevel = 3;
	public double fearDarkDrainPerSecond = 0.5;
	/** Cave drain applies when the player is at least this many blocks below the surface in a dimension with sky light. */
	public int fearCaveDepthBlocks = 8;
	public double fearCaveDrainPerSecond = 0.25;
	/** Drain per feared thing in range (uses phobiaRange and the other phobia settings). Stacks per phobia. */
	public double fearPhobiaDrainPerSecond = 1.5;
	/** Blocks scanned around the player for lit campfires and playing jukeboxes. */
	public int fearComfortRange = 6;
	public double fearCampfireRecoveryPerSecond = 1.0;
	public double fearJukeboxRecoveryPerSecond = 1.0;
	/** Blocks within which a tamed pet owned by the player, or any villager, calms them. Counts once. */
	public double fearCompanionRange = 8.0;
	public double fearCompanionRecoveryPerSecond = 0.5;
	public double fearSleepRecoveryPerSecond = 3.0;
	/** Slow recovery that applies only when nothing is draining fear. 0 disables it. */
	public double fearPassiveRecoveryPerSecond = 0.1;
	public boolean fearHudEnabled = true;
	/** When false the bar only appears while fear is below 100. */
	public boolean fearHudAlwaysVisible = true;

	// Phase 4: infection, scars, max health loss. Health values are health points (2 per heart).
	/** Chance that a mob hit of at least infectionMinDamage opens a wound with a risk window. */
	public double infectionChance = 0.35;
	public double infectionMinDamage = 2.0;
	public int infectionRiskWindowTicks = 2400;
	/** Chance an untreated wound sets in when its window closes. */
	public double infectionSetInChance = 0.6;
	/** Subtracted from infectionSetInChance for each water rinse during the window. */
	public double infectionRinseReduction = 0.25;
	/** Ticks an infection spends at each stage before escalating. */
	public int infectionStageTicks = 6000;
	/** Stage 1 is Weakness, 2 adds Hunger, 3 adds stronger Weakness and Slowness and costs max health each stage. */
	public int infectionMaxStage = 3;
	/** Max health lost each time an infection at the max stage goes another stage interval untreated. */
	public double infectionMaxHealthLoss = 2.0;
	/** An infection cured at this stage or later leaves a scar. */
	public int infectionScarMinStage = 2;
	/** A single mob hit of at least this much damage leaves a scar. 0 disables. */
	public double scarSevereHitDamage = 10.0;
	/** Max health lost per scar. */
	public double scarMaxHealthPenalty = 1.0;
	/** Max health never drops below this, however much is lost. */
	public double minMaxHealth = 4.0;
	/** Max health lost per second while the food bar is empty. */
	public double starvationLossPerSecond = 0.01;
	/** Lost max health only recovers while the food level is at least this (20 is full). */
	public int maxHealthRecoveryMinFood = 18;
	public double maxHealthRecoveryPerSecond = 0.005;
	public double maxHealthSleepRecoveryPerSecond = 0.05;
	/** Max health restored by eating an item in the afflictions:max_health_restorers tag. */
	public double restorerMaxHealthRestore = 4.0;
	public boolean lostHeartsHudEnabled = true;

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
