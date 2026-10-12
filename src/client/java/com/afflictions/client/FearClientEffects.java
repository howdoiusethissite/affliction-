package com.afflictions.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Items;

import com.afflictions.config.AfflictionsConfig;
import com.afflictions.data.AfflictionData;
import com.afflictions.fear.FearTier;

/** Client half of the fear tiers: the heartbeat, camera sway and the aim jitter of drawn bows and crossbows. */
public final class FearClientEffects {
	private static final int PULSE_TICKS = 8;

	private static int heartbeatCountdown;
	private static int ticksSinceBeat = 1000;
	private static double appliedYaw;
	private static double appliedPitch;

	/** 1 right after a heartbeat, fading to 0. The vignette uses it to throb. */
	public static float pulse() {
		return Math.max(0f, 1f - ticksSinceBeat / (float) PULSE_TICKS);
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(FearClientEffects::tick);
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null || player.isCreative() || player.isSpectator() || !player.isAlive()) {
			reset();
			return;
		}
		if (minecraft.isPaused()) return;

		float fear = AfflictionData.getFear(player);
		FearTier tier = FearTier.of(fear);
		ticksSinceBeat++;
		tickHeartbeat(minecraft, fear, tier);
		tickSway(minecraft, player, tier);
	}

	private static void reset() {
		heartbeatCountdown = 0;
		ticksSinceBeat = 1000;
		appliedYaw = 0;
		appliedPitch = 0;
	}

	private static void tickHeartbeat(Minecraft minecraft, float fear, FearTier tier) {
		AfflictionsConfig config = AfflictionsConfig.get();
		if (!config.fearHeartbeatEnabled || tier == FearTier.CALM) {
			heartbeatCountdown = 0;
			return;
		}
		if (--heartbeatCountdown > 0) return;
		float severity = FearTier.severity(fear);
		double bpm = Mth.lerp(severity, config.fearHeartbeatSlowBpm, config.fearHeartbeatFastBpm);
		heartbeatCountdown = Math.max(4, (int) Math.round(1200.0 / Math.max(20.0, bpm)));
		float volume = (float) (config.fearHeartbeatVolume * (0.6 + 0.4 * severity));
		float pitch = 0.9f + 0.3f * severity;
		minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WARDEN_HEARTBEAT, pitch, volume));
		ticksSinceBeat = 0;
	}

	/**
	 * Shaken and worse sways the view with slow overlapping sines. Only the change since the last tick is applied,
	 * so the view drifts around where the player aims and comes back instead of wandering off.
	 */
	private static void tickSway(Minecraft minecraft, LocalPlayer player, FearTier tier) {
		AfflictionsConfig config = AfflictionsConfig.get();
		double targetYaw = 0;
		double targetPitch = 0;
		if (tier.atLeast(FearTier.SHAKEN) && minecraft.gui.screen() == null) {
			double t = player.tickCount;
			double sway = config.fearSwayDegrees * (tier == FearTier.BREAKING ? 1.6 : 1.0);
			targetYaw = sway * (Math.sin(t * 0.13) + 0.6 * Math.sin(t * 0.31 + 1.7));
			targetPitch = sway * 0.7 * (Math.sin(t * 0.11 + 0.5) + 0.6 * Math.sin(t * 0.27));

			boolean drawing = player.isUsingItem()
					&& (player.getUseItem().is(Items.BOW) || player.getUseItem().is(Items.CROSSBOW));
			if (drawing && config.fearAimJitterDegrees > 0) {
				double draw = 0.4 + 0.6 * Math.min(1.0, player.getTicksUsingItem() / 20.0);
				double jitter = config.fearAimJitterDegrees * draw * (tier == FearTier.BREAKING ? 1.5 : 1.0);
				targetYaw += jitter * (player.getRandom().nextDouble() - 0.5) * 2.0;
				targetPitch += jitter * (player.getRandom().nextDouble() - 0.5) * 2.0;
			}
		}
		double dYaw = targetYaw - appliedYaw;
		double dPitch = targetPitch - appliedPitch;
		appliedYaw = targetYaw;
		appliedPitch = targetPitch;
		if (dYaw == 0 && dPitch == 0) return;
		player.setYRot(player.getYRot() + (float) dYaw);
		player.setXRot(Mth.clamp(player.getXRot() + (float) dPitch, -90f, 90f));
	}

	private FearClientEffects() {
	}
}
