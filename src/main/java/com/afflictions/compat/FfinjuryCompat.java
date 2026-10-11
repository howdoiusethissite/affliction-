package com.afflictions.compat;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import com.afflictions.Afflictions;

/**
 * Optional FFInjury integration. This class never names an FFInjury type, so it is safe to load without the mod;
 * everything that does lives in {@link FfinjuryBridge}, which is only touched after the loaded check.
 */
public final class FfinjuryCompat {
	public static final String MOD_ID = "ffinjury";
	private static final boolean LOADED = FabricLoader.getInstance().isModLoaded(MOD_ID);

	public static boolean isLoaded() {
		return LOADED;
	}

	public static void register() {
		if (!LOADED) return;
		FfinjuryBridge.register();
		Afflictions.LOGGER.info("FFInjury found, integration enabled");
	}

	/** Injuries the player has, or 0 when FFInjury is absent or suppresses itself (creative). */
	public static int injuryCount(Player player) {
		return LOADED ? FfinjuryBridge.injuryCount(player) : 0;
	}

	/** Gives the player the configured FFInjury injury when an infection reaches its worst stage. */
	public static void grantInfectionInjury(ServerPlayer player) {
		if (LOADED) FfinjuryBridge.grantInfectionInjury(player);
	}

	private FfinjuryCompat() {
	}
}
