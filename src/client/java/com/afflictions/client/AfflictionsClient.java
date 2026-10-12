package com.afflictions.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;

import com.afflictions.Afflictions;
import com.afflictions.data.AfflictionData;
import com.afflictions.data.PermanentState;
import com.afflictions.data.TransientState;

public class AfflictionsClient implements ClientModInitializer {
	private PermanentState lastPermanent;
	private TransientState lastTransient;

	@Override
	public void onInitializeClient() {
		FearHud.register();
		FearOverlay.register();
		FearClientEffects.register();
		LostHeartsHud.register();
		ScarIcons.register();
		TreatmentTooltips.register();
		WoundStatus.register();

		// /afflictions config opens the settings screen. The screen is opened next tick so the chat screen has closed.
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) ->
				dispatcher.register(ClientCommands.literal("afflictions")
						.then(ClientCommands.literal("wounds").executes(ctx -> {
							var lines = WoundStatus.lines(Minecraft.getInstance().player);
							if (lines.isEmpty()) ctx.getSource().sendFeedback(Component.translatable("afflictions.status.none"));
							lines.forEach(line -> ctx.getSource().sendFeedback(line));
							return 1;
						}))
						.then(ClientCommands.literal("config").executes(ctx -> {
							Minecraft minecraft = Minecraft.getInstance();
							minecraft.schedule(() -> minecraft.setScreenAndShow(new AfflictionsConfigScreen(null)));
							return 1;
						}))));

		// An "Afflictions" button in the top-left corner of the pause menu, clear of the vanilla button grid.
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (!(screen instanceof PauseScreen)) return;
			Screens.getWidgets(screen).add(Button.builder(Component.translatable("afflictions.config.open"),
					b -> client.setScreenAndShow(new AfflictionsConfigScreen(screen))).bounds(6, 6, 100, 20).build());
		});

		// Debug only: log the synced values once whenever they change.
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null) {
				this.lastPermanent = null;
				this.lastTransient = null;
				return;
			}
			PermanentState perm = AfflictionData.getPermanent(client.player);
			TransientState trans = AfflictionData.getTransient(client.player);
			if (!perm.equals(this.lastPermanent)) {
				Afflictions.LOGGER.debug("Synced permanent state: {}", perm);
				this.lastPermanent = perm;
			}
			if (!trans.equals(this.lastTransient)) {
				Afflictions.LOGGER.debug("Synced transient state: {}", trans);
				this.lastTransient = trans;
			}
		});
	}
}
