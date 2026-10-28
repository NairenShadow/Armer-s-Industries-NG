package fr.armersindustries.ssng.core;

import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;

/**
 * Elements de jeu de NationsGlory utilises par l'assaut :
 * - la zone d'assaut (carre transparent a lignes rouges du mod NGUpgrades, synchronisee par le mod avec les joueurs du monde) ;
 * - le colis d'assaut (entite "carepackage" de NGContent) qui tombe du ciel.
 */
public final class ObjetsNG {

    private ObjetsNG() {
    }

    /** Colis poses par le plugin (le mod NG les supprime s'il ne trouve pas d'assaut a leur position). */
    public static final java.util.List<Entity> COLIS = new java.util.concurrent.CopyOnWriteArrayList<Entity>();

    /** Un colis du plugin se trouve-t-il a cette position ? (appele par NGCompat a la place de NationsBattle) */
    public static boolean colisA(int x, int z) {
        for (Entity e : COLIS) {
            if (e.isDead() || !e.isValid()) {
                COLIS.remove(e);
                continue;
            }
            if (Math.abs(e.getLocation().getX() - x) <= 4 && Math.abs(e.getLocation().getZ() - z) <= 4) {
                return true;
            }
        }
        return false;
    }

    private static ClassLoader cl() {
        return Bukkit.getServer().getClass().getClassLoader();
    }

    private static Object nms(World w) throws Exception {
        return w.getClass().getMethod("getHandle").invoke(w);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> bordures(World w) throws Exception {
        Class<?> ext = Class.forName("fr.nationsglory.ngupgrades.common.world.WorldExtension", true, cl());
        Object e = ext.getMethod("get", Class.forName("net.minecraft.world.World", true, cl())).invoke(null, nms(w));
        return (Map<String, Object>) ext.getMethod("getWorldBorders").invoke(e);
    }

    /** Affiche (ou deplace) un carre de zone : centre, demi-largeurs, hauteur au-dessus / en dessous de y. */
    public static boolean zone(World w, String cle, double cx, double cz, double demiX, double demiZ, int y, int haut, int bas, int couleur) {
        try {
            Class<?> bord = Class.forName("fr.nationsglory.ngupgrades.common.world.WorldBorder", true, cl());
            Object b = bord.getConstructor(int.class, int.class, int.class, int.class, int.class, int.class, int.class, int.class, String.class)
                    .newInstance((int) Math.round(cx - demiX), (int) Math.round(cz - demiZ), (int) Math.round(cx + demiX), (int) Math.round(cz + demiZ),
                            y, haut, bas, couleur, "false");
            bordures(w).put("shadow_" + cle, b);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static void retirerZone(World w, String cle) {
        try {
            bordures(w).remove("shadow_" + cle);
        } catch (Throwable t) {
            // NGUpgrades absent
        }
    }

    /** Fait tomber un colis NG (nom de modele + points de vie) ; renvoie l'entite Bukkit, ou null. */
    public static Entity colis(Location l, String nom) {
        return colis(l, nom, 500);
    }

    public static Entity colis(Location l, String nom, double pv) {
        try {
            Class<?> cMonde = Class.forName("net.minecraft.world.World", true, cl());
            Class<?> cEntite = Class.forName("net.minecraft.entity.Entity", true, cl());
            Object monde = nms(l.getWorld());
            l.getWorld().loadChunk(l.getBlockX() >> 4, l.getBlockZ() >> 4);
            Object e = null;
            // Le colis NG doit etre construit avec son nom de modele (sinon il est invisible) et ses PV
            try {
                Class<?> cColis = Class.forName("fr.nationsglory.ngupgrades.common.entity.CarePackageEntity", true, cl());
                e = cColis.getConstructor(cMonde, String.class, double.class).newInstance(monde, "care_package", Double.valueOf(pv));
            } catch (Throwable t) {
                Bukkit.getLogger().warning("[SSNG] Colis : constructeur NG indisponible (" + t + "), essai par nom d'entite.");
            }
            if (e == null) {
                e = Class.forName("net.minecraft.entity.EntityList", true, cl()).getMethod("func_75620_a", String.class, cMonde)
                        .invoke(null, nom, monde);
            }
            for (String alt : new String[] {"care_package", "carepackage"}) {
                if (e == null && !alt.equals(nom)) {
                    e = Class.forName("net.minecraft.entity.EntityList", true, cl()).getMethod("func_75620_a", String.class, cMonde)
                            .invoke(null, alt, monde);
                }
            }
            if (e == null) {
                Bukkit.getLogger().warning("[SSNG] Colis : entite inconnue " + nom);
                return null;
            }
            cEntite.getMethod("func_70012_b", double.class, double.class, double.class, float.class, float.class)
                    .invoke(e, l.getX(), l.getY(), l.getZ(), 0f, 0f);
            Object ok = null;
            for (java.lang.reflect.Method m : cMonde.getMethods()) {
                Class<?>[] p = m.getParameterTypes();
                if (p.length == 2 && p[0] == cEntite && p[1] == org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.class) {
                    ok = m.invoke(monde, e, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CUSTOM);
                }
            }
            if (ok == null) {
                ok = cMonde.getMethod("func_72838_d", cEntite).invoke(monde, e);
            }
            if (!Boolean.TRUE.equals(ok)) {
                Bukkit.getLogger().warning("[SSNG] Colis refuse par le monde.");
                return null;
            }
            Entity be = (Entity) e.getClass().getMethod("getBukkitEntity").invoke(e);
            COLIS.add(be);
            return be;
        } catch (Throwable t) {
            Bukkit.getLogger().warning("[SSNG] Colis impossible : " + (t.getCause() != null ? t.getCause() : t));
            return null;
        }
    }
}
