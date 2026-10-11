package com.afflictions.data;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** State that survives death (unless the config says otherwise). Immutable: use the with... methods. */
public record PermanentState(
		Map<String, PhobiaEntry> phobias,
		List<String> scars,
		Map<Limb, LimbState> limbs,
		Map<Limb, ImplantEntry> implants) {
	public static final PermanentState EMPTY = new PermanentState(Map.of(), List.of(), Map.of(), Map.of());

	public PermanentState {
		phobias = Map.copyOf(phobias);
		scars = List.copyOf(scars);
		limbs = Map.copyOf(limbs);
		implants = Map.copyOf(implants);
	}

	public static final Codec<PermanentState> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.unboundedMap(Codec.STRING, PhobiaEntry.CODEC).optionalFieldOf("phobias", Map.of()).forGetter(PermanentState::phobias),
			Codec.STRING.listOf().optionalFieldOf("scars", List.of()).forGetter(PermanentState::scars),
			Codec.unboundedMap(Limb.CODEC, LimbState.CODEC).optionalFieldOf("limbs", Map.of()).forGetter(PermanentState::limbs),
			Codec.unboundedMap(Limb.CODEC, ImplantEntry.CODEC).optionalFieldOf("implants", Map.of()).forGetter(PermanentState::implants)
	).apply(i, PermanentState::new));

	public static final StreamCodec<io.netty.buffer.ByteBuf, PermanentState> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, PhobiaEntry.STREAM_CODEC), PermanentState::phobias,
			ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), PermanentState::scars,
			ByteBufCodecs.map(HashMap::new, Limb.STREAM_CODEC, LimbState.STREAM_CODEC), PermanentState::limbs,
			ByteBufCodecs.map(HashMap::new, Limb.STREAM_CODEC, ImplantEntry.STREAM_CODEC), PermanentState::implants,
			PermanentState::new);

	public LimbState limb(Limb limb) {
		return this.limbs.getOrDefault(limb, LimbState.INTACT);
	}

	public PermanentState withPhobia(String key, PhobiaEntry entry) {
		Map<String, PhobiaEntry> m = new HashMap<>(this.phobias);
		m.put(key, entry);
		return new PermanentState(m, this.scars, this.limbs, this.implants);
	}

	public PermanentState withoutPhobia(String key) {
		Map<String, PhobiaEntry> m = new HashMap<>(this.phobias);
		m.remove(key);
		return new PermanentState(m, this.scars, this.limbs, this.implants);
	}

	public PermanentState withScar(String id) {
		if (this.scars.contains(id)) return this;
		List<String> l = new ArrayList<>(this.scars);
		l.add(id);
		return new PermanentState(this.phobias, l, this.limbs, this.implants);
	}

	public PermanentState withoutScar(String id) {
		List<String> l = new ArrayList<>(this.scars);
		l.remove(id);
		return new PermanentState(this.phobias, l, this.limbs, this.implants);
	}

	public PermanentState withLimb(Limb limb, LimbState state) {
		Map<Limb, LimbState> m = new EnumMap<>(Limb.class);
		m.putAll(this.limbs);
		m.put(limb, state);
		return new PermanentState(this.phobias, this.scars, m, this.implants);
	}

	public PermanentState withImplant(Limb limb, ImplantEntry entry) {
		Map<Limb, ImplantEntry> m = new EnumMap<>(Limb.class);
		m.putAll(this.implants);
		m.put(limb, entry);
		return new PermanentState(this.phobias, this.scars, this.limbs, m);
	}

	public PermanentState withoutImplant(Limb limb) {
		Map<Limb, ImplantEntry> m = new EnumMap<>(Limb.class);
		m.putAll(this.implants);
		m.remove(limb);
		return new PermanentState(this.phobias, this.scars, this.limbs, m);
	}
}
