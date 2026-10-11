package com.afflictions.client;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;

import com.afflictions.Afflictions;
import com.afflictions.config.AfflictionsConfig;
import com.afflictions.data.AfflictionData;
import com.afflictions.data.TransientState;

/** Phase 3: a small fear bar beside the hotbar, on the same side as the hunger bar. */
public final class FearHud {
	private static final int BAR_WIDTH = 40;
	private static final int BAR_HEIGHT = 5;
	/** Gap between the hotbar's right edge and the bar. Leaves room for a right-hand offhand slot. */
	private static final int HOTBAR_GAP = 36;

	public static void register() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.FOOD_BAR, Afflictions.id("fear_bar"), FearHud::extract);
	}

	private static void extract(GuiGraphicsExtractor graphics, DeltaTracker tickCounter) {
		AfflictionsConfig config = AfflictionsConfig.get();
		if (!config.fearHudEnabled) return;
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || player.isCreative() || player.isSpectator()) return;

		float fear = AfflictionData.getFear(player);
		if (!config.fearHudAlwaysVisible && fear >= TransientState.MAX_FEAR) return;

		int x = graphics.guiWidth() / 2 + 91 + HOTBAR_GAP;
		int y = graphics.guiHeight() - 12;
		int filled = Math.round(BAR_WIDTH * fear / TransientState.MAX_FEAR);

		// Calm blue-green at full fear, shifting to red as it empties.
		int color = ARGB.linearLerp(fear / TransientState.MAX_FEAR, 0xFFC03030, 0xFF58B0C8);

		graphics.fill(x - 1, y - 1, x + BAR_WIDTH + 1, y + BAR_HEIGHT + 1, 0xFF000000);
		graphics.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, 0xFF303030);
		if (filled > 0) graphics.fill(x, y, x + filled, y + BAR_HEIGHT, color);
		graphics.text(minecraft.font, Component.translatable("afflictions.hud.fear"), x, y - minecraft.font.lineHeight - 1, 0xFFFFFFFF, true);
	}

	private FearHud() {
	}
}
