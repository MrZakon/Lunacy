package net.lunacy.visuals.module.impl.visual;
import net.lunacy.visuals.event.Subscribe;
import net.lunacy.visuals.event.impl.TickEvent;
import net.lunacy.visuals.module.Module;
import net.lunacy.visuals.module.ModuleCategory;
import net.lunacy.visuals.module.setting.*;
import net.lunacy.visuals.render.VisualQuality;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.BlockPos;
import java.util.Random;

/** Local, bounded environmental particles. No gameplay or server changes. */
public final class BiomeAtmosphereModule extends Module {
    private final DoubleSetting density = add(new DoubleSetting("density", "Плотность", "Частицы вокруг игрока", 5, 1, 16, 1));
    private final DoubleSetting radius = add(new DoubleSetting("radius", "Радиус", "Дальность атмосферы", 10, 3, 20, 1));
    private final Random random = new Random();
    private double cold, forest;
    private Object world;
    private int tick;
    public BiomeAtmosphereModule() { super("biome_atmosphere", "Атмосфера биомов", "Листья, светлячки и ледяные искры с плавными переходами", ModuleCategory.WORLD_VISUALS); }
    @Subscribe private void tick(TickEvent.Client event) {
        var client = event.client();
        if (client.player == null || client.level == null) { reset(); return; }
        if (world != client.level) { reset(); world = client.level; }
        String biome = client.level.getBiome(client.player.blockPosition()).unwrapKey().map(k -> k.identifier().toString()).orElse("");
        boolean snowy = biome.contains("snow") || biome.contains("frozen") || biome.contains("ice") || biome.contains("grove");
        boolean wooded = biome.contains("forest") || biome.contains("jungle") || biome.contains("cherry") || biome.contains("taiga");
        cold += ((snowy ? 1 : 0) - cold) * .04;
        forest += ((wooded ? 1 : 0) - forest) * .04;
        if (++tick % VisualQuality.cadence(3) != 0) return;
        boolean night = Math.floorMod(client.level.getGameTime(), 24000) > 13000;
        for (int i=0; i<VisualQuality.particles(density.get().intValue()); i++) {
            double x = client.player.getX() + (random.nextDouble()-.5)*radius.get()*2;
            double y = client.player.getY() + random.nextDouble()*6;
            double z = client.player.getZ() + (random.nextDouble()-.5)*radius.get()*2;
            if (!client.level.canSeeSky(BlockPos.containing(x,y,z))) continue;
            var type = random.nextDouble() < cold ? ParticleTypes.SNOWFLAKE
                : random.nextDouble() < forest ? (night ? ParticleTypes.END_ROD : ParticleTypes.CHERRY_LEAVES) : null;
            if (type != null) client.particleEngine.createParticle(type, x,y,z, .015, night ? .002 : -.015, .005);
        }
    }
    private void reset() { cold=forest=0; tick=0; world=null; }
    @Override protected void onDisable() { reset(); }
}
