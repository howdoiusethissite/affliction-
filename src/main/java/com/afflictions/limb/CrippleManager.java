package com.afflictions.limb;

import java.util.HashMap;
import java.util.Map;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;

import com.afflictions.config.AfflictionsConfig;
import com.afflictions.data.AfflictionData;
import com.afflictions.data.Limb;
import com.afflictions.data.LimbState;
import com.afflictions.data.TransientState;

/**
 * A single huge hit that leaves the player alive cripples a limb for a while. The penalty itself is applied by
 * {@link LimbManager}; this class decides when a limb becomes crippled and when it recovers. State is transient,
 * so dying clears it.
 */
public final class CrippleManager {
	private static final int TICK_INTERVAL = 20;

	public static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (!blocked && entity instanceof ServerPlayer player) onHurt(player, source, damageTaken);
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % TICK_INTERVAL != 0) return;
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (player.isAlive()) expire(player);
			}
		});
	}

	private static void onHurt(ServerPlayer player, DamageSource source, float damage) {
		AfflictionsConfig config = AfflictionsConfig.get();
		if (config.crippleDamageThreshold <= 0 || damage < config.crippleDamageThreshold) return;
		if (!player.isAlive() || player.isCreative() || player.isSpectator()) return;
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
		if (player.getRandom().nextDouble() >= config.crippleChance) return;

		Limb limb = source.is(DamageTypeTags.IS_FALL)
				? (player.getRandom().nextBoolean() ? Limb.LEFT_LEG : Limb.RIGHT_LEG)
				: LimbManager.randomLimb(player);
		cripple(player, limb);
	}

	/** Cripples an intact limb, or extends the crippling it already has. Does nothing for a limb that is already gone. */
	public static boolean cripple(ServerPlayer player, Limb limb) {
		if (AfflictionData.getPermanent(player).limb(limb) != LimbState.INTACT) return false;
		TransientState state = AfflictionData.getTransient(player);
		Map<Limb, Long> next = new HashMap<>(state.crippled());
		boolean fresh = !next.containsKey(limb);
		next.put(limb, player.level().getGameTime() + AfflictionsConfig.get().crippleDurationTicks);
		AfflictionData.setTransient(player, state.withCrippled(next));
		if (fresh) player.sendSystemMessage(Component.translatable("afflictions.message.crippled", LimbManager.limbName(limb)));
		return true;
	}

	private static void expire(ServerPlayer player) {
		TransientState state = AfflictionData.getTransient(player);
		if (state.crippled().isEmpty()) return;
		long now = player.level().getGameTime();
		Map<Limb, Long> next = new HashMap<>();
		for (Map.Entry<Limb, Long> entry : state.crippled().entrySet()) {
			if (now < entry.getValue()) next.put(entry.getKey(), entry.getValue());
			else player.sendSystemMessage(Component.translatable("afflictions.message.cripple_healed", LimbManager.limbName(entry.getKey())));
		}
		if (next.size() != state.crippled().size()) AfflictionData.setTransient(player, state.withCrippled(next));
	}

	private CrippleManager() {
	}
}
