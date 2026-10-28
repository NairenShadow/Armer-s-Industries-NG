package fr.armersindustries.ssng.ressources;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import fr.armersindustries.ssng.core.SSNG;

/**
 * SSNG-Ressources : gisements de petrole et de gaz par continent / region.
 * Les machines NG (petrolier, extracteur de gaz...) demandent au serveur combien elles peuvent extraire
 * a leur position ; SSNG-Ponts transmet la question ici.
 * Sans reglage : partout 1.0 (gisement normal). Une region a 0 = pas de gisement.
 */
public class Ressources extends JavaPlugin {

    static final String[] TYPES = {"petrole", "gaz"};
    private YamlConfiguration donnees;

    @Override
    public void onEnable() {
        donnees = SSNG.charger(this, "gisements.yml");
        getLogger().info(regions().size() + " region(s) de gisements.");
    }

    private void sauver() {
        SSNG.sauver(this, "gisements.yml", donnees);
    }

    static final class Region {
        String id, monde, nom;
        int x1, z1, x2, z2;
        double petrole = 1, gaz = 1;

        long aire() {
            return (long) (x2 - x1 + 1) * (z2 - z1 + 1);
        }

        boolean contient(String m, int x, int z) {
            return monde.equalsIgnoreCase(m) && x >= x1 && x <= x2 && z >= z1 && z <= z2;
        }

        double valeur(String type) {
            return type.equals("gaz") ? gaz : petrole;
        }
    }

    private List<Region> regions() {
        List<Region> l = new ArrayList<Region>();
        ConfigurationSection s = donnees.getConfigurationSection("regions");
        if (s == null) {
            return l;
        }
        for (String id : s.getKeys(false)) {
            ConfigurationSection r = s.getConfigurationSection(id);
            if (r == null || !r.isSet("x1")) {
                continue;
            }
            Region g = new Region();
            g.id = id;
            g.nom = r.getString("nom", id);
            g.monde = r.getString("monde", "");
            g.x1 = Math.min(r.getInt("x1"), r.getInt("x2"));
            g.x2 = Math.max(r.getInt("x1"), r.getInt("x2"));
            g.z1 = Math.min(r.getInt("z1"), r.getInt("z2"));
            g.z2 = Math.max(r.getInt("z1"), r.getInt("z2"));
            g.petrole = r.getDouble("petrole", 1);
            g.gaz = r.getDouble("gaz", 1);
            l.add(g);
        }
        return l;
    }

    /** La plus petite region qui contient ce point (une ville dans un continent l'emporte sur le continent). */
    private Region regionA(String monde, int x, int z) {
        Region best = null;
        for (Region r : regions()) {
            if (r.contient(monde, x, z) && (best == null || r.aire() < best.aire())) {
                best = r;
            }
        }
        return best;
    }

    /**
     * Appele par SSNG-Ponts (par reflexion) : coefficient du gisement a cet endroit, ou null pour la valeur par defaut.
     * @param type "petrole" ou "gaz" ; @param monde nom du monde (ou du joueur, selon le mod)
     */
    public Double gisement(String type, String monde, int x, int z) {
        String m = monde;
        Player p = monde == null ? null : getServer().getPlayerExact(monde);
        if (p != null) {
            m = p.getWorld().getName();
        }
        Region r = m == null ? null : regionA(m, x, z);
        if (r != null) {
            return Double.valueOf(r.valeur(type));
        }
        return donnees.isSet("defaut." + type) ? Double.valueOf(donnees.getDouble("defaut." + type)) : null;
    }

    // ------------------------------------------------------------------ /gisement

    private static double nombre(String s) {
        return Double.parseDouble(s.replace(',', '.'));
    }

    private void aide(CommandSender s) {
        SSNG.msg(s, "&6/gisement &7- petrole et gaz par continent ou region (1 = normal, 0 = rien, 2 = double) :");
        SSNG.msg(s, "&e/gisement ici &7: ce que donne l'endroit ou tu es");
        SSNG.msg(s, "&e/gisement creer <id> <petrole> <gaz> [nom] &7puis &e/gisement pos1|pos2 <id> &7(coins, a ta position)");
        SSNG.msg(s, "&e/gisement zone <id> <x1> <z1> <x2> <z2> &7: coins par coordonnees (monde ou tu es)");
        SSNG.msg(s, "&e/gisement regler <id> <petrole> <gaz> &8| &e/gisement supprimer <id> &8| &e/gisement liste");
        SSNG.msg(s, "&e/gisement defaut <petrole> <gaz> &7: hors des regions (sans reglage : 1 et 1)");
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (a.length == 0) {
            aide(s);
            return true;
        }
        String q = a[0].toLowerCase(Locale.ROOT);
        if (q.equals("ici") && s instanceof Player) {
            Player p = (Player) s;
            Region r = regionA(p.getWorld().getName(), p.getLocation().getBlockX(), p.getLocation().getBlockZ());
            Double pe = gisement("petrole", p.getWorld().getName(), p.getLocation().getBlockX(), p.getLocation().getBlockZ());
            Double ga = gisement("gaz", p.getWorld().getName(), p.getLocation().getBlockX(), p.getLocation().getBlockZ());
            SSNG.msg(s, "&6Ici : &f" + (r == null ? "hors region" : r.nom + " &7(" + r.id + ")") + " &7- petrole &e" + (pe == null ? "1.0" : pe)
                    + " &7- gaz &e" + (ga == null ? "1.0" : ga));
            return true;
        }
        if (q.equals("liste")) {
            List<Region> l = regions();
            SSNG.msg(s, "&6Regions de gisements : " + (l.isEmpty() ? "&7aucune (partout : gisement normal)" : ""));
            for (Region r : l) {
                SSNG.msg(s, "&e" + r.id + " &7(" + r.nom + ") " + r.monde + " " + r.x1 + "," + r.z1 + " -> " + r.x2 + "," + r.z2
                        + " &7: petrole &f" + r.petrole + " &7gaz &f" + r.gaz);
            }
            return true;
        }
        if (!SSNG.admin(s)) {
            SSNG.msg(s, "&cReserve aux admins.");
            return true;
        }
        try {
            if (q.equals("creer") && a.length >= 4) {
                String id = a[1].toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
                String b = "regions." + id + ".";
                donnees.set(b + "petrole", Double.valueOf(nombre(a[2])));
                donnees.set(b + "gaz", Double.valueOf(nombre(a[3])));
                StringBuilder nm = new StringBuilder();
                for (int i = 4; i < a.length; i++) {
                    nm.append(nm.length() > 0 ? " " : "").append(a[i]);
                }
                donnees.set(b + "nom", nm.length() > 0 ? nm.toString() : a[1]);
                sauver();
                SSNG.msg(s, "&aRegion &e" + id + " &acreee. Place ses coins : &e/gisement pos1 " + id + " &apuis &e/gisement pos2 " + id);
                return true;
            }
            if ((q.equals("pos1") || q.equals("pos2")) && a.length >= 2 && s instanceof Player) {
                Player p = (Player) s;
                String b = "regions." + a[1].toLowerCase(Locale.ROOT) + ".";
                if (!donnees.isSet(b + "petrole")) {
                    SSNG.msg(s, "&cRegion inconnue (/gisement creer).");
                    return true;
                }
                String n = q.equals("pos1") ? "1" : "2";
                donnees.set(b + "x" + n, Integer.valueOf(p.getLocation().getBlockX()));
                donnees.set(b + "z" + n, Integer.valueOf(p.getLocation().getBlockZ()));
                donnees.set(b + "monde", p.getWorld().getName());
                if (!donnees.isSet(b + "x" + (n.equals("1") ? "2" : "1"))) {
                    donnees.set(b + "x" + (n.equals("1") ? "2" : "1"), Integer.valueOf(p.getLocation().getBlockX()));
                    donnees.set(b + "z" + (n.equals("1") ? "2" : "1"), Integer.valueOf(p.getLocation().getBlockZ()));
                }
                sauver();
                SSNG.msg(s, "&aCoin " + n + " place.");
                return true;
            }
            if (q.equals("zone") && a.length >= 6 && s instanceof Player) {
                String b = "regions." + a[1].toLowerCase(Locale.ROOT) + ".";
                if (!donnees.isSet(b + "petrole")) {
                    SSNG.msg(s, "&cRegion inconnue (/gisement creer).");
                    return true;
                }
                donnees.set(b + "monde", ((Player) s).getWorld().getName());
                donnees.set(b + "x1", Integer.valueOf(a[2]));
                donnees.set(b + "z1", Integer.valueOf(a[3]));
                donnees.set(b + "x2", Integer.valueOf(a[4]));
                donnees.set(b + "z2", Integer.valueOf(a[5]));
                sauver();
                SSNG.msg(s, "&aZone de la region reglee.");
                return true;
            }
            if (q.equals("regler") && a.length >= 4) {
                String b = "regions." + a[1].toLowerCase(Locale.ROOT) + ".";
                donnees.set(b + "petrole", Double.valueOf(nombre(a[2])));
                donnees.set(b + "gaz", Double.valueOf(nombre(a[3])));
                sauver();
                SSNG.msg(s, "&aGisements mis a jour.");
                return true;
            }
            if (q.equals("supprimer") && a.length >= 2) {
                donnees.set("regions." + a[1].toLowerCase(Locale.ROOT), null);
                sauver();
                SSNG.msg(s, "&7Region supprimee.");
                return true;
            }
            if (q.equals("defaut") && a.length >= 3) {
                donnees.set("defaut.petrole", Double.valueOf(nombre(a[1])));
                donnees.set("defaut.gaz", Double.valueOf(nombre(a[2])));
                sauver();
                SSNG.msg(s, "&aValeurs hors region : petrole " + a[1] + ", gaz " + a[2]);
                return true;
            }
        } catch (NumberFormatException e) {
            SSNG.msg(s, "&cNombre invalide.");
            return true;
        }
        aide(s);
        return true;
    }
}
