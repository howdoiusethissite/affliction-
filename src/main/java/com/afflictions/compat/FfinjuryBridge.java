package com.afflictions.compat;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import com.fyxe.ffinjury.api.InjuryAPI;
import com.fyxe.ffinjury.api.event.InjuryApiEvents;
import com.fyxe.ffinjury.api.event.InjuryAppliedEvent;
import com.fyxe.ffinjury.api.event.InjuryTreatedEvent;
import com.fyxe.ffinjury.injury.InjuryType;

import com.afflictions.config.AfflictionsConfig;
import com.afflictions.data.Limb;
import com.afflictions.infection.InfectionManager;
import com.afflictions.limb.LimbManager;

/** The only class that touches the FFInjury API. Load it only through {@link FfinjuryCompat}. */
final class FfinjuryBridge {
	/** True while we grant an injury ourselves, so it does not open a wound that could loop back into an injury. */
	private static boolean granting;

	/**
	 * Whether the player already had a broken leg at FFInjury's max tier just before the current hit, taken in
	 * ALLOW_DAMAGE so that a hit which itself raises the injury to max tier does not count as a re-break.
	 */
	private static final Map<UUID, Boolean> LEG_AT_MAX_BEFORE_HIT = new ConcurrentHashMap<>();

	static void register() {
		InjuryApiEvents.register(InjuryAppliedEvent.class, FfinjuryBridge::onApplied);
		InjuryApiEvents.register(InjuryTreatedEvent.class, FfinjuryBridge::onTreated);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayer player) LEG_AT_MAX_BEFORE_HIT.put(player.getUUID(), legAtMaxTier(player));
			return true;
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (!(entity instanceof ServerPlayer player)) return;
			boolean wasAtMax = Boolean.TRUE.equals(LEG_AT_MAX_BEFORE_HIT.remove(player.getUUID()));
			if (wasAtMax && !blocked) onLegRehit(player, source, damageTaken);
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LEG_AT_MAX_BEFORE_HIT.remove(handler.getPlayer().getUUID()));
	}

	/** A leg injury from the config list sitting at FFInjury's max tier. */
	private static boolean legAtMaxTier(Player player) {
		AfflictionsConfig config = AfflictionsConfig.get();
		if (config.ffinjuryLimbBreakInjuries.isEmpty() || InjuryAPI.isGameplaySuppressed(player)) return false;
		int max = InjuryAPI.getDefaultMaxTier();
		for (String id : config.ffinjuryLimbBreakInjuries) {
			if (InjuryType.fromId(id).filter(type -> InjuryAPI.getTier(player, type) >= max).isPresent()) return true;
		}
		return false;
	}

	/** The "re-broken limb at max tier" amputation trigger: a configured hit on a leg that is already as broken as it gets. */
	private static void onLegRehit(ServerPlayer player, DamageSource source, float damage) {
		AfflictionsConfig config = AfflictionsConfig.get();
		if (damage < config.ffinjuryLimbBreakMinDamage || config.ffinjuryLimbBreakAmputationChance <= 0) return;
		boolean matches = source.typeHolder().unwrapKey()
				.map(key -> config.ffinjuryLimbBreakDamageTypes.contains(key.identifier().toString())).orElse(false);
		if (!matches) return;
		if (player.getRandom().nextDouble() < config.ffinjuryLimbBreakAmputationChance) {
			LimbManager.amputateRandom(player, Limb.LEFT_LEG, Limb.RIGHT_LEG);
		}
	}

	private static void onApplied(InjuryAppliedEvent event) {
		if (granting || !event.wasNew()) return;
		ServerPlayer player = event.getPlayer();
		if (InjuryAPI.isGameplaySuppressed(player)) return;
		InjuryType type = event.getInjuryType();
		if (!AfflictionsConfig.get().ffinjuryWoundInjuries.contains(type.getId())) return;
		InfectionManager.openWound(player, "ffinjury:" + type.getId(), Component.literal(type.getDisplayName()));
	}

	/** A bandage on the injury also seals any open wound. */
	private static void onTreated(InjuryTreatedEvent event) {
		InfectionManager.treatWounds(event.getPlayer(), true);
	}

	static int injuryCount(Player player) {
		return InjuryAPI.isGameplaySuppressed(player) ? 0 : InjuryAPI.getInjuryCount(player);
	}

	static void grantInfectionInjury(ServerPlayer player) {
		String id = AfflictionsConfig.get().ffinjuryInfectionInjury;
		if (id == null || id.isEmpty() || InjuryAPI.isGameplaySuppressed(player)) return;
		InjuryType.fromId(id).ifPresent(type -> {
			granting = true;
			try {
				InjuryAPI.addOrIncreaseInjury(player, type);
			} finally {
				granting = false;
			}
		});
	}

	private FfinjuryBridge() {
	}
}
