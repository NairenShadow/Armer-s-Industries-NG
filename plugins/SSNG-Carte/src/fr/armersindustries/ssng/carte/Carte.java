package fr.armersindustries.ssng.carte;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import fr.armersindustries.ssng.core.SSNG;

/**
 * SSNG-Carte : zones / territoires (comme les pays NG) affiches sur la carte NationsGlory,
 * gestion des chunks depuis la carte (onglet "Gestion du territoire"), regles par zone (/zone flags)
 * et commandes /f facon NationsGlory. Aucun reglage necessaire.
 */
public class Carte extends JavaPlugin {

    private Zones zones;
    private CarteNG carte;
    private final Grades grades = new Grades();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        zones = new Zones(this);
        Bukkit.getPluginManager().registerEvents(zones, this);
        getCommand("zone").setExecutor(zones);
        CommandesF f = new CommandesF(this);
        Bukkit.getPluginManager().registerEvents(f, this);
        getCommand("f").setExecutor(f);
        carte = new CarteNG(this, SSNG.reseau());
        // apres SSNG-Core (reseau NG pret un tick apres le demarrage)
        Bukkit.getScheduler().runTaskLater(this, new Runnable() {
            public void run() {
                carte.demarrer();
            }
        }, 2L);
    }

    @Override
    public void onDisable() {
        if (zones != null) {
            zones.sauver();
        }
    }

    public Zones zones() {
        return zones;
    }

    public CarteNG carte() {
        return carte;
    }

    public Grades grades() {
        return grades;
    }

    /** Joueur en combat (duel, assaut) : demande aux autres plugins du pack s'ils sont installes. */
    public boolean occupe(Player p) {
        for (String n : new String[] {"SSNG-Assaut", "ShadowMondes"}) {
            Plugin x = Bukkit.getPluginManager().getPlugin(n);
            if (x == null || !x.isEnabled()) {
                continue;
            }
            try {
                Object r = x.getClass().getMethod("enCombat", Player.class).invoke(x, p);
                if (Boolean.TRUE.equals(r)) {
                    return true;
                }
            } catch (Throwable t) {
                // pas de reponse
            }
        }
        return false;
    }

    /**
     * Grades pour les droits de construction par chunk : liste dans config.yml (grades),
     * un joueur a le grade X s'il a la permission ssng.grade.x (ou s'il est admin).
     */
    public class Grades {
        public List<String> ordre() {
            List<String> l = getConfig().getStringList("grades");
            return l.isEmpty() ? new ArrayList<String>(Arrays.asList("Builder", "Testeur", "Moderateur", "Admin")) : l;
        }

        public int indexGrade(String g) {
            List<String> o = ordre();
            for (int i = 0; i < o.size(); i++) {
                if (o.get(i).equalsIgnoreCase(g)) {
                    return i;
                }
            }
            return -1;
        }

        public boolean auMoins(CommandSender s, String g) {
            if (SSNG.admin(s)) {
                return true;
            }
            int min = indexGrade(g);
            List<String> o = ordre();
            for (int i = Math.max(0, min); i < o.size(); i++) {
                if (s.hasPermission("ssng.grade." + o.get(i).toLowerCase())) {
                    return true;
                }
            }
            return false;
        }
    }
}
