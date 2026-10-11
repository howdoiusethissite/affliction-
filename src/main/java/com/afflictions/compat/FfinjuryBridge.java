package com.afflictions.compat;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import com.fyxe.ffinjury.api.InjuryAPI;
import com.fyxe.ffinjury.api.event.InjuryApiEvents;
import com.fyxe.ffinjury.api.event.InjuryAppliedEvent;
import com.fyxe.ffinjury.api.event.InjuryTreatedEvent;
import com.fyxe.ffinjury.injury.InjuryType;

import com.afflictions.config.AfflictionsConfig;
import com.afflictions.infection.InfectionManager;

/** The only class that touches the FFInjury API. Load it only through {@link FfinjuryCompat}. */
final class FfinjuryBridge {
	/** True while we grant an injury ourselves, so it does not open a wound that could loop back into an injury. */
	private static boolean granting;

	static void register() {
		InjuryApiEvents.register(InjuryAppliedEvent.class, FfinjuryBridge::onApplied);
		InjuryApiEvents.register(InjuryTreatedEvent.class, FfinjuryBridge::onTreated);
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
