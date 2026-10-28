package fr.armersindustries.ssng.iles;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import fr.armersindustries.ssng.core.SSNG;
import fr.armersindustries.ssng.core.ReseauNG;

/**
 * Gestion des iles du monde Build : stockage, emplacement sur la grille,
 * generation (couche de terre en Y=0), permissions par role.
 *
 * Chaque ile occupe sa propre "region" Minecraft de 512x512 blocs : l'ile n
 * est centree sur la region (col, ligne) avec col = n % 40, ligne = n / 40.
 * La region (0,0) est reservee au spawn du monde Build.
 */
public class GestionIles {

    public static final int REGION = 512;
    /** Hauteur de la couche d'herbe des iles (5 couches de terre en dessous, Y 0 a 4). */
    public static final int SOL = 5;
    private static final int COLONNES = 40;
    private static final String[] BIOMES = {"plaine", "marais", "desert", "neige", "jungle"};

    private final IlesNG plugin;
    private final Map<Integer, Ile> iles = new TreeMap<Integer, Ile>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final File dossier;

    public GestionIles(IlesNG plugin) {
        this.plugin = plugin;
        this.dossier = new File(plugin.getDataFolder(), "iles");
        charger();
    }

    // ------------------------------------------------------------------ stockage

    private void charger() {
        dossier.mkdirs();
        File[] fichiers = dossier.listFiles();
        if (fichiers == null) {
            return;
        }
        for (File f : fichiers) {
            if (!f.getName().endsWith(".json")) {
                continue;
            }
            try {
                Reader r = new InputStreamReader(new FileInputStream(f), "UTF-8");
                try {
                    Ile ile = gson.fromJson(r, Ile.class);
                    if (ile != null) {
                        ile.completer();
                        iles.put(ile.id, ile);
                    }
                } finally {
                    r.close();
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Ile illisible : " + f.getName(), e);
            }
        }
        plugin.getLogger().info(iles.size() + " ile(s) chargee(s).");
    }

    public void sauver(Ile ile) {
        try {
            File f = new File(dossier, ile.id + ".json");
            File tmp = new File(dossier, ile.id + ".json.tmp");
            Writer w = new OutputStreamWriter(new FileOutputStream(tmp), "UTF-8");
            try {
                gson.toJson(ile, w);
            } finally {
                w.close();
            }
            if (f.exists() && !f.delete()) {
                plugin.getLogger().warning("Impossible de remplacer " + f);
            }
            if (!tmp.renameTo(f)) {
                plugin.getLogger().warning("Impossible d'enregistrer " + f);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Sauvegarde de l'ile " + ile.id + " impossible", e);
        }
    }

    public Collection<Ile> toutes() {
        List<Ile> l = new ArrayList<Ile>();
        for (Ile i : iles.values()) {
            if (!i.supprimee) {
                l.add(i);
            }
        }
        return l;
    }

    public Ile ile(int id) {
        Ile i = iles.get(id);
        return i == null || i.supprimee ? null : i;
    }

    public Ile ile(String id) {
        try {
            return ile(Integer.parseInt(id.trim()));
        } catch (Exception e) {
            return null;
        }
    }

    public List<Ile> ilesDe(String joueur) {
        List<Ile> l = new ArrayList<Ile>();
        for (Ile i : toutes()) {
            if (i.estCreateur(joueur)) {
                l.add(i);
            }
        }
        return l;
    }

    // ------------------------------------------------------------------ geometrie

    public World monde() {
        return plugin.monde(IlesNG.TypeMonde.BUILD);
    }

    public int centreX(int id) {
        return (id % COLONNES) * REGION + REGION / 2;
    }

    public int centreZ(int id) {
        return (id / COLONNES) * REGION + REGION / 2;
    }

    /** Ile dont la zone contient ce point (monde Build uniquement), ou null. */
    public Ile ileA(Location l) {
        if (l == null || l.getWorld() == null || !l.getWorld().equals(monde())) {
            return null;
        }
        int col = (int) Math.floor(l.getX() / REGION);
        int lig = (int) Math.floor(l.getZ() / REGION);
        if (col < 0 || col >= COLONNES || lig < 0) {
            return null;
        }
        Ile i = ile(lig * COLONNES + col);
        if (i == null) {
            return null;
        }
        int demi = i.taille / 2;
        if (Math.abs(l.getBlockX() - centreX(i.id)) > demi || Math.abs(l.getBlockZ() - centreZ(i.id)) > demi) {
            return null;
        }
        return i;
    }

    public Location spawn(Ile i) {
        World w = monde();
        if (i.spawnY > 0) {
            return new Location(w, i.spawnX, i.spawnY, i.spawnZ, i.spawnYaw, 0f);
        }
        return new Location(w, centreX(i.id) + 0.5, SOL + 1.2, centreZ(i.id) + 0.5);
    }

    // ------------------------------------------------------------------ permissions

    /** Role du joueur : "createur", "membre", "visiteur" ou "banni". Les admins sont "createur". */
    public String role(Player p, Ile i) {
        if (SSNG.admin(p) || i.estCreateur(p.getName())) {
            return "createur";
        }
        if (i.estBanni(p.getName())) {
            return "banni";
        }
        return i.estMembre(p.getName()) ? "membre" : "visiteur";
    }

    public boolean permission(Player p, Ile i, String perm) {
        String r = role(p, i);
        if (r.equals("createur")) {
            return true;
        }
        if (r.equals("banni")) {
            return false;
        }
        Map<String, Boolean> m = r.equals("membre") ? i.permsMembres : i.permsVisiteurs;
        Boolean b = m.get(perm);
        return b != null && b.booleanValue();
    }

    public List<String> permissionsDe(Player p, Ile i) {
        List<String> l = new ArrayList<String>();
        for (String perm : Ile.PERMISSIONS) {
            if (permission(p, i, perm)) {
                l.add(perm);
            }
        }
        return l;
    }

    // ------------------------------------------------------------------ creation / generation

    public boolean biomeValide(String b) {
        for (String x : BIOMES) {
            if (x.equals(b)) {
                return true;
            }
        }
        return false;
    }

    public static Biome biomeBukkit(String b) {
        if ("marais".equals(b)) {
            return Biome.SWAMPLAND;
        }
        if ("desert".equals(b)) {
            return Biome.DESERT;
        }
        if ("neige".equals(b)) {
            return Biome.ICE_PLAINS;
        }
        if ("jungle".equals(b)) {
            return Biome.JUNGLE;
        }
        return Biome.PLAINS;
    }

    /** Cree l'ile (donnees) et lance la generation du terrain. */
    public Ile creer(Player p, String nom, String description, int taille, String biome, boolean privee) {
        int id = plugin.donnees().getInt("iles-ng.prochain", 1);
        while (iles.containsKey(id) || id % COLONNES == 0 && id / COLONNES == 0) {
            id++;
        }
        plugin.donnees().set("iles-ng.prochain", id + 1);
        plugin.sauverDonnees();

        Ile i = new Ile();
        i.parDefaut();
        i.id = id;
        i.nom = nom;
        i.description = description;
        i.createur = p.getName();
        i.taille = Math.max(25, Math.min(plugin.getConfig().getInt("iles.taille-max", 500), taille));
        i.biome = biomeValide(biome) ? biome : "plaine";
        i.privee = privee;
        i.creation = System.currentTimeMillis();
        i.membres.add(p.getName());
        iles.put(id, i);
        sauver(i);
        terrain(i, false, null);
        return i;
    }

    /**
     * (Re)genere le terrain : couche de terre en Y=0 sur toute la surface et biome.
     * Si reset, les chunks sont d'abord vides. Le travail est etale sur plusieurs ticks.
     */
    public void terrain(final Ile i, final boolean reset, final Runnable fin) {
        final World w = monde();
        if (w == null) {
            return;
        }
        final int demi = i.taille / 2;
        final int x0 = centreX(i.id) - demi;
        final int z0 = centreZ(i.id) - demi;
        final int x1 = centreX(i.id) + demi;
        final int z1 = centreZ(i.id) + demi;
        final List<int[]> chunks = new ArrayList<int[]>();
        for (int cx = x0 >> 4; cx <= x1 >> 4; cx++) {
            for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) {
                chunks.add(new int[] {cx, cz});
            }
        }
        final Biome biome = biomeBukkit(i.biome);
        final int parTick = plugin.getConfig().getInt("iles.chunks-par-tick", 6);
        new org.bukkit.scheduler.BukkitRunnable() {
            int n = 0;

            public void run() {
                for (int k = 0; k < parTick && n < chunks.size(); k++, n++) {
                    int cx = chunks.get(n)[0];
                    int cz = chunks.get(n)[1];
                    if (reset) {
                        w.regenerateChunk(cx, cz);
                    } else {
                        w.loadChunk(cx, cz, true);
                    }
                    for (int x = Math.max(x0, cx << 4); x <= Math.min(x1, (cx << 4) + 15); x++) {
                        for (int z = Math.max(z0, cz << 4); z <= Math.min(z1, (cz << 4) + 15); z++) {
                            if (!reset) {
                                // retire ce qui a pu etre genere a cet endroit (structures des mods)
                                for (int y = SOL + 1; y < 128; y++) {
                                    if (w.getBlockTypeIdAt(x, y, z) != 0) {
                                        w.getBlockAt(x, y, z).setTypeIdAndData(0, (byte) 0, false);
                                    }
                                }
                            }
                            for (int y = 0; y < SOL; y++) {
                                w.getBlockAt(x, y, z).setTypeIdAndData(3, (byte) 0, false);
                            }
                            w.getBlockAt(x, SOL, z).setTypeIdAndData(2, (byte) 0, false);
                            w.setBiome(x, z, biome);
                        }
                    }
                    w.refreshChunk(cx, cz);
                }
                if (n >= chunks.size()) {
                    plugin.getLogger().info("Terrain de l'ile " + i.id + " (" + i.taille + "x" + i.taille + ") pret.");
                    cancel();
                    if (fin != null) {
                        fin.run();
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /** Applique un nouveau biome a toute la zone de l'ile. */
    public void appliquerBiome(final Ile i) {
        final World w = monde();
        final Biome biome = biomeBukkit(i.biome);
        final int demi = i.taille / 2;
        final int x0 = centreX(i.id) - demi;
        final int z0 = centreZ(i.id) - demi;
        final int x1 = centreX(i.id) + demi;
        final int z1 = centreZ(i.id) + demi;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                w.setBiome(x, z, biome);
            }
        }
        for (int cx = x0 >> 4; cx <= x1 >> 4; cx++) {
            for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) {
                if (w.isChunkLoaded(cx, cz)) {
                    w.refreshChunk(cx, cz);
                }
            }
        }
    }


    // ------------------------------------------------------------------ nettoyage du monde Build

    /**
     * Retire tout ce qui a ete genere dans le monde Build en dehors des iles (structures
     * des mods apparues avant la correction du biome). Les iles ne sont pas touchees.
     */
    public void nettoyerHorsIles(final org.bukkit.command.CommandSender s) {
        final World w = monde();
        if (w == null) {
            return;
        }
        File region = new File(w.getWorldFolder(), "region");
        final List<int[]> chunks = new ArrayList<int[]>();
        File[] fs = region.listFiles();
        if (fs != null) {
            for (File f : fs) {
                String[] p = f.getName().split("\\.");
                if (p.length != 4 || !p[3].equals("mca")) {
                    continue;
                }
                int rx = Integer.parseInt(p[1]);
                int rz = Integer.parseInt(p[2]);
                try {
                    java.io.DataInputStream in = new java.io.DataInputStream(new FileInputStream(f));
                    try {
                        for (int k = 0; k < 1024; k++) {
                            if (in.readInt() != 0) {
                                chunks.add(new int[] {rx * 32 + (k % 32), rz * 32 + (k / 32)});
                            }
                        }
                    } finally {
                        in.close();
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Region illisible : " + f.getName());
                }
            }
        }
        org.bukkit.Location spawn = w.getSpawnLocation();
        final int sx = spawn.getBlockX();
        final int sz = spawn.getBlockZ();
        SSNG.msg(s, "&7Nettoyage du monde Build : " + chunks.size() + " chunks a verifier...");
        new org.bukkit.scheduler.BukkitRunnable() {
            int i = 0;
            long retires = 0;

            public void run() {
                for (int k = 0; k < 4 && i < chunks.size(); k++, i++) {
                    int cx = chunks.get(i)[0];
                    int cz = chunks.get(i)[1];
                    org.bukkit.Chunk ch = w.getChunkAt(cx, cz);
                    org.bukkit.ChunkSnapshot snap = ch.getChunkSnapshot();
                    boolean modifie = false;
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            int bx = (cx << 4) + x;
                            int bz = (cz << 4) + z;
                            if (Math.abs(bx - sx) <= 12 && Math.abs(bz - sz) <= 12) {
                                continue; // plateforme de spawn du monde Build
                            }
                            if (ileA(new Location(w, bx, 64, bz)) != null) {
                                continue;
                            }
                            for (int sy = 0; sy < 16; sy++) {
                                if (snap.isSectionEmpty(sy)) {
                                    continue;
                                }
                                for (int y = sy << 4; y < (sy << 4) + 16; y++) {
                                    if (snap.getBlockTypeId(x, y, z) != 0) {
                                        w.getBlockAt(bx, y, bz).setTypeIdAndData(0, (byte) 0, false);
                                        retires++;
                                        modifie = true;
                                    }
                                }
                            }
                            w.setBiome(bx, bz, Biome.PLAINS);
                        }
                    }
                    if (modifie) {
                        w.refreshChunk(cx, cz);
                    }
                }
                if (i >= chunks.size()) {
                    cancel();
                    SSNG.msg(s, "&aNettoyage termine : " + retires + " blocs retires hors des iles.");
                    plugin.getLogger().info("Nettoyage du monde Build : " + retires + " blocs retires.");
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }
}
