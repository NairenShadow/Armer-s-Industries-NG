package fr.armersindustries.ssng.machines;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import fr.armersindustries.ssng.core.SSNG;

/**
 * SSNG-Machines : fait fonctionner les machines NationsGlory sans le plugin prive du serveur officiel :
 * petrolier, extracteur de gaz, reservoirs, silos (paquets des machines), ateliers (recettes, fabrication),
 * entreprises et marches, /recharge (energie, carburant, gaz), vitrine de toutes les machines.
 * Aucun reglage necessaire. Avec SSNG-Carte : zones a energie infinie ; avec SSNG-Iles : droits des iles.
 */
public class MachinesNG extends JavaPlugin {

    /** Compatibilite avec le code d'origine. TEST = monde de la vitrine (config), BUILD = monde des iles (SSNG-Iles). */
    public enum TypeMonde { HUB, BUILD, PVP, TEST }

    private Machines machines;
    private Ateliers ateliers;
    private Entreprises entreprises;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        machines = new Machines(this, SSNG.reseau());
        ateliers = new Ateliers(this, SSNG.reseau());
        entreprises = new Entreprises(this, SSNG.reseau());
        getCommand("machines").setExecutor(machines);
        getCommand("recharge").setExecutor(new Recharge(this));
        getCommand("atelier").setExecutor(ateliers);
        getCommand("entreprise").setExecutor(entreprises);
        Bukkit.getPluginManager().registerEvents(ateliers, this);
        // apres SSNG-Core (reseau NG pret un tick apres le demarrage)
        Bukkit.getScheduler().runTaskLater(this, new Runnable() {
            public void run() {
                machines.demarrer();
                ateliers.demarrer();
                entreprises.demarrer();
            }
        }, 3L);
    }

    // ------------------------------------------------------------------ compatibilite

    public World monde(TypeMonde t) {
        if (t == TypeMonde.TEST) {
            World w = Bukkit.getWorld(getConfig().getString("monde-vitrine", ""));
            return w != null ? w : Bukkit.getWorlds().get(0);
        }
        if (t == TypeMonde.BUILD) {
            Plugin i = Bukkit.getPluginManager().getPlugin("SSNG-Iles");
            if (i != null) {
                return Bukkit.getWorld(i.getConfig().getString("monde", "iles"));
            }
        }
        return Bukkit.getWorlds().get(0);
    }

    public TypeMonde type(World w) {
        if (w == null) {
            return null;
        }
        Plugin i = Bukkit.getPluginManager().getPlugin("SSNG-Iles");
        if (i != null && w.getName().equalsIgnoreCase(i.getConfig().getString("monde", "iles"))) {
            return TypeMonde.BUILD;
        }
        if (w == monde(TypeMonde.TEST)) {
            return TypeMonde.TEST;
        }
        return null;
    }

    public Location spawn(TypeMonde t) {
        World w = monde(t);
        return w.getSpawnLocation().add(0.5, 0, 0.5);
    }

    /** Zones de SSNG-Carte (energie infinie, vitrine). */
    public fr.armersindustries.ssng.carte.Zones zones() {
        Plugin c = Bukkit.getPluginManager().getPlugin("SSNG-Carte");
        return c instanceof fr.armersindustries.ssng.carte.Carte && c.isEnabled() ? ((fr.armersindustries.ssng.carte.Carte) c).zones() : null;
    }

    /** Droit d'utiliser une machine sur une ile (SSNG-Iles), par reflexion. */
    public boolean peutInteragirIle(Player p, Location l) {
        Plugin i = Bukkit.getPluginManager().getPlugin("SSNG-Iles");
        if (i == null) {
            return true;
        }
        try {
            Object g = i.getClass().getMethod("iles").invoke(i);
            Object ile = g.getClass().getMethod("ileA", Location.class).invoke(g, l);
            if (ile == null) {
                return false;
            }
            for (java.lang.reflect.Method m : g.getClass().getMethods()) {
                if (m.getName().equals("permission") && m.getParameterTypes().length == 3) {
                    return Boolean.TRUE.equals(m.invoke(g, p, ile, "interact"));
                }
            }
        } catch (Throwable t) {
            return true;
        }
        return true;
    }

    /** Journal (le plugin d'origine publiait aussi vers son API web). */
    public Evenements evenements() {
        return evenements;
    }

    private final Evenements evenements = new Evenements();

    public class Evenements {
        public void publier(String type, String texte, Object donnees) {
            getLogger().info("[" + type + "] " + texte);
        }
    }
}
