package com.afflictions.compat;

import java.util.function.Predicate;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Optional Trinkets integration. Like {@link FfinjuryCompat} this class never names a Trinkets type, so it loads without
 * the mod; everything that does lives in {@link TrinketsBridge}. Trinkets Updated declares {@code provides: trinkets}.
 */
public final class TrinketsCompat {
	public static final String MOD_ID = "trinkets";
	private static final boolean LOADED = FabricLoader.getInstance().isModLoaded(MOD_ID);

	public static boolean isLoaded() {
		return LOADED;
	}

	/** True when any item the player has equipped in a Trinkets slot matches. False without Trinkets. */
	public static boolean anyEquipped(Player player, Predicate<ItemStack> matcher) {
		return LOADED && TrinketsBridge.anyEquipped(player, matcher);
	}

	private TrinketsCompat() {
	}
}
