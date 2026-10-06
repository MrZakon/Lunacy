package net.lunacy.visuals.holyworld;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.nio.charset.StandardCharsets;

/**
 * Custom payload implementation for HolyWorld In-Game LiteAPI.
 * Channel: liteapi:feature-control
 */
public record LiteApiPayload(String json) implements CustomPayload {
    public static final CustomPayload.Id<LiteApiPayload> ID =
            new CustomPayload.Id<>(Identifier.of("liteapi", "feature-control"));

    public static final PacketCodec<PacketByteBuf, LiteApiPayload> CODEC =
            CustomPayload.codecOf(LiteApiPayload::write, LiteApiPayload::read);

    public static LiteApiPayload read(PacketByteBuf buf) {
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        String str = new String(bytes, StandardCharsets.UTF_8).trim();
        int brace = str.indexOf('{');
        if (brace > 0) {
            str = str.substring(brace);
        }
        return new LiteApiPayload(str);
    }

    public void write(PacketByteBuf buf) {
        byte[] bytes = this.json.getBytes(StandardCharsets.UTF_8);
        buf.writeBytes(bytes);
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
