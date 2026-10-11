package com.afflictions.infection;

import java.util.HashMap;
import java.util.Map;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

import com.afflictions.Afflictions;
import com.afflictions.config.AfflictionsConfig;
import com.afflictions.data.AfflictionData;
import com.afflictions.data.InfectionEntry;
import com.afflictions.data.TransientState;
import com.afflictions.health.MaxHealthManager;
import com.afflictions.phobia.PhobiaKeys;

/**
 * Phase 4: mob wounds open a risk window, untreated wounds become infections that escalate through
 * stages, and severe or late-cured infections leave scars. See {@link InfectionEntry} for the stage meaning.
 */
public final class InfectionManager {
	/** Drunk during the risk window to close the wound for good (honey bottle). */
	public static final TagKey<Item> ANTISEPTICS = TagKey.create(Registries.ITEM, Afflictions.id("antiseptics"));
	/** Drunk during the risk window to lower the chance the wound infects (water bottle). */
	public static final TagKey<Item> RINSES = TagKey.create(Registries.ITEM, Afflictions.id("rinses"));

	private static final int TICK_INTERVAL = 20;

	public static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (!blocked && entity instanceof ServerPlayer player) onHurt(player, source, damageTaken);
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % TICK_INTERVAL != 0) return;
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (player.isAlive() && !player.isSpectator()) tickPlayer(player);
			}
		});
	}

	private static void onHurt(ServerPlayer player, DamageSource source, float damage) {
		if (!(source.getEntity() instanceof Mob mob)) return;
		AfflictionsConfig config = AfflictionsConfig.get();
		String key = PhobiaKeys.ofEntityType(mob.getType());
		String name = pathOf(key);

		if (config.scarSevereHitDamage > 0 && damage >= config.scarSevereHitDamage) {
			AfflictionData.addScar(player, "wound_" + name);
		}

		if (damage < config.infectionMinDamage) return;
		if (AfflictionData.getTransient(player).infections().containsKey(key)) return;
		if (player.getRandom().nextDouble() >= config.infectionChance) return;

		long now = player.level().getGameTime();
		AfflictionData.setInfection(player, key, new InfectionEntry(key, now + config.infectionRiskWindowTicks, 0));
		player.sendSystemMessage(Component.literal("The wound from ").append(mob.getType().getDescription())
				.append(Component.literal(" is open. Treat it before it sets in.")));
	}

	private static void tickPlayer(ServerPlayer player) {
		TransientState state = AfflictionData.getTransient(player);
		if (state.infections().isEmpty()) return;

		AfflictionsConfig config = AfflictionsConfig.get();
		long now = player.level().getGameTime();
		boolean regenerating = player.hasEffect(MobEffects.REGENERATION);
		Map<String, InfectionEntry> next = new HashMap<>();
		float addedLoss = 0f;
		int worst = 0;

		for (InfectionEntry entry : state.infections().values()) {
			String key = entry.source();
			if (entry.stage() > 0 && regenerating) {
				cured(player, entry);
				continue;
			}
			if (now < entry.riskEndsTick()) {
				next.put(key, entry);
				worst = Math.max(worst, entry.stage());
				continue;
			}
			if (entry.stage() == 0) {
				double chance = config.infectionSetInChance - entry.rinses() * config.infectionRinseReduction;
				if (player.getRandom().nextDouble() < chance) {
					next.put(key, new InfectionEntry(key, now + config.infectionStageTicks, 1, entry.rinses()));
					worst = Math.max(worst, 1);
					player.sendSystemMessage(Component.literal("Your wound is infected."));
				} else {
					player.sendSystemMessage(Component.literal("Your wound heals cleanly."));
				}
			} else if (entry.stage() < config.infectionMaxStage) {
				InfectionEntry advanced = new InfectionEntry(key, now + config.infectionStageTicks, entry.stage() + 1, entry.rinses());
				next.put(key, advanced);
				worst = Math.max(worst, advanced.stage());
				player.sendSystemMessage(Component.literal("Your infection is getting worse."));
			} else {
				next.put(key, entry.withRiskEndsTick(now + config.infectionStageTicks));
				worst = Math.max(worst, entry.stage());
				addedLoss += config.infectionMaxHealthLoss;
			}
		}

		if (!next.equals(state.infections()) || addedLoss > 0f) {
			AfflictionData.setTransient(player, new TransientState(state.fear(), next, state.maxHealthLoss()));
			if (addedLoss > 0f) MaxHealthManager.addLoss(player, addedLoss);
		}
		applyDebuffs(player, worst);
	}

	/** Weakness from stage 1, Hunger from stage 2, a stronger Weakness and Slowness from stage 3. */
	private static void applyDebuffs(ServerPlayer player, int stage) {
		int duration = TICK_INTERVAL * 3;
		if (stage >= 1) player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration, stage >= 3 ? 1 : 0, false, true, true));
		if (stage >= 2) player.addEffect(new MobEffectInstance(MobEffects.HUNGER, duration, 0, false, true, true));
		if (stage >= 3) player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, duration, 0, false, true, true));
	}

	private static void cured(ServerPlayer player, InfectionEntry entry) {
		if (entry.stage() >= AfflictionsConfig.get().infectionScarMinStage) {
			AfflictionData.addScar(player, "infection_" + pathOf(entry.source()));
		}
		player.sendSystemMessage(Component.literal("Your infection clears up."));
	}

	/** Called when a player finishes drinking or eating an item. */
	public static void onItemConsumed(ServerPlayer player, ItemStack stack) {
		boolean antiseptic = stack.is(ANTISEPTICS);
		boolean rinse = !antiseptic && isRinse(stack);
		if (!antiseptic && !rinse) return;

		TransientState state = AfflictionData.getTransient(player);
		Map<String, InfectionEntry> next = new HashMap<>(state.infections());
		boolean changed = false;
		for (InfectionEntry entry : state.infections().values()) {
			if (entry.stage() != 0) continue;
			changed = true;
			if (antiseptic) next.remove(entry.source());
			else next.put(entry.source(), entry.withRinses(entry.rinses() + 1));
		}
		if (!changed) return;
		AfflictionData.setTransient(player, new TransientState(state.fear(), next, state.maxHealthLoss()));
		player.sendSystemMessage(Component.literal(antiseptic ? "The wound is cleaned and sealed." : "You rinse the wound."));
	}

	/** In the rinse tag, and if it is a potion bottle then only plain water counts. */
	private static boolean isRinse(ItemStack stack) {
		if (!stack.is(RINSES)) return false;
		PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
		return contents == null || contents.is(Potions.WATER);
	}

	/** Clears every infection and wound without leaving a scar. For cleric healing in a later phase. */
	public static void cureAll(ServerPlayer player) {
		TransientState state = AfflictionData.getTransient(player);
		AfflictionData.setTransient(player, new TransientState(state.fear(), Map.of(), state.maxHealthLoss()));
	}

	private static String pathOf(String key) {
		Identifier id = Identifier.tryParse(key);
		return id == null ? key : id.getPath();
	}

	private InfectionManager() {
	}
}
