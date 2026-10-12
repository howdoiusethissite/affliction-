package com.afflictions;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.afflictions.cleric.ClericManager;
import com.afflictions.command.AfflictCommand;
import com.afflictions.command.AmputateCommand;
import com.afflictions.compat.FfinjuryCompat;
import com.afflictions.config.AfflictionsConfig;
import com.afflictions.data.AfflictionAttachments;
import com.afflictions.data.AfflictionData;
import com.afflictions.fear.FearEffects;
import com.afflictions.fear.FearManager;
import com.afflictions.health.MaxHealthManager;
import com.afflictions.implant.AfflictionItems;
import com.afflictions.infection.InfectionManager;
import com.afflictions.limb.CrippleManager;
import com.afflictions.limb.LimbManager;
import com.afflictions.data.PermanentState;
import com.afflictions.phobia.PhobiaManager;

public class Afflictions implements ModInitializer {
	public static final String MOD_ID = "afflictions";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		AfflictionsConfig.load();
		AfflictionAttachments.initialize();

		AfflictionItems.register();
		PhobiaManager.register();
		FearManager.register();
		FearEffects.register();
		InfectionManager.register();
		MaxHealthManager.register();
		ClericManager.register();
		LimbManager.register();
		CrippleManager.register();
		FfinjuryCompat.register();

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
					AfflictCommand.register(dispatcher);
					AmputateCommand.register(dispatcher);
				});

		// copyOnDeath is fixed at registration, so the config toggle works by clearing afterwards.
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			if (!alive && !AfflictionsConfig.get().permanentConditionsSurviveDeath) {
				AfflictionData.setPermanent(newPlayer, PermanentState.EMPTY);
			}
		});
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
