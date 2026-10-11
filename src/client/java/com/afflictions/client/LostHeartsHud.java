package com.afflictions.client;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import com.afflictions.Afflictions;
import com.afflictions.config.AfflictionsConfig;
import com.afflictions.health.MaxHealthManager;

/**
 * Phase 4: hearts taken from the maximum are drawn as faint dark outlines after the last real heart.
 * The amount comes from the synced max health modifier, so it matches what the server applied.
 */
public final class LostHeartsHud {
	private static final Identifier CONTAINER = Identifier.withDefaultNamespace("hud/heart/container");
	private static final int OUTLINE_COLOR = 0x90805050;

	public static void register() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.HEALTH_BAR, Afflictions.id("lost_hearts"), LostHeartsHud::extract);
	}

	private static void extract(GuiGraphicsExtractor graphics, DeltaTracker tickCounter) {
		if (!AfflictionsConfig.get().lostHeartsHudEnabled) return;
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || player.isCreative() || player.isSpectator()) return;

		AttributeInstance max = player.getAttribute(Attributes.MAX_HEALTH);
		AttributeModifier modifier = max == null ? null : max.getModifier(MaxHealthManager.MODIFIER_ID);
		if (modifier == null || modifier.amount() >= 0) return;

		int lostHearts = (int) Math.ceil(-modifier.amount() / 2.0);
		int shownHearts = (int) Math.ceil((player.getMaxHealth() + player.getAbsorptionAmount()) / 2.0);
		// Vanilla squeezes extra rows together once there are more than two.
		int totalRows = (int) Math.ceil((shownHearts + lostHearts) / 10.0);
		int rowHeight = Math.max(10 - (totalRows - 2), 3);
		int left = graphics.guiWidth() / 2 - 91;
		int bottom = graphics.guiHeight() - 39;

		for (int i = shownHearts; i < shownHearts + lostHearts; i++) {
			int x = left + (i % 10) * 8;
			int y = bottom - (i / 10) * rowHeight;
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, CONTAINER, x, y, 9, 9, OUTLINE_COLOR);
		}
	}

	private LostHeartsHud() {
	}
}
