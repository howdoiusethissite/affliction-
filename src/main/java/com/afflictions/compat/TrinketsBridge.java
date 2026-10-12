package com.afflictions.compat;

import java.util.function.Predicate;

import eu.pb4.trinkets.api.TrinketAttachment;
import eu.pb4.trinkets.api.TrinketsApi;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** The only class that touches the Trinkets API. Only load it after {@link TrinketsCompat#isLoaded()}. */
final class TrinketsBridge {
	static boolean anyEquipped(Player player, Predicate<ItemStack> matcher) {
		TrinketAttachment attachment = TrinketsApi.getAttachment(player);
		if (attachment == null) return false;
		return attachment.isEquipped(matcher);
	}

	private TrinketsBridge() {
	}
}
