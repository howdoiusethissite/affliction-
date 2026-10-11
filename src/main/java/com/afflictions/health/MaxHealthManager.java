package com.afflictions.health;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import com.afflictions.Afflictions;
import com.afflictions.config.AfflictionsConfig;
import com.afflictions.data.AfflictionData;

/**
 * Phase 4: max health loss from starvation and infection, plus the flat penalty for each scar.
 * The loss lives in the transient state (it resets on death) and is applied as a max health attribute modifier.
 */
public final class MaxHealthManager {
	public static final Identifier MODIFIER_ID = Afflictions.id("max_health_loss");
	/** Eating these restores lost max health (golden apple, glistering melon slice). */
	public static final TagKey<Item> RESTORERS = TagKey.create(Registries.ITEM, Afflictions.id("max_health_restorers"));

	private static final int TICK_INTERVAL = 20;

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % TICK_INTERVAL != 0) return;
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (player.isAlive() && !player.isSpectator()) tickPlayer(player);
			}
		});
	}

	private static void tickPlayer(ServerPlayer player) {
		AfflictionsConfig config = AfflictionsConfig.get();
		float loss = AfflictionData.getMaxHealthLoss(player);
		float updated = loss;
		double seconds = TICK_INTERVAL / 20.0;
		int food = player.getFoodData().getFoodLevel();

		if (food <= 0 && !player.isCreative()) {
			updated += (float) (config.starvationLossPerSecond * seconds);
		} else if (loss > 0f && food >= config.maxHealthRecoveryMinFood) {
			double rate = player.isSleeping() ? config.maxHealthSleepRecoveryPerSecond : config.maxHealthRecoveryPerSecond;
			updated -= (float) (rate * seconds);
		}
		updated = Math.max(0f, updated);
		// Only write on change: every setAttached on a synced attachment sends a packet.
		if (Math.abs(updated - loss) > 0.0001f) AfflictionData.setMaxHealthLoss(player, updated);

		applyModifier(player);
	}

	/** Total health points taken off the maximum: tracked loss plus scars, never below the configured floor. */
	public static double totalLoss(Player player) {
		AfflictionsConfig config = AfflictionsConfig.get();
		double loss = AfflictionData.getMaxHealthLoss(player)
				+ AfflictionData.getPermanent(player).scars().size() * config.scarMaxHealthPenalty;
		AttributeInstance max = player.getAttribute(Attributes.MAX_HEALTH);
		double base = max == null ? 20.0 : max.getBaseValue();
		return Math.max(0.0, Math.min(loss, base - config.minMaxHealth));
	}

	private static void applyModifier(ServerPlayer player) {
		AttributeInstance max = player.getAttribute(Attributes.MAX_HEALTH);
		if (max == null) return;
		double loss = totalLoss(player);
		AttributeModifier existing = max.getModifier(MODIFIER_ID);
		if (loss <= 0.0) {
			if (existing != null) max.removeModifier(MODIFIER_ID);
		} else if (existing == null || Math.abs(existing.amount() + loss) > 0.0001) {
			max.addOrUpdateTransientModifier(new AttributeModifier(MODIFIER_ID, -loss, AttributeModifier.Operation.ADD_VALUE));
		}
		if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
	}

	public static void addLoss(Player player, float amount) {
		AfflictionData.setMaxHealthLoss(player, AfflictionData.getMaxHealthLoss(player) + amount);
	}

	/** Called when a player finishes eating or drinking an item. */
	public static void onItemConsumed(ServerPlayer player, ItemStack stack) {
		if (!stack.is(RESTORERS)) return;
		float loss = AfflictionData.getMaxHealthLoss(player);
		if (loss <= 0f) return;
		AfflictionData.setMaxHealthLoss(player, Math.max(0f, loss - (float) AfflictionsConfig.get().restorerMaxHealthRestore));
	}

	private MaxHealthManager() {
	}
}
