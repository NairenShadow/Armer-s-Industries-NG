package fr.armersindustries.ssng.core;

import java.io.DataOutputStream;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import com.google.gson.Gson;

/**
 * SSNG-Core : boite a outils commune du pack ShadowState-NG (Armer's Industries).
 * - reseau NationsGUI (paquets du mod NG, dans les deux sens) ;
 * - objets NG : bordure de zone (lignes rouges), colis d'assaut ;
 * - marqueurs NG (ecran, boussole, minimap, carte), titres, messages en couleurs ;
 * - droits simples (op ou permissions ssng.*), petits fichiers de donnees YAML.
 * Les autres plugins du pack en dependent ; il ne fait rien tout seul.
 */
public class SSNG extends JavaPlugin {

    private static SSNG instance;
    private ReseauNG reseau;

    @Override
    public void onEnable() {
        instance = this;
        reseau = new ReseauNG(this);
        // un tick apres le demarrage : le registre des paquets de NationsGUI est pret
        Bukkit.getScheduler().runTask(this, new Runnable() {
            public void run() {
                reseau.demarrer(null);
            }
        });
        getLogger().info("SSNG-Core actif (pack ShadowState-NG par Armer's Industries).");
    }

    public static SSNG get() {
        return instance;
    }

    public static ReseauNG reseau() {
        return instance.reseau;
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        msg(s, "&6ShadowState-NG &7(Armer's Industries) - reseau NG : " + (reseau != null && reseau.pret() ? "&aOK" : "&cindisponible"));
        StringBuilder b = new StringBuilder();
        for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
            if (p.getName().startsWith("SSNG-")) {
                b.append(p.getName()).append(' ').append(p.getDescription().getVersion()).append(", ");
            }
        }
        msg(s, "&7Plugins du pack : &f" + (b.length() > 2 ? b.substring(0, b.length() - 2) : "-"));
        return true;
    }

    // ------------------------------------------------------------------ messages

    public static String c(String t) {
        return ChatColor.translateAlternateColorCodes('&', t == null ? "" : t);
    }

    public static void msg(CommandSender s, String t) {
        if (s != null) {
            s.sendMessage(c(t));
        }
    }

    /** Grand titre NG au milieu de l'ecran (message dans le chat si NationsGUI est absent). */
    public static void titre(Player p, String titre, String sousTitre, int ticks) {
        final String t = c(titre);
        final String st = c(sousTitre == null ? "" : sousTitre);
        final float duree = ticks;
        if (instance == null || instance.reseau == null || !instance.reseau.pret()) {
            p.sendMessage(t + (st.isEmpty() ? "" : " " + st));
            return;
        }
        instance.reseau.envoyer(p, "TitlePacket", new ReseauNG.Ecrivain() {
            public void ecrire(DataOutputStream out) throws Exception {
                out.writeUTF(t);
                out.writeUTF(st);
                out.writeFloat(duree);
            }
        });
    }

    // ------------------------------------------------------------------ marqueurs NG

    /** Couleurs de marqueurs NG utiles. */
    public static final String ROUGE = "FOUNDATIONS_ALLIANCES_RED_400";
    public static final String JAUNE = "FOUNDATIONS_ALLIANCES_YELLOW_400";
    public static final String CYAN = "FOUNDATIONS_ALLIANCES_CYAN_400";

    /** Ajoute (ou deplace) un marqueur chez ce joueur : ecran, boussole, minimap et carte. */
    public static void marqueur(Player p, String nom, String label, double x, double y, double z, String couleur) {
        Map<String, Object> m = new HashMap<String, Object>();
        m.put("name", nom);
        m.put("label", label);
        m.put("text", label);
        m.put("world", p.getWorld().getName());
        m.put("x", Double.valueOf(x));
        m.put("y", Double.valueOf(y));
        m.put("z", Double.valueOf(z));
        m.put("colorName", couleur);
        m.put("kind", "POINT_OF_INTEREST");
        m.put("type", "POINT_OF_INTEREST");
        m.put("showOnMap", Boolean.TRUE);
        m.put("showOnMinimap", Boolean.TRUE);
        m.put("showOnCompass", Boolean.TRUE);
        m.put("visible", Boolean.TRUE);
        m.put("hideOnReach", Boolean.FALSE);
        m.put("maxDistance", Double.valueOf(1000));
        final String json = new Gson().toJson(m);
        reseau().envoyer(p, "MarkerPacket", new ReseauNG.Ecrivain() {
            public void ecrire(DataOutputStream out) throws Exception {
                out.writeUTF(json);
            }
        });
    }

    public static void retirerMarqueur(Player p, final String nom) {
        reseau().envoyer(p, "MarkerRemovePacket", new ReseauNG.Ecrivain() {
            public void ecrire(DataOutputStream out) throws Exception {
                out.writeUTF(nom);
            }
        });
    }

    // ------------------------------------------------------------------ droits

    /** Admin : op ou permission ssng.admin. */
    public static boolean admin(CommandSender s) {
        return s.isOp() || s.hasPermission("ssng.admin");
    }

    /** Haut staff (chefs de nation...) : ssng.hautstaff ou admin. */
    public static boolean hautStaff(CommandSender s) {
        return admin(s) || s.hasPermission("ssng.hautstaff");
    }

    /** Staff : ssng.staff ou au-dessus. */
    public static boolean staff(CommandSender s) {
        return hautStaff(s) || s.hasPermission("ssng.staff");
    }

    // ------------------------------------------------------------------ donnees

    /** Petit fichier YAML dans le dossier d'un plugin (cree vide s'il n'existe pas). */
    public static YamlConfiguration charger(Plugin p, String nom) {
        File f = new File(p.getDataFolder(), nom);
        return f.exists() ? YamlConfiguration.loadConfiguration(f) : new YamlConfiguration();
    }

    public static void sauver(Plugin p, String nom, YamlConfiguration y) {
        try {
            p.getDataFolder().mkdirs();
            y.save(new File(p.getDataFolder(), nom));
        } catch (Exception e) {
            p.getLogger().warning("Sauvegarde de " + nom + " impossible : " + e);
        }
    }

    /**
     * Nom du monde tel que la carte NG le connait ("DIM3" pour un monde de plugin...) :
     * le mod NationsGUI l'annonce au client avec ServerEventHandler.dynmapWorldNameOf.
     */
    public static String cleCarte(World w) {
        try {
            Object nms = w.getClass().getMethod("getHandle").invoke(w);
            Class<?> cMonde = Class.forName("net.minecraft.world.World", true, w.getClass().getClassLoader());
            Class<?> seh = Class.forName("net.ilexiconn.nationsgui.forge.server.ServerEventHandler", true, w.getClass().getClassLoader());
            Object r = seh.getMethod("dynmapWorldNameOf", cMonde).invoke(null, nms);
            if (r instanceof String && !((String) r).isEmpty()) {
                return (String) r;
            }
        } catch (Throwable t) {
            // NationsGUI absent : nom Bukkit
        }
        return w.getName();
    }
}
