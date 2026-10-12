package com.afflictions.fear;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;

import com.afflictions.Afflictions;
import com.afflictions.config.AfflictionsConfig;
import com.afflictions.data.AfflictionData;
import com.afflictions.data.TransientState;
import com.afflictions.phobia.PhobiaKeys;

/**
 * What the fear tiers do on the server: slower regeneration, faster hunger, farther mob detection, dearer villagers,
 * weaker sleep, panic, fumbling, reduced experience and the breakdown at 0. The client half (heartbeat, vignette,
 * sway, HUD glitches) lives in the client source set and reads the same tiers from the synced fear value.
 */
public final class FearEffects {
	private static final Identifier FOLLOW_RANGE_ID = Afflictions.id("fear_detection");
	private static final int SECOND = 20;

	private static final Map<UUID, Float> LAST_HEALTH = new HashMap<>();
	/** Game tick at which the speed burst of a panic ends and the crash starts. */
	private static final Map<UUID, Long> PANIC_CRASH_AT = new HashMap<>();
	private static final Map<UUID, Long> PANIC_COOLDOWN_UNTIL = new HashMap<>();
	/** Fear at the moment the current sleep began. */
	private static final Map<UUID, Float> SLEEP_START_FEAR = new HashMap<>();
	/** Monsters wearing the detection modifier, with the server tick they last got it. */
	private static final Map<Mob, Long> DETECTING = new WeakHashMap<>();

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(FearEffects::tick);

		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (!blocked && entity instanceof ServerPlayer player) onHurt(player, source, damageTaken);
		});

		// Runs before the trading screen opens. Special price changes are cleared by vanilla when trading stops,
		// so they are reset here first in case an earlier click never opened the screen.
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (hand == InteractionHand.MAIN_HAND && entity instanceof Villager villager
					&& player instanceof ServerPlayer serverPlayer && !serverPlayer.isSpectator()) {
				applyVillagerMarkup(serverPlayer, villager);
			}
			return InteractionResult.PASS;
		});

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> clearPlayer(handler.getPlayer().getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			LAST_HEALTH.clear();
			PANIC_CRASH_AT.clear();
			PANIC_COOLDOWN_UNTIL.clear();
			SLEEP_START_FEAR.clear();
			DETECTING.clear();
		});
	}

	private static void clearPlayer(UUID id) {
		LAST_HEALTH.remove(id);
		PANIC_CRASH_AT.remove(id);
		PANIC_COOLDOWN_UNTIL.remove(id);
		SLEEP_START_FEAR.remove(id);
	}

	private static boolean affected(ServerPlayer player) {
		return player.isAlive() && !player.isCreative() && !player.isSpectator();
	}

	private static void tick(MinecraftServer server) {
		boolean second = server.getTickCount() % SECOND == 0;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!affected(player)) {
				LAST_HEALTH.remove(player.getUUID());
				continue;
			}
			FearTier tier = FearTier.of(AfflictionData.getFear(player));
			tickRegen(player, tier);
			if (second && tier.atLeast(FearTier.SHAKEN)) tickHunger(player, tier);
		}
		tickPanicCrash(server);
		if (second) tickDetection(server);
	}

	// ---- Uneasy: natural regeneration slows --------------------------------------------------------------------

	/**
	 * Natural regeneration heals in steps of at most one health point, so a small rise in health that no
	 * Regeneration effect explains is treated as one, and each is undone with the tier's chance. This avoids
	 * hooking the food code, at the cost of the exhaustion that heal used being spent anyway.
	 */
	private static void tickRegen(ServerPlayer player, FearTier tier) {
		float health = player.getHealth();
		Float last = LAST_HEALTH.put(player.getUUID(), health);
		if (last == null) return;
		float gain = health - last;
		if (gain <= 0f || gain > 1.0001f) return;
		double chance = regenBlockChance(tier);
		if (chance <= 0 || player.hasEffect(MobEffects.REGENERATION)) return;
		if (player.getRandom().nextDouble() < chance) {
			player.setHealth(last);
			LAST_HEALTH.put(player.getUUID(), last);
		}
	}

	private static double regenBlockChance(FearTier tier) {
		AfflictionsConfig config = AfflictionsConfig.get();
		return switch (tier) {
			case CALM -> 0;
			case UNEASY -> config.fearRegenBlockChanceUneasy;
			case SHAKEN -> config.fearRegenBlockChanceShaken;
			case BREAKING -> config.fearRegenBlockChanceBreaking;
		};
	}

	// ---- Shaken: hunger, mob detection, villagers, sleep -------------------------------------------------------

	private static void tickHunger(ServerPlayer player, FearTier tier) {
		AfflictionsConfig config = AfflictionsConfig.get();
		double perSecond = tier == FearTier.BREAKING ? config.fearHungerExhaustionPerSecondBreaking
				: config.fearHungerExhaustionPerSecondShaken;
		if (perSecond > 0) player.causeFoodExhaustion((float) perSecond);
	}

	/**
	 * Monsters pick targets within their follow range attribute, so near a shaken player it is raised by a transient
	 * modifier. Monsters that were not refreshed on this pass get it removed again.
	 */
	private static void tickDetection(MinecraftServer server) {
		long now = server.getTickCount();
		AfflictionsConfig config = AfflictionsConfig.get();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!affected(player)) continue;
			FearTier tier = FearTier.of(AfflictionData.getFear(player));
			if (!tier.atLeast(FearTier.SHAKEN)) continue;
			double extra = tier == FearTier.BREAKING ? config.fearMobDetectionExtraRangeBreaking
					: config.fearMobDetectionExtraRangeShaken;
			if (extra <= 0) continue;
			double reach = 40.0 + extra;
			ServerLevel level = player.level();
			List<Mob> near = level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(reach),
					m -> m.isAlive() && m.getType().getCategory() == MobCategory.MONSTER && m.distanceToSqr(player) <= reach * reach);
			for (Mob mob : near) {
				AttributeInstance follow = mob.getAttribute(Attributes.FOLLOW_RANGE);
				if (follow == null) continue;
				AttributeModifier existing = follow.getModifier(FOLLOW_RANGE_ID);
				boolean refreshedThisPass = DETECTING.getOrDefault(mob, -1L) == now;
				if (existing == null || !refreshedThisPass || existing.amount() < extra) {
					follow.addOrUpdateTransientModifier(new AttributeModifier(FOLLOW_RANGE_ID, extra, AttributeModifier.Operation.ADD_VALUE));
				}
				DETECTING.put(mob, now);
			}
		}
		Iterator<Map.Entry<Mob, Long>> it = DETECTING.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Mob, Long> entry = it.next();
			Mob mob = entry.getKey();
			if (mob == null || mob.isRemoved()) {
				it.remove();
			} else if (entry.getValue() < now) {
				AttributeInstance follow = mob.getAttribute(Attributes.FOLLOW_RANGE);
				if (follow != null) follow.removeModifier(FOLLOW_RANGE_ID);
				it.remove();
			}
		}
	}

	private static void applyVillagerMarkup(ServerPlayer player, Villager villager) {
		if (villager.getTradingPlayer() != null) return;
		AfflictionsConfig config = AfflictionsConfig.get();
		FearTier tier = FearTier.of(AfflictionData.getFear(player));
		int increase = switch (tier) {
			case SHAKEN -> config.fearVillagerPriceIncreaseShaken;
			case BREAKING -> config.fearVillagerPriceIncreaseBreaking;
			default -> 0;
		};
		for (MerchantOffer offer : villager.getOffers()) {
			offer.resetSpecialPriceDiff();
			if (increase > 0) offer.addToSpecialPriceDiff(increase);
		}
	}

	/** Multiplier on sleep recovery for the tier the player is in. Used by FearManager. */
	public static double sleepMultiplier(FearTier tier) {
		AfflictionsConfig config = AfflictionsConfig.get();
		return switch (tier) {
			case SHAKEN -> config.fearSleepRecoveryMultiplierShaken;
			case BREAKING -> config.fearSleepRecoveryMultiplierBreaking;
			default -> 1.0;
		};
	}

	/**
	 * The highest fear this sleep may reach. A sleep that began at Shaken or lower is capped at its starting fear plus
	 * fearSleepMaxRestorePerNight, so one night never resets a badly shaken player. Returns the full meter otherwise.
	 */
	public static float sleepCeiling(ServerPlayer player, float fear) {
		UUID id = player.getUUID();
		if (!player.isSleeping()) {
			SLEEP_START_FEAR.remove(id);
			return TransientState.MAX_FEAR;
		}
		float start = SLEEP_START_FEAR.computeIfAbsent(id, k -> fear);
		if (!FearTier.of(start).atLeast(FearTier.SHAKEN)) return TransientState.MAX_FEAR;
		return start + (float) AfflictionsConfig.get().fearSleepMaxRestorePerNight;
	}

	// ---- Breaking: panic, fumble, experience -------------------------------------------------------------------

	private static void onHurt(ServerPlayer player, DamageSource source, float damage) {
		if (!affected(player) || source.getEntity() == null) return;
		if (FearTier.of(AfflictionData.getFear(player)) != FearTier.BREAKING) return;
		AfflictionsConfig config = AfflictionsConfig.get();
		long now = player.level().getGameTime();

		if (damage >= config.fearPanicMinDamage && now >= PANIC_COOLDOWN_UNTIL.getOrDefault(player.getUUID(), 0L)
				&& player.getRandom().nextDouble() < config.fearPanicChance) {
			player.addEffect(new MobEffectInstance(MobEffects.SPEED, config.fearPanicSpeedTicks, config.fearPanicSpeedAmplifier, false, true, true));
			PANIC_CRASH_AT.put(player.getUUID(), now + config.fearPanicSpeedTicks);
			PANIC_COOLDOWN_UNTIL.put(player.getUUID(), now + config.fearPanicSpeedTicks + config.fearPanicCrashTicks + config.fearPanicCooldownTicks);
			player.sendSystemMessage(Component.translatable("afflictions.message.panic"));
		}

		if (damage >= config.fearFumbleMinDamage && player.getRandom().nextDouble() < config.fearFumbleChance) {
			fumble(player);
		}
	}

	private static void fumble(ServerPlayer player) {
		ItemStack held = player.getMainHandItem();
		if (held.isEmpty()) return;
		ItemStack dropped = held.copy();
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		ItemEntity entity = new ItemEntity(player.level(), player.getX(), player.getEyeY() - 0.3, player.getZ(), dropped);
		entity.setDeltaMovement((player.getRandom().nextDouble() - 0.5) * 0.2, 0.2, (player.getRandom().nextDouble() - 0.5) * 0.2);
		entity.setPickUpDelay(40);
		player.level().addFreshEntity(entity);
		player.sendSystemMessage(Component.translatable("afflictions.message.fumble"));
	}

	private static void tickPanicCrash(MinecraftServer server) {
		if (PANIC_CRASH_AT.isEmpty()) return;
		AfflictionsConfig config = AfflictionsConfig.get();
		Iterator<Map.Entry<UUID, Long>> it = PANIC_CRASH_AT.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Long> entry = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null) {
				it.remove();
			} else if (player.level().getGameTime() >= entry.getValue()) {
				it.remove();
				if (!player.isAlive()) continue;
				player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, config.fearPanicCrashTicks, 0, false, true, true));
				player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, config.fearPanicCrashTicks, 0, false, true, true));
				player.sendSystemMessage(Component.translatable("afflictions.message.panic_crash"));
			}
		}
	}

	/** Called from the Player mixin on every experience gain. Rounds randomly so small orbs are not rounded away. */
	public static int scaleXp(ServerPlayer player, int amount) {
		if (amount <= 0 || !affected(player)) return amount;
		if (FearTier.of(AfflictionData.getFear(player)) != FearTier.BREAKING) return amount;
		double scaled = amount * Math.max(0.0, AfflictionsConfig.get().fearXpMultiplier);
		int whole = (int) Math.floor(scaled);
		return whole + (player.getRandom().nextDouble() < scaled - whole ? 1 : 0);
	}

	// ---- Breakdown at 0 ----------------------------------------------------------------------------------------

	/** The one-time event when fear hits 0: fear bumps back up, then either a breakdown or, rarely, a steeled moment. */
	public static void breakdown(ServerPlayer player) {
		AfflictionsConfig config = AfflictionsConfig.get();
		AfflictionData.setFear(player, (float) Math.max(1.0, config.fearBreakdownRestoreTo));
		ServerLevel level = player.level();

		if (player.getRandom().nextDouble() < config.fearSteeledChance) {
			player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, config.fearSteeledTicks, config.fearSteeledStrengthAmplifier, false, true, true));
			player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, config.fearSteeledTicks, config.fearSteeledResistanceAmplifier, false, true, true));
			player.sendSystemMessage(Component.translatable("afflictions.message.steeled"));
			level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 1.4f);
			return;
		}

		int ticks = config.fearBreakdownDebuffTicks;
		player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 0, false, true, true));
		player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, 0, false, true, true));
		player.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, ticks, 0, false, true, true));
		player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 200, 0, false, false, true));
		player.sendSystemMessage(Component.translatable("afflictions.message.breakdown"));
		level.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_SCREAM, SoundSource.PLAYERS, 0.8f, 0.6f);

		if (player.getRandom().nextDouble() < config.fearBreakdownPhobiaChance) {
			String key = pickBreakdownPhobia(player, level);
			if (!AfflictionData.hasPhobia(player, key)) {
				AfflictionData.addPhobia(player, key);
				player.sendSystemMessage(Component.translatable("afflictions.message.breakdown_phobia", phobiaName(key)));
			}
		}
	}

	/** The nearest monster within 24 blocks, otherwise a random environmental fear. */
	private static String pickBreakdownPhobia(ServerPlayer player, ServerLevel level) {
		Mob nearest = null;
		double best = 24.0 * 24.0;
		for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(24.0),
				m -> m.isAlive() && m.getType().getCategory() == MobCategory.MONSTER)) {
			double d = mob.distanceToSqr(player);
			if (d < best) {
				best = d;
				nearest = mob;
			}
		}
		if (nearest != null) return PhobiaKeys.ofEntityType(nearest.getType());
		List<String> pool = PhobiaKeys.ENVIRONMENTAL;
		return pool.get(player.getRandom().nextInt(pool.size()));
	}

	private static Component phobiaName(String key) {
		if (PhobiaKeys.isEnvironmental(key)) return Component.translatable("afflictions.phobia." + key);
		Identifier id = Identifier.tryParse(key);
		if (id != null) {
			var type = BuiltInRegistries.ENTITY_TYPE.getOptional(id);
			if (type.isPresent()) return ((EntityType<?>) type.get()).getDescription();
		}
		return Component.literal(key);
	}

	private FearEffects() {
	}
}
