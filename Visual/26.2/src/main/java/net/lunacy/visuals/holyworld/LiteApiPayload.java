package net.lunacy.visuals.holyworld;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;

/**
 * Custom payload implementation for HolyWorld In-Game LiteAPI.
 * Channel: liteapi:feature-control
 */
public record LiteApiPayload(String json) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<LiteApiPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("liteapi", "feature-control"));

    public static final StreamCodec<FriendlyByteBuf, LiteApiPayload> STREAM_CODEC =
            CustomPacketPayload.codec(LiteApiPayload::write, LiteApiPayload::read);

    public static LiteApiPayload read(FriendlyByteBuf buf) {
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        String str = new String(bytes, StandardCharsets.UTF_8).trim();
        int brace = str.indexOf('{');
        if (brace > 0) {
            str = str.substring(brace);
        }
        return new LiteApiPayload(str);
    }

    public void write(FriendlyByteBuf buf) {
        byte[] bytes = this.json.getBytes(StandardCharsets.UTF_8);
        buf.writeBytes(bytes);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
