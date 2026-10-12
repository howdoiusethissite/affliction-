package com.afflictions.client;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;

import com.afflictions.Afflictions;
import com.afflictions.config.AfflictionsConfig;
import com.afflictions.data.AfflictionData;
import com.afflictions.fear.FearTier;

/** Darkened screen edges, a gray wash that dulls colors, and the unreliable health and hunger bars of Breaking. */
public final class FearOverlay {
	private static final int VIGNETTE_STEPS = 16;
	private static final int SALT_HEALTH = 0x51ED;
	private static final int SALT_FOOD = 0x7C3B;

	public static void register() {
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Afflictions.id("fear_overlay"), FearOverlay::extract);
		HudElementRegistry.replaceElement(VanillaHudElements.HEALTH_BAR, original -> (graphics, tracker) -> {
			if (!glitchHidden(SALT_HEALTH)) original.extractRenderState(graphics, tracker);
		});
		HudElementRegistry.replaceElement(VanillaHudElements.FOOD_BAR, original -> (graphics, tracker) -> {
			if (!glitchHidden(SALT_FOOD)) original.extractRenderState(graphics, tracker);
		});
	}

	private static void extract(GuiGraphicsExtractor graphics, DeltaTracker tracker) {
		AfflictionsConfig config = AfflictionsConfig.get();
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || player.isCreative() || player.isSpectator()) return;
		float fear = AfflictionData.getFear(player);
		if (FearTier.of(fear) == FearTier.CALM) return;
		float severity = FearTier.severity(fear);
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();

		// Gray wash: blending toward mid gray is what makes colors look drained.
		int wash = alpha(config.fearDesaturationMaxAlpha * severity);
		if (wash > 0) graphics.fill(0, 0, width, height, (wash << 24) | 0x7F7F7F);

		if (!config.fearVignetteEnabled) return;
		double throb = 1.0 + 0.3 * FearClientEffects.pulse();
		double peak = Math.min(1.0, config.fearVignetteMaxAlpha * severity * throb);
		int step = Math.max(2, (int) (Math.min(width, height) * 0.32 / VIGNETTE_STEPS));
		for (int i = 0; i < VIGNETTE_STEPS; i++) {
			double falloff = 1.0 - i / (double) VIGNETTE_STEPS;
			int a = alpha(peak * falloff * falloff);
			if (a <= 0) continue;
			int color = (a << 24) | 0x140000;
			int in = i * step;
			graphics.fill(in, in, width - in, in + step, color);
			graphics.fill(in, height - in - step, width - in, height - in, color);
			graphics.fill(in, in + step, in + step, height - in - step, color);
			graphics.fill(width - in - step, in + step, width - in, height - in - step, color);
		}
	}

	private static int alpha(double fraction) {
		return Math.max(0, Math.min(255, (int) Math.round(fraction * 255.0)));
	}

	/**
	 * Breaking only. A fast flicker (a few ticks at a time) plus rarer stretches where the bar is gone for about two
	 * seconds. Both are pure functions of the tick count so the two bars glitch independently but steadily.
	 */
	private static boolean glitchHidden(int salt) {
		AfflictionsConfig config = AfflictionsConfig.get();
		LocalPlayer player = Minecraft.getInstance().player;
		if (!config.fearHudGlitchEnabled || player == null || player.isCreative() || player.isSpectator()) return false;
		float fear = AfflictionData.getFear(player);
		if (FearTier.of(fear) != FearTier.BREAKING) return false;
		float severity = FearTier.severity(fear);
		int tick = player.tickCount;
		int flicker = mix(tick / 3, salt) & 0xFF;
		int gone = mix(tick / 40, salt ^ 0x9E37) & 0xFF;
		return flicker < 255 * (0.08 + 0.3 * severity) || gone < 255 * 0.12 * severity;
	}

	private static int mix(int value, int salt) {
		int h = value * 0x45D9F3B + salt * 0x119DE1F3;
		h ^= h >>> 15;
		h *= 0x2C1B3C6D;
		h ^= h >>> 12;
		return h & 0x7FFFFFFF;
	}

	private FearOverlay() {
	}
}
