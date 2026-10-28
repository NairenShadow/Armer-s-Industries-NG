package fr.armersindustries.ssng.assaut;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import fr.armersindustries.ssng.core.ObjetsNG;
import fr.armersindustries.ssng.core.SSNG;

/**
 * SSNG-Assaut : l'assaut de NationsGlory sur n'importe quel serveur NG.
 * - /assaut : assaut automatique Attaquants contre Defenseurs dans une arene (6 phases comme sur NG,
 *   HUD et ecran de resultat du mod, zone de capture 10x10 au hasard, colis NG, marqueurs) ;
 * - /assautadmin : regler l'arene en jeu (monde, champ, points d'arrivee, kits, regles) ;
 * - /nation : nations d'evenement et assaut grandeur nature sur le territoire du defenseur.
 */
public class AssautNG extends JavaPlugin {

    private Arene arene;
    private Assaut assaut;
    private GrandAssaut nations;
    private final Stats stats = new Stats();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        arene = new Arene(this);
        assaut = new Assaut(this);
        nations = new GrandAssaut(this);
        Bukkit.getPluginManager().registerEvents(assaut, this);
        Bukkit.getPluginManager().registerEvents(nations, this);
        getCommand("assaut").setExecutor(assaut);
        getCommand("assautadmin").setExecutor(arene);
        getCommand("nation").setExecutor(nations);
        assaut.demarrer();
        if (arene.monde() == null) {
            getLogger().info("Arene d'assaut pas encore reglee : un admin tape /assautadmin monde dans le monde voulu.");
        }
    }

    @Override
    public void onDisable() {
        if (nations != null) {
            nations.arreter();
        }
    }

    public Arene arene() {
        return arene;
    }

    public Stats stats() {
        return stats;
    }

    /** Appele par SSNG-Ponts : un colis du pack est-il a cette position ? (sinon le mod NG le supprime) */
    public Boolean colisValide(int x, int z) {
        return Boolean.valueOf(ObjetsNG.colisA(x, z));
    }

    /** Appele par SSNG-Carte : le joueur est-il en plein assaut ? */
    public Boolean enCombat(Player p) {
        return Boolean.valueOf(assaut.participe(p));
    }

    /** Statistiques simples (stats.yml) : participations, victoires, defaites, kills. */
    public class Stats {
        private YamlConfiguration y;

        public void ajouter(String joueur, String cle, int n) {
            if (y == null) {
                y = SSNG.charger(AssautNG.this, "stats.yml");
            }
            String k = joueur.toLowerCase() + "." + cle;
            y.set(k, Integer.valueOf(y.getInt(k, 0) + n));
            SSNG.sauver(AssautNG.this, "stats.yml", y);
        }
    }
}
