package com.afflictions.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

import com.afflictions.config.AfflictionsConfig;
import com.afflictions.data.AfflictionData;

/** Phase 4: one small scar mark per scar in a row above the survival inventory, with hover details. */
public final class ScarIcons {
	private static final int ICON = 9;
	private static final int GAP = 3;
	private static final int PANEL_WIDTH = 176;
	private static final int PANEL_HEIGHT = 166;

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof InventoryScreen) {
				ScreenEvents.afterExtract(screen).register(ScarIcons::extract);
			}
		});
	}

	private static void extract(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) return;
		List<String> scars = AfflictionData.getPermanent(minecraft.player).scars();
		if (scars.isEmpty()) return;

		int left = (screen.width - PANEL_WIDTH) / 2;
		int y = (screen.height - PANEL_HEIGHT) / 2 - ICON - 3;
		for (int i = 0; i < scars.size(); i++) {
			int x = left + i * (ICON + GAP);
			drawScar(graphics, x, y);
			if (mouseX >= x && mouseX < x + ICON && mouseY >= y && mouseY < y + ICON) {
				List<Component> lines = new ArrayList<>();
				lines.add(Component.literal("Scar: " + displayName(scars.get(i))));
				lines.add(Component.literal("-" + trim(AfflictionsConfig.get().scarMaxHealthPenalty / 2.0) + " max hearts"));
				graphics.setComponentTooltipForNextFrame(minecraft.font, lines, mouseX, mouseY);
			}
		}
	}

	/** A pale diagonal cut with two stitches, drawn with fills so the mod needs no texture. */
	private static void drawScar(GuiGraphicsExtractor graphics, int x, int y) {
		graphics.fill(x - 1, y - 1, x + ICON + 1, y + ICON + 1, 0xFF000000);
		graphics.fill(x, y, x + ICON, y + ICON, 0xFF4A2A2A);
		for (int i = 0; i < ICON; i++) {
			graphics.fill(x + i, y + ICON - 1 - i, x + i + 1, y + ICON - i, 0xFFE8B8B0);
		}
		graphics.fill(x + 2, y + 2, x + 4, y + 3, 0xFFB03030);
		graphics.fill(x + 5, y + 5, x + 7, y + 6, 0xFFB03030);
	}

	/** "infection_zombie" becomes "Infection zombie". */
	private static String displayName(String id) {
		String spaced = id.replace('_', ' ');
		return spaced.isEmpty() ? spaced : Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
	}

	private static String trim(double value) {
		return value == Math.floor(value) ? String.valueOf((long) value) : String.format(Locale.ROOT, "%.1f", value);
	}

	private ScarIcons() {
	}
}
