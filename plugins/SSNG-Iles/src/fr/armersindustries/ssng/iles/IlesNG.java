package fr.armersindustries.ssng.iles;

import java.io.File;
import java.io.IOException;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.java.JavaPlugin;

import fr.armersindustries.ssng.core.SSNG;

/**
 * SSNG-Iles : les iles de NationsGlory (interface "Iles" du mod : creation, visite, membres, permissions,
 * proprietes, sauvegardes) dans un monde vide dedie. Aucun reglage necessaire :
 * le monde "iles" est cree au premier demarrage. /i (ou /ile) ouvre l'interface.
 * Droits : ssng.iles.creer (creer une ile, par defaut tout le monde), ssng.iles.worldedit (WorldEdit sur son ile).
 */
public class IlesNG extends JavaPlugin implements Listener {

    /** Compatibilite avec le code d'origine : seul BUILD (le monde des iles) est utilise ici. */
    public enum TypeMonde { HUB, BUILD, PVP, TEST }

    private GestionIles iles;
    private PaquetsIles paquets;
    private ProtectionIles protection;
    private YamlConfiguration donnees;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        donnees = SSNG.charger(this, "donnees.yml");
        creerMonde();
        iles = new GestionIles(this);
        paquets = new PaquetsIles(this, iles, SSNG.reseau());
        protection = new ProtectionIles(this, iles, paquets);
        Bukkit.getPluginManager().registerEvents(protection, this);
        Bukkit.getPluginManager().registerEvents(this, this);
        // apres SSNG-Core (reseau NG pret un tick apres le demarrage)
        Bukkit.getScheduler().runTaskLater(this, new Runnable() {
            public void run() {
                SSNG.reseau().ajouterPrefixe("Island", new fr.armersindustries.ssng.core.ReseauNG.Recepteur() {
                    public void recu(Player j, String paquet, java.io.DataInputStream in) throws Exception {
                        paquets.recu(j, paquet, in);
                    }
                });
                SSNG.reseau().ajouterPrefixe("RemoteOpenIsland", new fr.armersindustries.ssng.core.ReseauNG.Recepteur() {
                    public void recu(Player j, String paquet, java.io.DataInputStream in) throws Exception {
                        paquets.recu(j, paquet, in);
                    }
                });
            }
        }, 2L);
    }

    private void creerMonde() {
        String nom = getConfig().getString("monde", "iles");
        World w = Bukkit.getWorld(nom);
        if (w == null) {
            getLogger().info("Chargement du monde des iles (vide) : " + nom);
            w = new WorldCreator(nom).generator(new GenerateurVide()).generateStructures(false).createWorld();
        }
        if (w == null) {
            return;
        }
        w.setSpawnFlags(false, false);
        w.setPVP(true);
        w.setStorm(false);
        if (w.getBlockAt(0, 64, 0).getType() == Material.AIR) {
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    boolean bord = Math.abs(dx) == 4 || Math.abs(dz) == 4;
                    w.getBlockAt(dx, 64, dz).setType(bord ? Material.GLOWSTONE : Material.SMOOTH_BRICK);
                }
            }
            w.setSpawnLocation(0, 65, 0);
        }
    }

    // ------------------------------------------------------------------ compatibilite

    public World monde(TypeMonde t) {
        return t == TypeMonde.BUILD ? Bukkit.getWorld(getConfig().getString("monde", "iles")) : Bukkit.getWorlds().get(0);
    }

    public TypeMonde type(World w) {
        if (w == null) {
            return null;
        }
        return w.getName().equalsIgnoreCase(getConfig().getString("monde", "iles")) ? TypeMonde.BUILD : null;
    }

    public Location spawn(TypeMonde t) {
        World w = monde(t);
        return w == null ? null : w.getSpawnLocation().add(0.5, 0, 0.5);
    }

    public YamlConfiguration donnees() {
        return donnees;
    }

    public void sauverDonnees() {
        SSNG.sauver(this, "donnees.yml", donnees);
    }

    public ProtectionIles protectionIles() {
        return protection;
    }

    public GestionIles iles() {
        return iles;
    }

    public PaquetsIles paquetsIles() {
        return paquets;
    }

    private final Grades grades = new Grades();

    public Grades grades() {
        return grades;
    }

    /** WorldEdit sur son ile : permission ssng.iles.worldedit (le grade de config n'est plus utilise). */
    public static class Grades {
        public boolean auMoins(CommandSender s, String grade) {
            return SSNG.admin(s) || s.hasPermission("ssng.iles.worldedit");
        }
    }

    /** Lecture / ecriture YAML (comme dans le plugin d'origine). */
    public static final class Yaml {
        private Yaml() {
        }

        public static YamlConfiguration charger(File f) {
            return f.exists() ? YamlConfiguration.loadConfiguration(f) : new YamlConfiguration();
        }

        public static void sauver(YamlConfiguration y, File f) throws IOException {
            f.getParentFile().mkdirs();
            y.save(f);
        }
    }

    // ------------------------------------------------------------------ /i, /ile

    /** "/i" est souvent pris par Essentials (/item) : on l'intercepte avant lui. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void commandeI(PlayerCommandPreprocessEvent e) {
        String m = e.getMessage().trim();
        String bas = m.toLowerCase();
        if (bas.equals("/i") || bas.startsWith("/i ") || bas.equals("/is") || bas.startsWith("/is ")) {
            e.setCancelled(true);
            String reste = m.substring(bas.startsWith("/is") ? 3 : 2).trim();
            ile(e.getPlayer(), reste.isEmpty() ? new String[0] : reste.split("\\s+"));
        }
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (s instanceof Player) {
            ile((Player) s, a);
        }
        return true;
    }

    private void ile(Player p, String[] a) {
        String sous = a.length == 0 ? "" : a[0].toLowerCase();
        if (sous.equals("i") || sous.equals("info") || sous.equals("menu")) {
            paquets.ouvrirIle(p);
        } else if ((sous.equals("tp") || sous.equals("go")) && a.length > 1) {
            paquets.teleporter(p, iles.ile(a[1]), SSNG.admin(p));
        } else if (sous.equals("home") || sous.equals("maison")) {
            java.util.List<Ile> mes = iles.ilesDe(p.getName());
            if (mes.isEmpty()) {
                SSNG.msg(p, "&cTu n'as pas encore d'ile. &f/i &cpour en creer une.");
            } else {
                paquets.teleporter(p, mes.get(0), true);
            }
        } else if (sous.equals("spawn")) {
            p.teleport(spawn(TypeMonde.BUILD));
        } else if (sous.equals("aide") || sous.equals("help")) {
            SSNG.msg(p, "&e/i &7- liste des iles (creer, visiter)");
            SSNG.msg(p, "&e/i i &7- menu de l'ile ou tu es (membres, permissions, proprietes...)");
            SSNG.msg(p, "&e/i home &7- aller sur ta premiere ile &8| &e/i tp <numero> &8| &e/i spawn");
        } else {
            paquets.ouvrirListe(p);
        }
    }
}
