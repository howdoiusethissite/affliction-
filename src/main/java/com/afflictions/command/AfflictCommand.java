package com.afflictions.command;

import java.util.Arrays;
import java.util.Locale;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

import com.afflictions.data.AfflictionData;
import com.afflictions.config.AfflictionsConfig;
import com.afflictions.data.InfectionEntry;
import com.afflictions.data.Limb;
import com.afflictions.data.LimbState;
import com.afflictions.data.PermanentState;
import com.afflictions.data.TransientState;
import com.afflictions.fear.FearManager;
import com.afflictions.phobia.PhobiaKeys;

public final class AfflictCommand {
	private static final String[] TIERS = {"wood", "iron", "redstone", "netherite"};

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("afflict")
				.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
				.then(Commands.literal("list").then(player()
						.executes(ctx -> list(ctx))))
				.then(Commands.literal("phobia")
						.then(Commands.literal("add").then(player().then(phobiaKey().executes(ctx -> {
							AfflictionData.addPhobia(target(ctx), PhobiaKeys.normalize(StringArgumentType.getString(ctx, "key")));
							return done(ctx, "Added phobia");
						}))))
						.then(Commands.literal("remove").then(player().then(phobiaKey().executes(ctx -> {
							AfflictionData.removePhobia(target(ctx), PhobiaKeys.normalize(StringArgumentType.getString(ctx, "key")));
							return done(ctx, "Removed phobia");
						})))))
				.then(Commands.literal("scar")
						.then(Commands.literal("add").then(player().then(key("id").executes(ctx -> {
							AfflictionData.addScar(target(ctx), StringArgumentType.getString(ctx, "id"));
							return done(ctx, "Added scar");
						}))))
						.then(Commands.literal("remove").then(player().then(key("id").executes(ctx -> {
							AfflictionData.removeScar(target(ctx), StringArgumentType.getString(ctx, "id"));
							return done(ctx, "Removed scar");
						})))))
				.then(Commands.literal("limb")
						.then(Commands.literal("amputate").then(player().then(limb().executes(ctx -> {
							AfflictionData.setLimb(target(ctx), limbArg(ctx), LimbState.AMPUTATED);
							return done(ctx, "Amputated limb");
						}))))
						.then(Commands.literal("restore").then(player().then(limb().executes(ctx -> {
							AfflictionData.setLimb(target(ctx), limbArg(ctx), LimbState.INTACT);
							return done(ctx, "Restored limb");
						})))))
				.then(Commands.literal("implant")
						.then(Commands.literal("set").then(player().then(limb().then(
								Commands.argument("tier", StringArgumentType.word())
										.suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(TIERS, b))
										.executes(ctx -> {
											String tier = StringArgumentType.getString(ctx, "tier").toLowerCase(Locale.ROOT);
											if (!Arrays.asList(TIERS).contains(tier)) {
												throw new SimpleCommandExceptionType(Component.literal("Tier must be one of " + String.join(", ", TIERS))).create();
											}
											AfflictionData.setImplant(target(ctx), limbArg(ctx), tier);
											return done(ctx, "Set implant");
										})))))
						.then(Commands.literal("clear").then(player().then(limb().executes(ctx -> {
							AfflictionData.clearImplant(target(ctx), limbArg(ctx));
							return done(ctx, "Cleared implant");
						})))))
				.then(Commands.literal("infection")
						.then(Commands.literal("add").then(player().then(phobiaKey("source").executes(ctx -> addInfection(ctx, 0))
								.then(Commands.argument("stage", IntegerArgumentType.integer(0, 9)).executes(ctx ->
										addInfection(ctx, IntegerArgumentType.getInteger(ctx, "stage")))))))
						.then(Commands.literal("remove").then(player().then(phobiaKey("source").executes(ctx -> {
							AfflictionData.removeInfection(target(ctx), PhobiaKeys.normalize(StringArgumentType.getString(ctx, "source")));
							return done(ctx, "Removed infection");
						})))))
				.then(Commands.literal("maxhealth")
						.then(Commands.literal("loss").then(player().then(
								Commands.argument("points", FloatArgumentType.floatArg(0f, 40f)).executes(ctx -> {
									AfflictionData.setMaxHealthLoss(target(ctx), FloatArgumentType.getFloat(ctx, "points"));
									return done(ctx, "Set max health loss");
								})))))
				.then(Commands.literal("fear")
						.then(Commands.literal("rate").then(player().executes(ctx -> rate(ctx))))
						.then(Commands.literal("set").then(player().then(
						Commands.argument("value", FloatArgumentType.floatArg(0f, 100f)).executes(ctx -> {
							AfflictionData.setFear(target(ctx), FloatArgumentType.getFloat(ctx, "value"));
							return done(ctx, "Set fear");
						})))))
				.then(Commands.literal("clear").then(player().executes(ctx -> {
					AfflictionData.clearAll(target(ctx));
					return done(ctx, "Cleared all afflictions");
				}))));
	}

	private static ArgumentBuilder<CommandSourceStack, ?> player() {
		return Commands.argument("player", EntityArgument.player());
	}

	/** Greedy so that "minecraft:zombie" needs no quotes. Bare names like "zombie" are accepted too. */
	private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> phobiaKey() {
		return phobiaKey("key");
	}

	private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> phobiaKey(String name) {
		return Commands.argument(name, StringArgumentType.greedyString())
				.suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(PhobiaKeys.allKnownKeys(), b));
	}

	private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> key(String name) {
		return Commands.argument(name, StringArgumentType.string());
	}

	private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> limb() {
		return Commands.argument("limb", StringArgumentType.word())
				.suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
						Arrays.stream(Limb.values()).map(Limb::getSerializedName), b));
	}

	private static ServerPlayer target(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		return EntityArgument.getPlayer(ctx, "player");
	}

	private static Limb limbArg(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		String s = StringArgumentType.getString(ctx, "limb");
		try {
			return Limb.valueOf(s.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			throw new SimpleCommandExceptionType(Component.literal("Unknown limb '" + s + "'")).create();
		}
	}

	private static int done(CommandContext<CommandSourceStack> ctx, String what) throws CommandSyntaxException {
		ServerPlayer p = target(ctx);
		ctx.getSource().sendSuccess(() -> Component.literal(what + " for " + p.getName().getString()), true);
		return 1;
	}

	/** Stage 0 opens a fresh wound with a full risk window. A later stage starts already infected. */
	private static int addInfection(CommandContext<CommandSourceStack> ctx, int stage) throws CommandSyntaxException {
		ServerPlayer p = target(ctx);
		String key = PhobiaKeys.normalize(StringArgumentType.getString(ctx, "source"));
		AfflictionsConfig config = AfflictionsConfig.get();
		long ends = p.level().getGameTime() + (stage == 0 ? config.infectionRiskWindowTicks : config.infectionStageTicks);
		AfflictionData.setInfection(p, key, new InfectionEntry(key, ends, stage));
		return done(ctx, "Added infection at stage " + stage);
	}

	private static int rate(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer p = target(ctx);
		FearManager.Rates r = FearManager.compute(p, true);
		ctx.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
				"Fear %.1f for %s, net %+.2f/s. Drain: dark %.2f, cave %.2f, phobia %.2f. Recovery: campfire %.2f, jukebox %.2f, companion %.2f, sleep %.2f, passive %.2f",
				AfflictionData.getFear(p), p.getName().getString(), r.net(),
				r.dark(), r.cave(), r.phobia(), r.campfire(), r.jukebox(), r.companion(), r.sleep(), r.passive())), false);
		return 1;
	}

	private static int list(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer p = target(ctx);
		PermanentState perm = AfflictionData.getPermanent(p);
		TransientState trans = AfflictionData.getTransient(p);
		ctx.getSource().sendSuccess(() -> Component.literal(
				"Afflictions for " + p.getName().getString()
						+ "\npermanent: " + perm
						+ "\ntransient: " + trans), false);
		return 1;
	}

	private AfflictCommand() {
	}
}
