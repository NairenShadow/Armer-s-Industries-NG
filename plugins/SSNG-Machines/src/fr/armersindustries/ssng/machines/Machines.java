package fr.armersindustries.ssng.machines;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import fr.armersindustries.ssng.core.SSNG;
import fr.armersindustries.ssng.core.ReseauNG;
import fr.armersindustries.ssng.carte.Zone;

/**
 * Machines NationsGlory : reponses aux paquets que le plugin prive "NationsGUI"
 * du serveur officiel traitait (petrolier, extracteur de gaz, reservoir, silo...),
 * energie et ressources infinies par zone, et vitrine de toutes les machines
 * dans le monde Test (/machines vitrine).
 */
public class Machines implements CommandExecutor {

    /**
     * Machines de la vitrine en plus de la liste officielle de NationsGlory
     * (ngcore_cache/machines.json) : chaine du gaz et du nucleaire, energie, ICBM...
     * {id, meta}
     */
    static final int[][] EXTRA = {
        // Energie
        {3382, 0}, {3382, 4}, {3696, 12}, {3363, 0}, {3363, 4}, {3364, 12}, {3571, 0}, {3588, 0}, {3574, 0}, {3570, 0},
        // Petrole et gaz
        {3660, 0}, {3662, 0}, {3634, 0}, {3692, 12}, {3692, 0}, {3692, 4}, {3692, 8}, {3693, 0}, {3695, 12}, {3359, 0},
        {3326, 0}, {3324, 0}, {3655, 0},
        // Nucleaire
        {3693, 8}, {3693, 12}, {3694, 0}, {3694, 4}, {3429, 0}, {3694, 8}, {3694, 12}, {3695, 0}, {3695, 4}, {3695, 8},
        {3696, 0}, {3696, 4}, {3697, 0}, {3697, 4}, {3435, 0}, {3696, 8},
        // Production
        {3363, 8}, {3363, 12}, {3364, 0}, {3595, 0}, {3597, 0}, {3575, 0}, {3651, 0}, {3650, 0}, {3572, 0}, {3576, 0},
        {3578, 0}, {3693, 4}, {3573, 0}, {3598, 0}, {3652, 0}, {3653, 0}, {3654, 0}, {3584, 0}, {3586, 0},
        // ICBM
        {3893, 0}, {3893, 3}, {3893, 4}, {3893, 5}, {3893, 6}, {3894, 0}
    };

    /** Liste de la vitrine : machines officielles NG + EXTRA, sans les blocs que NG masque. */
    List<int[]> listeVitrine() {
        List<int[]> l = new ArrayList<int[]>();
        java.util.Set<String> vus = new java.util.HashSet<String>();
        java.util.Set<String> masques = new java.util.HashSet<String>();
        java.io.File racine = new java.io.File(".");
        try {
            java.io.File bl = new java.io.File(racine, "config" + java.io.File.separator + "ngcontent_item_blacklist.json");
            if (bl.exists()) {
                com.google.gson.JsonObject o = new com.google.gson.JsonParser().parse(new String(java.nio.file.Files.readAllBytes(bl.toPath()), "UTF-8")).getAsJsonObject();
                if (o.has("itemBlacklist")) {
                    for (Map.Entry<String, com.google.gson.JsonElement> e : o.getAsJsonObject("itemBlacklist").entrySet()) {
                        for (com.google.gson.JsonElement id : e.getValue().getAsJsonArray()) {
                            masques.add(id.getAsInt() + ":*");
                        }
                    }
                }
                if (o.has("hiddenMetas")) {
                    for (com.google.gson.JsonElement m : o.getAsJsonArray("hiddenMetas")) {
                        masques.add(m.getAsString());
                    }
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().fine("Blacklist NG : " + t);
        }
        List<int[]> candidats = new ArrayList<int[]>();
        try {
            java.io.File mj = new java.io.File(racine, "ngcore_cache" + java.io.File.separator + "machines.json");
            if (mj.exists()) {
                com.google.gson.JsonObject o = new com.google.gson.JsonParser().parse(new String(java.nio.file.Files.readAllBytes(mj.toPath()), "UTF-8")).getAsJsonObject();
                for (com.google.gson.JsonElement m : o.getAsJsonArray("machines")) {
                    com.google.gson.JsonObject mo = m.getAsJsonObject();
                    if (mo.has("block_id") && !mo.get("block_id").isJsonNull()) {
                        candidats.add(new int[] {mo.get("block_id").getAsInt(), 0});
                    }
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("machines.json de NG illisible : " + t);
        }
        for (int[] e : EXTRA) {
            candidats.add(e);
        }
        for (int[] e : candidats) {
            String k = e[0] + ":" + e[1];
            if (vus.contains(k) || masques.contains(k) || masques.contains(e[0] + ":*")) {
                continue;
            }
            vus.add(k);
            l.add(e);
        }
        return l;
    }

    static final int VITRINE_X = 2000;
    static final int VITRINE_Z = 2000;
    static final int ESPACE = 12;
    static final int COLONNES = 10;

    private final MachinesNG plugin;
    private final ReseauNG reseau;

    public Machines(MachinesNG plugin, ReseauNG reseau) {
        this.plugin = plugin;
        this.reseau = reseau;
    }

    // ------------------------------------------------------------------ paquets

    public void demarrer() {
        reseau.ajouter("PumpjackInfosMachinePacket", new ReseauNG.Recepteur() {
            public void recu(Player p, String paquet, DataInputStream in) throws Exception {
                final boolean ok = peutUtiliser(p, in.readInt(), in.readInt(), in.readInt());
                final String zone = nomZone(p);
                final double reste = plugin.getConfig().getDouble("machines.petrole-zone", 1.0);
                reseau.envoyer(p, paquet, new ReseauNG.Ecrivain() {
                    public void ecrire(DataOutputStream out) throws Exception {
                        out.writeUTF(zone);
                        out.writeDouble(reste);
                        out.writeBoolean(ok);
                    }
                });
            }
        });
        reseau.ajouter("GasExtractorInfosMachinePacket", new ReseauNG.Recepteur() {
            public void recu(Player p, String paquet, DataInputStream in) throws Exception {
                final boolean ok = peutUtiliser(p, in.readInt(), in.readInt(), in.readInt());
                final String zone = nomZone(p);
                final float reste = (float) plugin.getConfig().getDouble("machines.gaz-zone", 1.0);
                reseau.envoyer(p, paquet, new ReseauNG.Ecrivain() {
                    public void ecrire(DataOutputStream out) throws Exception {
                        out.writeUTF(zone);
                        out.writeFloat(reste);
                        out.writeBoolean(ok);
                    }
                });
            }
        });
        reseau.ajouter("PumpjackActiveMachinePacket", new ReseauNG.Recepteur() {
            public void recu(Player p, String paquet, DataInputStream in) throws Exception {
                int x = in.readInt();
                int y = in.readInt();
                int z = in.readInt();
                boolean actif = in.readBoolean();
                if (peutUtiliser(p, x, y, z)) {
                    Object te = tuile(p.getWorld(), x, y, z);
                    if (te != null && ecrireChamp(te, "active", Boolean.valueOf(actif))) {
                        maj(p.getWorld(), x, y, z);
                    }
                }
            }
        });
        // Fenetres d'entreprises (citerne, accumulateur, silo, generateur) : voir Entreprises
        reseau.ajouter("CheckAccessMachinePacket", new ReseauNG.Recepteur() {
            public void recu(Player p, String paquet, DataInputStream in) {
                reseau.envoyer(p, paquet, new ReseauNG.Ecrivain() {
                    public void ecrire(DataOutputStream out) throws Exception {
                        out.writeBoolean(true);
                    }
                });
            }
        });
        // energie / ressources infinies dans les zones marquees
        Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            public void run() {
                remplir();
            }
        }, 100L, 40L);
    }

    private String nomZone(Player p) {
        Zone z = plugin.zones() == null ? null : plugin.zones().zoneA(p.getLocation());
        if (z != null) {
            return SSNG.c(z.nom);
        }
        MachinesNG.TypeMonde t = plugin.type(p.getWorld());
        return plugin.getConfig().getString("machines.nom-zone", t == MachinesNG.TypeMonde.TEST ? "Zone de test" : "Zone libre");
    }

    /** Le joueur peut-il utiliser la machine ? (iles : permission "interact" ; ailleurs : oui, sauf zone fermee). */
    private boolean peutUtiliser(Player p, int x, int y, int z) {
        if (SSNG.admin(p)) {
            return true;
        }
        Location l = new Location(p.getWorld(), x, y, z);
        if (plugin.type(p.getWorld()) == MachinesNG.TypeMonde.BUILD) {
            return plugin.peutInteragirIle(p, l);
        }
        return p.getLocation().distance(l) < 12;
    }

    // ------------------------------------------------------------------ reflexion

    private static Method mGetTuile;
    private static Method mMaj;
    private static Field fTuiles;
    private static Field fX;
    private static Field fY;
    private static Field fZ;
    private static final Map<Class<?>, Object[]> CACHE = new HashMap<Class<?>, Object[]>();

    private static void preparer() throws Exception {
        if (mGetTuile != null) {
            return;
        }
        ClassLoader cl = Bukkit.getServer().getClass().getClassLoader();
        Class<?> cMonde = Class.forName("net.minecraft.world.World", true, cl);
        Class<?> cTuile = Class.forName("net.minecraft.tileentity.TileEntity", true, cl);
        mGetTuile = cMonde.getMethod("func_72796_p", int.class, int.class, int.class);
        mMaj = cMonde.getMethod("func_72845_h", int.class, int.class, int.class);
        fTuiles = cMonde.getField("field_73009_h");
        fX = cTuile.getField("field_70329_l");
        fY = cTuile.getField("field_70330_m");
        fZ = cTuile.getField("field_70327_n");
    }

    static Object monde(World w) throws Exception {
        return w.getClass().getMethod("getHandle").invoke(w);
    }

    private Object tuile(World w, int x, int y, int z) {
        try {
            preparer();
            return mGetTuile.invoke(monde(w), x, y, z);
        } catch (Throwable t) {
            return null;
        }
    }

    private void maj(World w, int x, int y, int z) {
        try {
            mMaj.invoke(monde(w), x, y, z);
        } catch (Throwable t) {
            // pas grave
        }
    }

    private static Field champ(Class<?> c, String nom) {
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            try {
                Field f = k.getDeclaredField(nom);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException e) {
                // classe parente
            }
        }
        return null;
    }

    private static boolean ecrireChamp(Object o, String nom, Object v) {
        Field f = champ(o.getClass(), nom);
        if (f == null) {
            return false;
        }
        try {
            f.set(o, v);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** {setEnergyStored, getMaxEnergyStored, storedGas, maxGas, storedPetrol, maxPetrol} pour une classe. */
    private static Object[] acces(Class<?> c) {
        Object[] a = CACHE.get(c);
        if (a != null) {
            return a;
        }
        a = new Object[6];
        try {
            a[0] = c.getMethod("setEnergyStored", float.class);
            a[1] = c.getMethod("getMaxEnergyStored");
        } catch (Exception e) {
            a[0] = null;
        }
        a[2] = champ(c, "storedGas");
        a[3] = champ(c, "maxGas");
        a[4] = champ(c, "storedPetrol");
        a[5] = champ(c, "maxPetrol");
        CACHE.put(c, a);
        return a;
    }

    /** Remplit l'energie (et gaz / petrole) des machines des zones "energie-infinie". */
    private void remplir() {
        boolean auMoinsUne = false;
        if (plugin.zones() == null) {
            return;
        }
        for (Zone z : plugin.zones().toutes()) {
            if (z.flag("energie-infinie")) {
                auMoinsUne = true;
                break;
            }
        }
        if (!auMoinsUne) {
            return;
        }
        try {
            preparer();
            for (World w : Bukkit.getWorlds()) {
                List<?> tuiles = new ArrayList<Object>((List<?>) fTuiles.get(monde(w)));
                for (Object te : tuiles) {
                    int x = fX.getInt(te);
                    int y = fY.getInt(te);
                    int z = fZ.getInt(te);
                    if (!plugin.zones().flag(new Location(w, x, y, z), "energie-infinie", false)) {
                        continue;
                    }
                    Object[] a = acces(te.getClass());
                    if (a[0] != null) {
                        float max = ((Number) ((Method) a[1]).invoke(te)).floatValue();
                        ((Method) a[0]).invoke(te, Float.valueOf(max));
                    }
                    if (a[2] != null && a[3] != null) {
                        ((Field) a[2]).set(te, ((Field) a[3]).get(te));
                    }
                    if (a[4] != null && a[5] != null) {
                        ((Field) a[4]).set(te, ((Field) a[5]).get(te));
                    }
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().fine("Energie infinie : " + t);
        }
    }

    // ------------------------------------------------------------------ vitrine

    /** Construit la vitrine dans le monde Test : une machine posee par case, avec un panneau. */
    public void vitrine(final CommandSender s) {
        final World w = plugin.monde(MachinesNG.TypeMonde.TEST);
        if (w == null) {
            SSNG.msg(s, "&cMonde Test absent.");
            return;
        }
        final Object joueurFictif;
        final Method placer;
        final java.lang.reflect.Constructor<?> nouveauStack;
        final Field fYaw;
        try {
            ClassLoader cl = Bukkit.getServer().getClass().getClassLoader();
            Class<?> cMonde = Class.forName("net.minecraft.world.World", true, cl);
            Class<?> cStack = Class.forName("net.minecraft.item.ItemStack", true, cl);
            Class<?> cJoueur = Class.forName("net.minecraft.entity.player.EntityPlayer", true, cl);
            Class<?> usine = Class.forName("net.minecraftforge.common.FakePlayerFactory", true, cl);
            joueurFictif = usine.getMethod("get", cMonde, String.class).invoke(null, monde(w), "ShadowVitrine");
            placer = cStack.getMethod("func_77943_a", cJoueur, cMonde, int.class, int.class, int.class, int.class, float.class, float.class, float.class);
            nouveauStack = cStack.getConstructor(int.class, int.class, int.class);
            fYaw = Class.forName("net.minecraft.entity.Entity", true, cl).getField("field_70177_z");
        } catch (Throwable t) {
            SSNG.msg(s, "&cVitrine impossible : " + t);
            return;
        }
        final List<int[]> liste = listeVitrine();
        final int lignes = (liste.size() + COLONNES - 1) / COLONNES;
        final int x1 = VITRINE_X - 4;
        final int z1 = VITRINE_Z - 6;
        final int x2 = VITRINE_X + (COLONNES - 1) * ESPACE + 4;
        final int z2 = VITRINE_Z + (lignes - 1) * ESPACE + 4;
        // zone avec energie infinie, construction libre
        if (plugin.zones() == null) {
            SSNG.msg(s, "&cLa vitrine a besoin de SSNG-Carte (zones).");
            return;
        }
        Zone zone = plugin.zones().creer("vitrine-machines");
        zone.nom = "Vitrine des machines";
        zone.couleur = "&b";
        zone.regler("energie-infinie", true);
        zone.regler("construire", true);
        zone.regler("pvp", false);
        zone.regler("explosions", false);
        zone.regler("degats", false);
        zone.regler("faim", false);
        zone.regler("garder-inventaire", true);
        zone.spawn = Zone.ecrire(new Location(w, VITRINE_X + 0.5, 4, z1 + 1.5, 0f, 0f));
        plugin.zones().claimRectangle(zone, w, x1, z1, x2, z2);
        plugin.zones().sauver();
        SSNG.msg(s, "&7Construction de la vitrine (" + liste.size() + " machines)...");
        new org.bukkit.scheduler.BukkitRunnable() {
            int i = -1;
            int posees = 0;
            final List<String> echecs = new ArrayList<String>();

            @SuppressWarnings("deprecation")
            public void run() {
                try {
                    if (i < 0) {
                        // dalle propre
                        for (int x = x1; x <= x2; x++) {
                            for (int z = z1; z <= z2; z++) {
                                w.getBlockAt(x, 3, z).setTypeIdAndData(x == x1 || x == x2 || z == z1 || z == z2 ? 89 : 43, (byte) 0, false);
                                for (int y = 4; y <= 24; y++) {
                                    if (w.getBlockTypeIdAt(x, y, z) != 0) {
                                        w.getBlockAt(x, y, z).setTypeIdAndData(0, (byte) 0, false);
                                    }
                                }
                            }
                        }
                        i = 0;
                        return;
                    }
                    for (int k = 0; k < 3 && i < liste.size(); k++, i++) {
                        int id = liste.get(i)[0];
                        int meta = liste.get(i)[1];
                        int x = VITRINE_X + (i % COLONNES) * ESPACE;
                        int z = VITRINE_Z + (i / COLONNES) * ESPACE;
                        fYaw.setFloat(joueurFictif, 180f); // face au nord (vers l'allee)
                        Object stack = nouveauStack.newInstance(id, 1, meta);
                        boolean ok;
                        try {
                            ok = Boolean.TRUE.equals(placer.invoke(stack, joueurFictif, monde(w), x, 3, z, 1, 0.5f, 1f, 0.5f));
                        } catch (java.lang.reflect.InvocationTargetException ex) {
                            ok = false; // bloc sans objet enregistre dans ce pack de mods
                        }
                        Block pose = w.getBlockAt(x, 4, z);
                        String nom = nom(id, meta);
                        if (!ok || pose.getTypeId() == 0) {
                            echecs.add(id + ":" + meta);
                        } else {
                            posees++;
                        }
                        // panneau devant
                        Block b = w.getBlockAt(x, 4, z - 3);
                        if (b.getTypeId() != 0 || tuile(w, x, 4, z - 3) != null) {
                            continue; // une grande machine deborde ici : pas de panneau
                        }
                        b.setTypeIdAndData(63, (byte) 8, false);
                        if (b.getState() instanceof Sign) {
                            Sign sg = (Sign) b.getState();
                            String[] l = decouper(nom);
                            sg.setLine(0, SSNG.c("&1" + l[0]));
                            sg.setLine(1, l[1]);
                            sg.setLine(2, l[2]);
                            sg.setLine(3, SSNG.c((ok ? "&8" : "&4") + id + ":" + meta));
                            sg.update(true, false);
                        }
                    }
                    if (i >= liste.size()) {
                        cancel();
                        SSNG.msg(s, "&aVitrine prete : " + posees + "/" + liste.size() + " machines posees"
                                + (echecs.isEmpty() ? "." : ", non posees : " + echecs) + " &7Energie infinie dans la zone. &f/machines tp");
                        plugin.getLogger().info("Vitrine des machines : " + posees + "/" + liste.size() + (echecs.isEmpty() ? "" : " (echecs " + echecs + ")"));
                    }
                } catch (Throwable t) {
                    cancel();
                    SSNG.msg(s, "&cErreur pendant la vitrine : " + t);
                    plugin.getLogger().log(java.util.logging.Level.WARNING, "Vitrine", t);
                }
            }
        }.runTaskTimer(plugin, 1L, 2L);
    }

    private static String nom(int id, int meta) {
        try {
            ClassLoader cl = Bukkit.getServer().getClass().getClassLoader();
            Class<?> cStack = Class.forName("net.minecraft.item.ItemStack", true, cl);
            Object st = cStack.getConstructor(int.class, int.class, int.class).newInstance(id, 1, meta);
            return String.valueOf(cStack.getMethod("func_82833_r").invoke(st));
        } catch (Throwable t) {
            return "Machine " + id;
        }
    }

    private static String[] decouper(String nom) {
        String[] r = {"", "", ""};
        String[] mots = nom.split(" ");
        int ligne = 0;
        for (String m : mots) {
            if (ligne > 2) {
                break;
            }
            int lim = ligne == 0 ? 13 : 15;
            if (r[ligne].length() + m.length() + (r[ligne].isEmpty() ? 0 : 1) > lim && !r[ligne].isEmpty()) {
                ligne++;
                if (ligne > 2) {
                    break;
                }
            }
            r[ligne] = (r[ligne].isEmpty() ? "" : r[ligne] + " ") + m;
            if (r[ligne].length() > (ligne == 0 ? 13 : 15)) {
                r[ligne] = r[ligne].substring(0, ligne == 0 ? 13 : 15);
            }
        }
        return r;
    }

    // ------------------------------------------------------------------ /machines

    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        String sous = a.length == 0 ? "aide" : a[0].toLowerCase();
        if (sous.equals("tp") && s instanceof Player) {
            Zone z = plugin.zones() == null ? null : plugin.zones().zone("vitrine-machines");
            if (z == null || z.spawn() == null) {
                SSNG.msg(s, "&cLa vitrine n'est pas encore construite (&f/machines vitrine&c).");
            } else if (!s.hasPermission("ssng.test")) {
                SSNG.msg(s, "&cReserve aux testeurs.");
            } else {
                ((Player) s).teleport(z.spawn());
            }
            return true;
        }
        if (!SSNG.admin(s)) {
            SSNG.msg(s, "&c/machines tp &7: aller a la vitrine des machines");
            return true;
        }
        if (sous.equals("vitrine")) {
            vitrine(s);
            return true;
        }
        if (sous.equals("liste")) {
            try {
                java.io.File f = new java.io.File(plugin.getDataFolder(), "blocs-a-tuile.txt");
                int n = Registre.exporter(plugin.monde(MachinesNG.TypeMonde.TEST), f);
                SSNG.msg(s, "&a" + n + " blocs a tile entity ecrits dans " + f.getPath());
            } catch (Throwable t) {
                SSNG.msg(s, "&cErreur : " + t);
            }
            return true;
        }
        SSNG.msg(s, "&6/machines tp &7: aller a la vitrine (testeurs)");
        SSNG.msg(s, "&6/machines vitrine &7: (re)construit la vitrine de toutes les machines NG dans le monde Test");
        SSNG.msg(s, "&6/machines liste &7: exporte la liste des blocs a tile entity (id, nom)");
        SSNG.msg(s, "&7Energie infinie : &f/zone flag <zone> energie-infinie on");
        return true;
    }
}
