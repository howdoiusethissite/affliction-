package com.afflictions.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
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

	@Section("General")
	public boolean permanentConditionsSurviveDeath = true;

	// Phase 2: phobias
	/** Blocks within which a phobic player reacts to a feared mob. */
	@Section("Phobias")
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
	@Section("Fear")
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
	/** Holding one of these in the offhand (see fearLightSourceMainHand) cancels the darkness and cave drain. */
	public java.util.List<String> fearLightSourceItems = java.util.List.of(
			"minecraft:torch", "minecraft:soul_torch", "minecraft:copper_torch", "minecraft:redstone_torch",
			"minecraft:lantern", "minecraft:soul_lantern", "minecraft:copper_lantern");
	/** Share of the darkness and cave drain a light source removes (1.0 removes all of it, 0 disables the feature). */
	public double fearLightSourceDrainReduction = 1.0;
	/** Extra fear per second restored while a light source is held and it would otherwise be dark or deep underground. */
	public double fearLightSourceRecoveryPerSecond = 0.25;
	/** Also count a light source in the main hand. */
	public boolean fearLightSourceMainHand = false;
	/** Also count a light source in any Trinkets slot (torches and lanterns can be worn in the belt slot). Ignored without Trinkets. */
	public boolean fearLightSourceTrinkets = true;
	public boolean fearHudEnabled = true;
	/** When false the bar only appears while fear is below 100. */
	public boolean fearHudAlwaysVisible = true;

	// Fear effects: what a low fear value does to the player. Fear 100 is calm, 0 is a breakdown.
	/** Master switch for every effect in this group (the tiers, the breakdown and the client atmosphere). */
	@Section("Fear effects")
	public boolean fearEffectsEnabled = true;
	/** Fear below this is Uneasy. */
	public double fearUneasyBelow = 70.0;
	/** Fear below this is Shaken. */
	public double fearShakenBelow = 50.0;
	/** Fear below this is Breaking. At 0 a breakdown happens. */
	public double fearBreakingBelow = 25.0;

	// Uneasy
	/** Chance that a natural regeneration tick of health is undone, per tier. */
	public double fearRegenBlockChanceUneasy = 0.3;
	public double fearRegenBlockChanceShaken = 0.5;
	public double fearRegenBlockChanceBreaking = 0.65;
	public boolean fearHeartbeatEnabled = true;
	public double fearHeartbeatVolume = 0.7;
	/** Beats per minute at the top of the Uneasy tier and at fear 0. */
	public double fearHeartbeatSlowBpm = 60.0;
	public double fearHeartbeatFastBpm = 150.0;
	public boolean fearVignetteEnabled = true;
	/** Opacity (0 to 1) of the darkened screen edges at fear 0. */
	public double fearVignetteMaxAlpha = 0.6;
	/** Opacity (0 to 1) of the gray wash that dulls colors at fear 0. */
	public double fearDesaturationMaxAlpha = 0.25;

	// Shaken
	/** Degrees of camera sway at the Shaken tier (1.6 times as much when Breaking). 0 disables. */
	public double fearSwayDegrees = 0.35;
	/** Extra degrees of random aim jitter while drawing a bow or crossbow. 0 disables. */
	public double fearAimJitterDegrees = 1.0;
	/** Extra food exhaustion per second (4.0 exhaustion costs one hunger point). */
	public double fearHungerExhaustionPerSecondShaken = 0.02;
	public double fearHungerExhaustionPerSecondBreaking = 0.04;
	/** Blocks added to the follow range of monsters near the player. */
	public double fearMobDetectionExtraRangeShaken = 8.0;
	public double fearMobDetectionExtraRangeBreaking = 16.0;
	/** Emeralds (or whatever the first cost is) added to every villager trade price. */
	public int fearVillagerPriceIncreaseShaken = 1;
	public int fearVillagerPriceIncreaseBreaking = 2;
	/** Sleep recovery is multiplied by this per tier. */
	public double fearSleepRecoveryMultiplierShaken = 0.5;
	public double fearSleepRecoveryMultiplierBreaking = 0.25;
	/** A sleep that starts at Shaken or lower cannot restore more than this much fear. */
	public double fearSleepMaxRestorePerNight = 30.0;

	// Breaking
	public boolean fearHudGlitchEnabled = true;
	/** Chance that a hit from a mob or player triggers panic. */
	public double fearPanicChance = 0.5;
	public double fearPanicMinDamage = 1.0;
	public int fearPanicSpeedTicks = 60;
	public int fearPanicSpeedAmplifier = 1;
	/** Slowness and Weakness duration once the speed burst ends. */
	public int fearPanicCrashTicks = 200;
	public int fearPanicCooldownTicks = 400;
	/** Chance to drop the held item when a hit does at least fearFumbleMinDamage. */
	public double fearFumbleChance = 0.3;
	public double fearFumbleMinDamage = 6.0;
	/** Experience gained is multiplied by this while Breaking. */
	public double fearXpMultiplier = 0.5;

	// Breakdown at fear 0
	/** Fear after a breakdown or a steeled moment. */
	public double fearBreakdownRestoreTo = 30.0;
	/** Chance that hitting 0 is a steeled moment instead of a breakdown. */
	public double fearSteeledChance = 0.1;
	public int fearBreakdownDebuffTicks = 3600;
	/** Chance a breakdown leaves a new phobia (of the nearest monster, otherwise an environmental one). */
	public double fearBreakdownPhobiaChance = 0.35;
	public int fearSteeledTicks = 1800;
	public int fearSteeledStrengthAmplifier = 1;
	public int fearSteeledResistanceAmplifier = 1;

	// Phase 4: infection, scars, max health loss. Health values are health points (2 per heart).
	/** Chance that a mob hit of at least infectionMinDamage opens a wound with a risk window. */
	@Section("Infection and scars")
	public double infectionChance = 0.25;
	public double infectionMinDamage = 2.0;
	public int infectionRiskWindowTicks = 3600;
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
	@Section("Max health")
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

	// Phase 5: cleric healing. Sneak + right-click a cleric holding the payment item. Levels are villager levels:
	// 1 novice, 2 apprentice, 3 journeyman, 4 expert, 5 master.
	@Section("Cleric healing")
	public boolean clericHealingEnabled = true;
	/** Item id the cleric accepts, taken from the stack in the player's main hand. */
	public String clericPaymentItem = "minecraft:emerald";
	/** Clears every wound and infection without leaving a scar. */
	public int clericInfectionMinLevel = 2;
	public int clericInfectionCost = 4;
	/** Cures the phobia closest to being cured by exposure. */
	public int clericPhobiaMinLevel = 1;
	public int clericPhobiaCost = 8;
	/** Removes the oldest scar. */
	public int clericScarMinLevel = 5;
	public int clericScarCost = 24;

	// Phase 6: amputation and implants. A penalty of 1.0 is a missing limb; 0 is a healthy one; negative is better than natural.
	/** Chance, each time an infection at the max stage goes another stage interval untreated, that it takes a limb. 0 disables. */
	@Section("Amputation and implants")
	public double infectionAmputationChance = 0.25;
	/** Penalty left by each implant tier. Redstone only counts as charged while it has charge, otherwise it acts as a dead limb. */
	public double implantPenaltyWood = 0.5;
	public double implantPenaltyIron = 0.25;
	public double implantPenaltyRedstone = -0.1;
	public double implantPenaltyNetherite = -0.15;
	/** Movement speed lost per leg at penalty 1.0 (a fraction of base speed). */
	public double legSpeedPenalty = 0.3;
	/** Mining speed lost per arm at penalty 1.0. */
	public double armMiningPenalty = 0.35;
	/** Attack speed lost per arm at penalty 1.0. */
	public double armAttackSpeedPenalty = 0.25;
	/** Redstone implant charge is in ticks of use. It drains continuously while the implant is installed. */
	public int redstoneMaxCharge = 36000;
	public int redstoneDustCharge = 6000;
	/** Charge a freshly installed redstone implant starts with. */
	public int redstoneInstallCharge = 6000;

	// Crippling: one huge hit that does not kill leaves a limb weak for a while. Damage values are health points (2 per heart).
	/** A single hit of at least this much damage that leaves you alive can cripple a limb. 0 disables. */
	@Section("Crippling and wounds")
	public double crippleDamageThreshold = 12.0;
	public double crippleChance = 0.6;
	/** Falls cripple a leg, everything else a random limb. Ticks until the limb recovers by itself (20 per second). */
	public int crippleDurationTicks = 12000;
	/** Penalty on a crippled limb, same scale as implants (1.0 acts like a missing limb, 0.5 is a wooden one). */
	public double crippledLimbPenalty = 0.6;
	/** Shows a list of open wounds, infections and crippled limbs in the top-left corner (client side). */
	public boolean woundHudEnabled = true;

	// Voluntary amputation: /amputate <limb>, to swap a healthy limb for an implant on purpose.
	@Section("Voluntary amputation")
	public boolean voluntaryAmputationEnabled = true;
	public boolean voluntaryAmputationNeedsSmithingTable = true;
	/** Blocks within which a smithing table must be. */
	public int voluntaryAmputationTableRange = 4;

	// FFInjury integration. Everything here is ignored when FFInjury is not installed.
	/** FFInjury injury ids that open an infection wound when newly applied. */
	@Section("FFInjury integration")
	public java.util.List<String> ffinjuryWoundInjuries = java.util.List.of("bleeding", "burn");
	/** Fear drain per second for each injury the player has. */
	public double fearInjuryDrainPerSecond = 0.25;
	/** FFInjury injury id granted when an infection first reaches its max stage. Empty disables. */
	public String ffinjuryInfectionInjury = "bleeding";
	/** When true and FFInjury is installed, mob hits no longer open wounds by themselves. */
	public boolean ffinjuryReplacesMobWounds = false;

	// Phase 8: the "re-broken limb at max tier" amputation. A leg injury already at FFInjury's max tier that is
	// hit again by one of these damage types for at least the minimum damage can take a leg.
	/** FFInjury injury ids that count as a broken leg for the re-break trigger. Empty disables the trigger. */
	public java.util.List<String> ffinjuryLimbBreakInjuries = java.util.List.of("broken_leg", "ankle_fracture");
	public java.util.List<String> ffinjuryLimbBreakDamageTypes = java.util.List.of("minecraft:fall");
	public double ffinjuryLimbBreakMinDamage = 4.0;
	public double ffinjuryLimbBreakAmputationChance = 0.35;

	// Phase 8: tooltips on vanilla treatment items (client side).
	@Section("Interface")
	public boolean treatmentTooltipsEnabled = true;

	/** Marks the first option of a group so the config screen can print a heading above it. */
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.FIELD)
	public @interface Section {
		String value();
	}

	public static AfflictionsConfig get() {
		return instance;
	}

	/** Throws away unsaved changes by reading the file again. */
	public static void reload() {
		load();
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
		save();
	}

	public static void save() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve(Afflictions.MOD_ID + ".json");
		try (Writer writer = Files.newBufferedWriter(path)) {
			GSON.toJson(instance, writer);
		} catch (IOException e) {
			Afflictions.LOGGER.error("Could not write {}", path, e);
		}
	}

	/** A fresh copy with every option at its default, for showing defaults in the config screen. */
	public static AfflictionsConfig defaults() {
		return new AfflictionsConfig();
	}

	private AfflictionsConfig() {
	}
}
