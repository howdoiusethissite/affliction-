package com.afflictions.data;

import java.util.HashMap;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;

/** State that resets on death. Immutable: use the with... methods. */
public record TransientState(float fear, Map<String, InfectionEntry> infections) {
	public static final float MAX_FEAR = 100f;
	public static final TransientState EMPTY = new TransientState(MAX_FEAR, Map.of());

	public TransientState {
		fear = Mth.clamp(fear, 0f, MAX_FEAR);
		infections = Map.copyOf(infections);
	}

	public static final Codec<TransientState> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.FLOAT.optionalFieldOf("fear", MAX_FEAR).forGetter(TransientState::fear),
			Codec.unboundedMap(Codec.STRING, InfectionEntry.CODEC).optionalFieldOf("infections", Map.of()).forGetter(TransientState::infections)
	).apply(i, TransientState::new));

	public static final StreamCodec<io.netty.buffer.ByteBuf, TransientState> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.FLOAT, TransientState::fear,
			ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, InfectionEntry.STREAM_CODEC), TransientState::infections,
			TransientState::new);

	public TransientState withFear(float fear) {
		return new TransientState(fear, this.infections);
	}

	public TransientState withInfection(String key, InfectionEntry entry) {
		Map<String, InfectionEntry> m = new HashMap<>(this.infections);
		m.put(key, entry);
		return new TransientState(this.fear, m);
	}

	public TransientState withoutInfection(String key) {
		Map<String, InfectionEntry> m = new HashMap<>(this.infections);
		m.remove(key);
		return new TransientState(this.fear, m);
	}
}
