package com.afflictions.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.client.Minecraft;

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

		// /afflictions config opens the settings screen. The screen is opened next tick so the chat screen has closed.
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) ->
				dispatcher.register(ClientCommands.literal("afflictions")
						.then(ClientCommands.literal("config").executes(ctx -> {
							Minecraft minecraft = Minecraft.getInstance();
							minecraft.schedule(() -> minecraft.setScreenAndShow(new AfflictionsConfigScreen(null)));
							return 1;
						}))));

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
