package com.afflictions.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

import com.afflictions.Afflictions;
import com.afflictions.config.AfflictionsConfig;
import com.afflictions.data.AfflictionData;
import com.afflictions.data.InfectionEntry;
import com.afflictions.data.Limb;
import com.afflictions.data.TransientState;
import com.afflictions.limb.LimbManager;

/**
 * Where each wound, infection and crippled limb is and how far along it is. Used by the corner list on the HUD and by
 * {@code /afflictions wounds}. Everything is read from the synced transient state; times come from the client's game time.
 */
public final class WoundStatus {
	private static final int LINE_HEIGHT = 10;

	public static void register() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.FOOD_BAR, Afflictions.id("wound_list"), WoundStatus::extract);
	}

	/** One line per problem, worst first. Empty when the player is fine. */
	public static List<Component> lines(LocalPlayer player) {
		TransientState state = AfflictionData.getTransient(player);
		long now = player.level().getGameTime();
		int maxStage = AfflictionsConfig.get().infectionMaxStage;
		List<Component> lines = new ArrayList<>();

		state.infections().values().stream()
				.sorted(Comparator.comparingInt(InfectionEntry::stage).reversed())
				.forEach(entry -> {
					Component where = place(entry);
					Component time = clock(entry.riskEndsTick() - now);
					if (entry.stage() == 0) {
						lines.add(Component.translatable("afflictions.status.wound", where, time).withStyle(ChatFormatting.YELLOW));
					} else {
						lines.add(Component.translatable("afflictions.status.infection", where, entry.stage(), maxStage, time)
								.withStyle(entry.stage() >= maxStage ? ChatFormatting.DARK_RED : ChatFormatting.RED));
					}
				});

		state.crippled().entrySet().stream()
				.sorted(java.util.Map.Entry.comparingByKey())
				.forEach(entry -> lines.add(Component.translatable("afflictions.status.crippled",
						LimbManager.limbName(entry.getKey()), clock(entry.getValue() - now)).withStyle(ChatFormatting.GOLD)));
		return lines;
	}

	private static Component place(InfectionEntry entry) {
		Limb limb = entry.limbOrNull();
		return limb == null ? Component.translatable("afflictions.status.somewhere") : LimbManager.limbName(limb);
	}

	/** Ticks as minutes and seconds. */
	private static Component clock(long ticks) {
		long seconds = Math.max(0, ticks) / 20;
		return Component.literal(String.format("%d:%02d", seconds / 60, seconds % 60));
	}

	private static void extract(GuiGraphicsExtractor graphics, DeltaTracker tickCounter) {
		if (!AfflictionsConfig.get().woundHudEnabled) return;
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || player.isSpectator() || minecraft.options.hideGui) return;

		int y = 6;
		for (Component line : lines(player)) {
			graphics.text(minecraft.font, line, 6, y, 0xFFFFFFFF, true);
			y += LINE_HEIGHT;
		}
	}

	private WoundStatus() {
	}
}
