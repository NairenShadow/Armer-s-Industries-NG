package fr.armersindustries.ssng.carte;

import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/** Une zone : un ensemble de chunks avec ses regles (flags), un spawn et un lieu de reapparition. */
public class Zone {

    /** Flags : nom -> {valeur par defaut, description}. */
    public static final Map<String, String[]> FLAGS = new LinkedHashMap<String, String[]>();

    static {
        FLAGS.put("pvp", new String[] {"false", "Combat entre joueurs"});
        FLAGS.put("degats", new String[] {"true", "Degats (chute, feu, lave, mobs...)"});
        FLAGS.put("faim", new String[] {"true", "La faim baisse"});
        FLAGS.put("construire", new String[] {"false", "Les joueurs peuvent poser / casser"});
        FLAGS.put("explosions", new String[] {"false", "Les explosions cassent les blocs"});
        FLAGS.put("feu", new String[] {"false", "Le feu se propage"});
        FLAGS.put("garder-inventaire", new String[] {"false", "On garde son stuff a la mort"});
        FLAGS.put("jeter", new String[] {"true", "Jeter des objets"});
        FLAGS.put("ramasser", new String[] {"true", "Ramasser des objets"});
        FLAGS.put("monstres", new String[] {"false", "Apparition des monstres"});
        FLAGS.put("entree", new String[] {"true", "Les joueurs peuvent entrer"});
        FLAGS.put("kits", new String[] {"false", "Kits autorises (/kits)"});
        FLAGS.put("teleportation", new String[] {"true", "Commandes de TP (/tp, /home, /spawn...)"});
        FLAGS.put("energie-infinie", new String[] {"false", "Machines NG : energie, gaz et petrole infinis"});
        FLAGS.put("vision-nocturne", new String[] {"false", "Tous les joueurs voient dans le noir"});
    }

    public String id;
    public String nom;
    public String couleur = "&e";
    public String spawn;
    public String reapparition;
    /** Zone en volume (prioritaire sur les chunks) : "monde;x1;y1;z1;x2;y2;z2", ou null. */
    public String volume;
    public final Map<String, Boolean> flags = new LinkedHashMap<String, Boolean>();

    public Zone(String id) {
        this.id = id;
        this.nom = id;
        for (Map.Entry<String, String[]> e : FLAGS.entrySet()) {
            flags.put(e.getKey(), Boolean.valueOf(e.getValue()[0]));
        }
    }

    public boolean flag(String f) {
        Boolean b = flags.get(f);
        if (b == null) {
            String[] d = FLAGS.get(f);
            return d != null && Boolean.parseBoolean(d[0]);
        }
        return b.booleanValue();
    }

    public void regler(String f, boolean v) {
        flags.put(f, Boolean.valueOf(v));
    }

    /** Le point est-il dans le volume de la zone ? */
    public boolean dansVolume(Location l) {
        if (volume == null || l == null || l.getWorld() == null) {
            return false;
        }
        if (!volume.equals(volumeLu)) {
            try {
                String[] p = volume.split(";");
                int[] b = new int[6];
                for (int i = 0; i < 6; i++) {
                    b[i] = Integer.parseInt(p[i + 1].trim());
                }
                bornes = b;
                mondeVolume = p[0];
            } catch (Exception e) {
                bornes = null;
            }
            volumeLu = volume;
        }
        if (bornes == null || !mondeVolume.equals(l.getWorld().getName())) {
            return false;
        }
        int x = l.getBlockX(), y = l.getBlockY(), z = l.getBlockZ();
        return x >= bornes[0] && y >= bornes[1] && z >= bornes[2] && x <= bornes[3] && y <= bornes[4] && z <= bornes[5];
    }

    private String volumeLu;
    private String mondeVolume;
    private int[] bornes;

    public Location spawn() {
        return lire(spawn);
    }

    public static String ecrire(Location l) {
        return l.getWorld().getName() + ";" + l.getX() + ";" + l.getY() + ";" + l.getZ() + ";" + l.getYaw() + ";" + l.getPitch();
    }

    public static Location lire(String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        try {
            String[] p = s.split(";");
            World w = Bukkit.getWorld(p[0]);
            if (w == null) {
                return null;
            }
            return new Location(w, Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]),
                    p.length > 4 ? Float.parseFloat(p[4]) : 0f, p.length > 5 ? Float.parseFloat(p[5]) : 0f);
        } catch (Exception e) {
            return null;
        }
    }
}
