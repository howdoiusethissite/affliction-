package com.afflictions.data;

import java.util.HashMap;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;

/**
 * State that resets on death. Immutable: use the with... methods.
 * maxHealthLoss is health points taken off the maximum (2 per heart), see MaxHealthManager.
 * crippled maps a limb to the game tick its crippling ends, see CrippleManager.
 */
public record TransientState(float fear, Map<String, InfectionEntry> infections, float maxHealthLoss, Map<Limb, Long> crippled) {
	public static final float MAX_FEAR = 100f;
	public static final TransientState EMPTY = new TransientState(MAX_FEAR, Map.of(), 0f, Map.of());

	public TransientState {
		fear = Mth.clamp(fear, 0f, MAX_FEAR);
		infections = Map.copyOf(infections);
		maxHealthLoss = Math.max(0f, maxHealthLoss);
		crippled = Map.copyOf(crippled);
	}

	public TransientState(float fear, Map<String, InfectionEntry> infections, float maxHealthLoss) {
		this(fear, infections, maxHealthLoss, Map.of());
	}

	public static final Codec<TransientState> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.FLOAT.optionalFieldOf("fear", MAX_FEAR).forGetter(TransientState::fear),
			Codec.unboundedMap(Codec.STRING, InfectionEntry.CODEC).optionalFieldOf("infections", Map.of()).forGetter(TransientState::infections),
			Codec.FLOAT.optionalFieldOf("max_health_loss", 0f).forGetter(TransientState::maxHealthLoss),
			Codec.unboundedMap(Limb.CODEC, Codec.LONG).optionalFieldOf("crippled", Map.of()).forGetter(TransientState::crippled)
	).apply(i, TransientState::new));

	public static final StreamCodec<io.netty.buffer.ByteBuf, TransientState> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.FLOAT, TransientState::fear,
			ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, InfectionEntry.STREAM_CODEC), TransientState::infections,
			ByteBufCodecs.FLOAT, TransientState::maxHealthLoss,
			ByteBufCodecs.map(HashMap::new, Limb.STREAM_CODEC, ByteBufCodecs.VAR_LONG), TransientState::crippled,
			TransientState::new);

	public TransientState withFear(float fear) {
		return new TransientState(fear, this.infections, this.maxHealthLoss, this.crippled);
	}

	public TransientState withInfection(String key, InfectionEntry entry) {
		Map<String, InfectionEntry> m = new HashMap<>(this.infections);
		m.put(key, entry);
		return new TransientState(this.fear, m, this.maxHealthLoss, this.crippled);
	}

	public TransientState withoutInfection(String key) {
		Map<String, InfectionEntry> m = new HashMap<>(this.infections);
		m.remove(key);
		return new TransientState(this.fear, m, this.maxHealthLoss, this.crippled);
	}

	public TransientState withMaxHealthLoss(float loss) {
		return new TransientState(this.fear, this.infections, loss, this.crippled);
	}

	public TransientState withInfections(Map<String, InfectionEntry> infections) {
		return new TransientState(this.fear, infections, this.maxHealthLoss, this.crippled);
	}

	public TransientState withCrippled(Map<Limb, Long> crippled) {
		return new TransientState(this.fear, this.infections, this.maxHealthLoss, crippled);
	}
}
