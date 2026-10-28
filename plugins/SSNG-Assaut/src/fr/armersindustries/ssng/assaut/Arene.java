package fr.armersindustries.ssng.assaut;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.TreeSet;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import fr.armersindustries.ssng.core.Inventaire;
import fr.armersindustries.ssng.core.SSNG;

/**
 * L'arene de l'assaut, reglee en jeu par les admins (/assautadmin) :
 * monde, champ de bataille (ou tombent la zone de capture et les colis), points d'arrivee
 * (attente, attaquants, defenseurs : plusieurs points ou zones, arrivee au hasard),
 * kits des deux equipes (ton inventaire est copie tel quel, objets NG compris) et regles.
 */
public class Arene implements CommandExecutor {

    public static final List<String> GROUPES = Arrays.asList("attente", "attaquants", "defenseurs");

    private final AssautNG plugin;
    private YamlConfiguration d;
    private final Random hasard = new Random();

    public Arene(AssautNG plugin) {
        this.plugin = plugin;
        d = SSNG.charger(plugin, "arene.yml");
    }

    private void sauver() {
        SSNG.sauver(plugin, "arene.yml", d);
    }

    // ------------------------------------------------------------------ lecture

    public World monde() {
        String n = d.getString("monde", null);
        return n == null ? null : Bukkit.getWorld(n);
    }

    /** Champ {x1, z1, x2, z2} ; par defaut un carre de 100 blocs autour du spawn du monde. */
    public int[] champ() {
        String v = d.getString("champ", null);
        if (v != null) {
            try {
                String[] p = v.split(",");
                int x1 = Integer.parseInt(p[0]), z1 = Integer.parseInt(p[1]), x2 = Integer.parseInt(p[2]), z2 = Integer.parseInt(p[3]);
                return new int[] {Math.min(x1, x2), Math.min(z1, z2), Math.max(x1, x2), Math.max(z1, z2)};
            } catch (Exception e) {
                // champ par defaut
            }
        }
        World w = monde();
        Location s = w == null ? null : w.getSpawnLocation();
        int x = s == null ? 0 : s.getBlockX(), z = s == null ? 0 : s.getBlockZ();
        return new int[] {x - 50, z - 50, x + 50, z + 50};
    }

    public boolean dansArene(Location l) {
        int[] c = champ();
        return l.getWorld() == monde() && l.getX() >= c[0] && l.getX() <= c[2] + 1 && l.getZ() >= c[1] && l.getZ() <= c[3] + 1;
    }

    /** Un point au hasard du groupe ; sinon : camps -> attente -> spawn du monde. */
    public Location point(String groupe) {
        World w = monde();
        if (w == null) {
            return Bukkit.getWorlds().get(0).getSpawnLocation();
        }
        List<String> l = d.getStringList("points." + groupe);
        if (!l.isEmpty()) {
            String v = l.get(hasard.nextInt(l.size()));
            try {
                String[] p;
                if (v.startsWith("zone:")) {
                    p = v.substring(5).split(",");
                    int x1 = Integer.parseInt(p[0]), z1 = Integer.parseInt(p[1]), x2 = Integer.parseInt(p[2]), z2 = Integer.parseInt(p[3]);
                    int x = Math.min(x1, x2) + hasard.nextInt(Math.abs(x2 - x1) + 1);
                    int z = Math.min(z1, z2) + hasard.nextInt(Math.abs(z2 - z1) + 1);
                    w.loadChunk(x >> 4, z >> 4);
                    return new Location(w, x + 0.5, w.getHighestBlockYAt(x, z) + 0.5, z + 0.5);
                }
                p = v.split(",");
                return new Location(w, Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]),
                        p.length > 3 ? Float.parseFloat(p[3]) : 0f, p.length > 4 ? Float.parseFloat(p[4]) : 0f);
            } catch (Exception e) {
                plugin.getLogger().warning("Point invalide " + v);
            }
        }
        if (!groupe.equals("attente")) {
            return point("attente");
        }
        return w.getSpawnLocation().add(0.5, 0, 0.5);
    }

    private File fichierKit(String nom) {
        return new File(plugin.getDataFolder(), "kits" + File.separator + nom.toLowerCase().replaceAll("[^a-z0-9_-]", "") + ".dat");
    }

    /** Donne le kit (inventaire vide avant). Faux si le kit n'existe pas. */
    public boolean donnerKit(Player p, String nom) {
        File f = fichierKit(nom);
        if (!f.exists()) {
            return false;
        }
        Inventaire.vider(p);
        return Inventaire.charger(p, f);
    }

    // ------------------------------------------------------------------ /assautadmin

    private void aide(CommandSender s) {
        SSNG.msg(s, "&6/assautadmin &7- regler l'arene d'assaut :");
        SSNG.msg(s, "&e/assautadmin monde &7: l'arene est dans le monde ou tu es");
        SSNG.msg(s, "&e/assautadmin champ pos1|pos2 &7(ou &echamp <x1> <z1> <x2> <z2>&7) : zone de combat, de capture et des colis");
        SSNG.msg(s, "&e/assautadmin <attente|attaquants|defenseurs> ajouter|zone <x1> <z1> <x2> <z2>|liste|retirer <n>|vider");
        SSNG.msg(s, "&e/assautadmin kit attaquants|defenseurs &7: copie TON inventaire comme kit de l'equipe");
        SSNG.msg(s, "&e/assautadmin regle [cle] [valeur] &7: durees, points, colis, noms des equipes...");
        SSNG.msg(s, "&e/assautadmin voir &7: resume");
    }

    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (!SSNG.admin(s)) {
            SSNG.msg(s, "&cReserve aux admins.");
            return true;
        }
        if (a.length == 0) {
            aide(s);
            return true;
        }
        String q = a[0].toLowerCase();
        Player p = s instanceof Player ? (Player) s : null;
        if (q.equals("voir")) {
            SSNG.msg(s, "&6Arene : &f" + (monde() == null ? "&cpas de monde (/assautadmin monde)" : monde().getName()) + " &7- champ " + Arrays.toString(champ()));
            for (String g : GROUPES) {
                SSNG.msg(s, "&e" + g + " &7: " + d.getStringList("points." + g).size() + " point(s)/zone(s)");
            }
            SSNG.msg(s, "&ekits &7: attaquants " + (fichierKit(plugin.getConfig().getString("assaut.kit-attaquants", "attaquants")).exists() ? "&aOK" : "&cabsent")
                    + " &7- defenseurs " + (fichierKit(plugin.getConfig().getString("assaut.kit-defenseurs", "defenseurs")).exists() ? "&aOK" : "&cabsent"));
            return true;
        }
        if (q.equals("monde") && p != null) {
            d.set("monde", p.getWorld().getName());
            sauver();
            SSNG.msg(s, "&aL'arene d'assaut est dans &e" + p.getWorld().getName() + "&a. Regle maintenant le champ : &e/assautadmin champ pos1");
            return true;
        }
        if (q.equals("champ")) {
            if (a.length >= 5) {
                try {
                    d.set("champ", Integer.parseInt(a[1]) + "," + Integer.parseInt(a[2]) + "," + Integer.parseInt(a[3]) + "," + Integer.parseInt(a[4]));
                    sauver();
                    SSNG.msg(s, "&aChamp regle.");
                } catch (NumberFormatException e) {
                    SSNG.msg(s, "&cCoordonnees invalides.");
                }
                return true;
            }
            if (a.length >= 2 && p != null && (a[1].equalsIgnoreCase("pos1") || a[1].equalsIgnoreCase("pos2"))) {
                d.set("champ-" + a[1].toLowerCase(), p.getLocation().getBlockX() + "," + p.getLocation().getBlockZ());
                if (d.getString("monde", null) == null) {
                    d.set("monde", p.getWorld().getName());
                }
                String p1 = d.getString("champ-pos1", null), p2 = d.getString("champ-pos2", null);
                if (p1 != null && p2 != null) {
                    d.set("champ", p1 + "," + p2);
                    SSNG.msg(s, "&aChamp de bataille : &e" + p1 + " -> " + p2);
                } else {
                    SSNG.msg(s, "&7" + a[1] + " notee. Place l'autre coin.");
                }
                sauver();
                return true;
            }
            SSNG.msg(s, "&c/assautadmin champ pos1|pos2 ou champ <x1> <z1> <x2> <z2>");
            return true;
        }
        if (GROUPES.contains(q)) {
            List<String> l = new ArrayList<String>(d.getStringList("points." + q));
            String act = a.length > 1 ? a[1].toLowerCase() : "liste";
            if (act.equals("ajouter") && p != null) {
                Location x = p.getLocation();
                l.add(Math.round(x.getX() * 10) / 10.0 + "," + Math.round(x.getY() * 10) / 10.0 + "," + Math.round(x.getZ() * 10) / 10.0 + ","
                        + Math.round(x.getYaw()) + "," + Math.round(x.getPitch()));
            } else if (act.equals("zone") && a.length >= 6) {
                try {
                    l.add("zone:" + Integer.parseInt(a[2]) + "," + Integer.parseInt(a[3]) + "," + Integer.parseInt(a[4]) + "," + Integer.parseInt(a[5]));
                } catch (NumberFormatException e) {
                    SSNG.msg(s, "&cCoordonnees invalides.");
                    return true;
                }
            } else if (act.equals("retirer") && a.length >= 3) {
                try {
                    l.remove(Integer.parseInt(a[2]) - 1);
                } catch (Exception e) {
                    SSNG.msg(s, "&cNumero invalide.");
                    return true;
                }
            } else if (act.equals("vider")) {
                l.clear();
            } else {
                SSNG.msg(s, "&6" + q + " &7(" + l.size() + ") :");
                for (int i = 0; i < l.size(); i++) {
                    SSNG.msg(s, "&e" + (i + 1) + ". &f" + l.get(i));
                }
                return true;
            }
            d.set("points." + q, l.isEmpty() ? null : l);
            sauver();
            SSNG.msg(s, "&a" + q + " : " + l.size() + " point(s)/zone(s). &7Arrivee au hasard sur l'un d'eux.");
            return true;
        }
        if (q.equals("kit") && a.length >= 2 && p != null) {
            String camp = a[1].toLowerCase().startsWith("att") ? "attaquants" : "defenseurs";
            String nom = plugin.getConfig().getString("assaut.kit-" + camp, camp);
            boolean ok = Inventaire.sauver(p, fichierKit(nom));
            SSNG.msg(s, ok ? "&aKit des " + camp + " enregistre (ton inventaire et ton armure)." : "&cEnregistrement impossible.");
            return true;
        }
        if (q.equals("regle") || q.equals("regles")) {
            return regle(s, a);
        }
        aide(s);
        return true;
    }

    // ------------------------------------------------------------------ regles (config.yml, section assaut)

    public List<String> cles() {
        TreeSet<String> r = new TreeSet<String>();
        for (ConfigurationSection cs : new ConfigurationSection[] {plugin.getConfig().getConfigurationSection("assaut"),
                plugin.getConfig().getDefaults() == null ? null : plugin.getConfig().getDefaults().getConfigurationSection("assaut")}) {
            if (cs == null) {
                continue;
            }
            for (String k : cs.getKeys(true)) {
                if (!cs.isConfigurationSection(k)) {
                    r.add("assaut." + k);
                }
            }
        }
        return new ArrayList<String>(r);
    }

    private boolean regle(CommandSender s, String[] a) {
        if (a.length == 1) {
            SSNG.msg(s, "&6Regles de l'assaut &7(/assautadmin regle <cle> <valeur>) :");
            for (String k : cles()) {
                SSNG.msg(s, "&e" + k + " &7= &f" + plugin.getConfig().get(k));
            }
            return true;
        }
        String k = a[1].toLowerCase();
        if (!k.startsWith("assaut.")) {
            k = "assaut." + k;
        }
        if (!cles().contains(k)) {
            SSNG.msg(s, "&cCle inconnue. &7/assautadmin regle pour la liste.");
            return true;
        }
        if (a.length < 3) {
            SSNG.msg(s, "&e" + k + " &7= &f" + plugin.getConfig().get(k));
            return true;
        }
        StringBuilder v = new StringBuilder();
        for (int i = 2; i < a.length; i++) {
            v.append(i > 2 ? " " : "").append(a[i]);
        }
        Object ancien = plugin.getConfig().get(k);
        Object nouveau;
        String t = v.toString();
        try {
            if (ancien instanceof Boolean) {
                nouveau = Boolean.valueOf(t.equalsIgnoreCase("true") || t.equalsIgnoreCase("oui") || t.equals("1"));
            } else if (ancien instanceof Integer || ancien instanceof Long) {
                nouveau = Integer.valueOf(t);
            } else if (ancien instanceof Double) {
                nouveau = Double.valueOf(t);
            } else if (ancien instanceof List) {
                List<Object> l = new ArrayList<Object>();
                for (String x : t.replace("[", "").replace("]", "").split(",")) {
                    String y = x.trim();
                    if (y.isEmpty()) {
                        continue;
                    }
                    try {
                        l.add(Integer.valueOf(y));
                    } catch (NumberFormatException e) {
                        l.add(y);
                    }
                }
                nouveau = l;
            } else {
                nouveau = t;
            }
        } catch (NumberFormatException e) {
            SSNG.msg(s, "&cValeur invalide (actuelle : " + ancien + ").");
            return true;
        }
        plugin.getConfig().set(k, nouveau);
        plugin.saveConfig();
        SSNG.msg(s, "&a" + k + " &7: &f" + ancien + " &7-> &a" + nouveau);
        return true;
    }
}
