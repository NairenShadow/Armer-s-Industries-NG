package fr.armersindustries.ssng.iles;

import java.util.Random;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;

/** Generateur de monde totalement vide (pour les iles du monde Build). */
public class GenerateurVide extends ChunkGenerator {

    @Override
    public byte[][] generateBlockSections(World world, Random random, int x, int z, BiomeGrid biomes) {
        // Biome "plaine" partout : sinon le biome par defaut est l'ocean, et le generateur
        // d'oceans de NationsGlory (ngupgrades) pose ses structures au fond du vide.
        if (biomes != null) {
            for (int bx = 0; bx < 16; bx++) {
                for (int bz = 0; bz < 16; bz++) {
                    biomes.setBiome(bx, bz, org.bukkit.block.Biome.PLAINS);
                }
            }
        }
        return new byte[world.getMaxHeight() / 16][];
    }

    @Override
    public boolean canSpawn(World world, int x, int z) {
        return true;
    }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, 0, 65, 0);
    }
}
