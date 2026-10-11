package com.afflictions.phobia;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;

import com.afflictions.config.AfflictionsConfig;
import com.afflictions.data.AfflictionData;
import com.afflictions.data.PhobiaEntry;

/** Phase 2: earns phobias on death, applies proximity effects, and cures them by exposure. */
public final class PhobiaManager {
	/** Per player, the server tick at which each phobia was last sighted. Used to space out Nausea bursts. */
	private static final Map<UUID, Map<String, Long>> LAST_SIGHTED = new HashMap<>();

	/** Per player, how many of their phobias had a trigger in range at the last proximity check. */
	private static final Map<UUID, Integer> ACTIVE_COUNT = new HashMap<>();

	/** Number of the player's phobias whose trigger is currently in range. Feeds fear drain. */
	public static int activePhobiaCount(ServerPlayer player) {
		return ACTIVE_COUNT.getOrDefault(player.getUUID(), 0);
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				onPlayerDeath(player, PhobiaKeys.ofDeath(source));
			} else if (source.getEntity() instanceof ServerPlayer killer) {
				onKill(killer, entity);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			int interval = Math.max(1, AfflictionsConfig.get().phobiaCheckIntervalTicks);
			if (server.getTickCount() % interval != 0) return;
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				tickPlayer(player, server.getTickCount());
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
				LAST_SIGHTED.remove(handler.getPlayer().getUUID()));
	}

	/**
	 * The phobia is added to the dying player's permanent state. copyOnDeath carries it to the new player on
	 * respawn, and the permanentConditionsSurviveDeath toggle clears it there if the config says so.
	 */
	private static void onPlayerDeath(ServerPlayer player, String key) {
		if (key == null || AfflictionData.hasPhobia(player, key)) return;
		AfflictionData.addPhobia(player, key);
	}

	private static void onKill(ServerPlayer killer, LivingEntity victim) {
		int needed = AfflictionsConfig.get().exposureKillsToCure;
		if (needed <= 0) return;
		String key = PhobiaKeys.ofEntityType(victim.getType());
		int kills = AfflictionData.addExposureKill(killer, key);
		if (kills < needed) return;
		AfflictionData.removePhobia(killer, key);
		killer.sendSystemMessage(Component.literal("You have faced your fear of ")
				.append(victim.getType().getDescription()).append(Component.literal(" and overcome it.")));
	}

	private static void tickPlayer(ServerPlayer player, int now) {
		if (!player.isAlive() || player.isSpectator()) {
			ACTIVE_COUNT.remove(player.getUUID());
			return;
		}
		Map<String, PhobiaEntry> phobias = AfflictionData.getPermanent(player).phobias();
		if (phobias.isEmpty()) {
			LAST_SIGHTED.remove(player.getUUID());
			ACTIVE_COUNT.remove(player.getUUID());
			return;
		}

		AfflictionsConfig config = AfflictionsConfig.get();
		Map<String, Long> sighted = LAST_SIGHTED.computeIfAbsent(player.getUUID(), id -> new HashMap<>());
		int nearCount = 0;
		boolean burst = false;
		for (String key : phobias.keySet()) {
			Presence presence = presence(player, key);
			if (!presence.near) continue;
			nearCount++;
			if (!presence.seen) continue;
			Long last = sighted.put(key, (long) now);
			if (last == null || now - last > config.phobiaNauseaCooldownTicks) burst = true;
		}

		if (nearCount > 0) ACTIVE_COUNT.put(player.getUUID(), nearCount);
		else ACTIVE_COUNT.remove(player.getUUID());

		if (burst && config.phobiaNauseaTicks > 0) {
			player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, config.phobiaNauseaTicks, 0, false, false, true));
		}
		if (nearCount > 0 && config.phobiaDarknessTicks > 0) {
			player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, config.phobiaDarknessTicks, 0, false, false, false));
		}
	}

	/** near: the trigger is inside its range. seen: the player can actually perceive it (line of sight for mobs). */
	private record Presence(boolean near, boolean seen) {
		static final Presence NONE = new Presence(false, false);
		static final Presence PRESENT = new Presence(true, true);
	}

	private static Presence presence(ServerPlayer player, String key) {
		return switch (key) {
			case PhobiaKeys.LAVA -> blockNearby(player, state -> state.getFluidState().is(FluidTags.LAVA)) ? Presence.PRESENT : Presence.NONE;
			case PhobiaKeys.FIRE -> player.isOnFire() || blockNearby(player, state -> state.is(BlockTags.FIRE)) ? Presence.PRESENT : Presence.NONE;
			case PhobiaKeys.DROWN -> player.isUnderWater() ? Presence.PRESENT : Presence.NONE;
			case PhobiaKeys.FALL -> dropBelow(player) ? Presence.PRESENT : Presence.NONE;
			default -> mobPresence(player, key);
		};
	}

	private static Presence mobPresence(ServerPlayer player, String key) {
		Identifier id = Identifier.tryParse(key);
		if (id == null) return Presence.NONE;
		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
		if (type == null) return Presence.NONE;

		double range = AfflictionsConfig.get().phobiaRange;
		List<? extends LivingEntity> mobs = player.level().getEntitiesOfClass(LivingEntity.class,
				player.getBoundingBox().inflate(range),
				e -> e.getType() == type && e.isAlive() && e.distanceToSqr(player) <= range * range);
		if (mobs.isEmpty()) return Presence.NONE;
		for (LivingEntity mob : mobs) {
			if (player.hasLineOfSight(mob)) return Presence.PRESENT;
		}
		return new Presence(true, false);
	}

	private static boolean blockNearby(ServerPlayer player, java.util.function.Predicate<BlockState> test) {
		int r = Math.max(1, AfflictionsConfig.get().phobiaEnvironmentRange);
		ServerLevel level = player.level();
		BlockPos origin = player.blockPosition();
		for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-r, -r, -r), origin.offset(r, r, r))) {
			if (test.test(level.getBlockState(pos))) return true;
		}
		return false;
	}

	/** True when nothing solid or liquid lies within the configured number of blocks under the player. */
	private static boolean dropBelow(ServerPlayer player) {
		int drop = AfflictionsConfig.get().phobiaFallDropBlocks;
		if (drop <= 0) return false;
		ServerLevel level = player.level();
		BlockPos.MutableBlockPos pos = player.blockPosition().mutable();
		for (int i = 0; i < drop; i++) {
			pos.move(0, -1, 0);
			if (pos.getY() < level.getMinY()) return false;
			BlockState state = level.getBlockState(pos);
			if (!state.getCollisionShape(level, pos).isEmpty() || !state.getFluidState().isEmpty()) return false;
		}
		return true;
	}

	private PhobiaManager() {
	}
}
