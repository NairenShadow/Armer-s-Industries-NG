package fr.armersindustries.ssng.carte;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import fr.armersindustries.ssng.core.SSNG;

/**
 * /f : les zones gerees comme des pays de NationsGlory.
 * Tout le monde : /f map, /f info, /f liste.
 * Admins : on "entre" dans une zone (/f creer, /f choisir) puis on claim autour de soi comme un chef de pays.
 */
public class CommandesF implements Listener, org.bukkit.command.CommandExecutor {

    private static final Set<String> NOMS = new HashSet<String>(Arrays.asList("f", "faction", "factions", "pays"));

    private final Carte plugin;
    /** joueur -> mode auto : "claim" ou "unclaim" */
    private final Map<String, String> auto = new HashMap<String, String>();

    public CommandesF(Carte plugin) {
        this.plugin = plugin;
    }

    private Zones zones() {
        return plugin.zones();
    }

    private static boolean admin(Player p) {
        return SSNG.admin(p);
    }

    // ------------------------------------------------------------------ interception de /f

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void commande(PlayerCommandPreprocessEvent e) {
        String[] mots = e.getMessage().substring(1).trim().split("\\s+");
        String cmd = mots[0].toLowerCase();
        if (cmd.contains(":")) {
            cmd = cmd.substring(cmd.indexOf(':') + 1);
        }
        if (!NOMS.contains(cmd)) {
            return;
        }
        e.setCancelled(true);
        executer(e.getPlayer(), Arrays.copyOfRange(mots, 1, mots.length));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void bouger(PlayerMoveEvent e) {
        if (auto.isEmpty() || e.getTo() == null) {
            return;
        }
        if ((e.getFrom().getBlockX() >> 4) == (e.getTo().getBlockX() >> 4) && (e.getFrom().getBlockZ() >> 4) == (e.getTo().getBlockZ() >> 4)) {
            return;
        }
        String mode = auto.get(e.getPlayer().getName());
        if (mode == null) {
            return;
        }
        Player p = e.getPlayer();
        Zone z = maZone(p, false);
        if (z == null && mode.equals("claim")) {
            auto.remove(p.getName());
            return;
        }
        int[] r = claimer(p, z, p.getWorld(), e.getTo().getBlockX() >> 4, e.getTo().getBlockZ() >> 4, 0, mode.equals("claim"));
        if (r[0] > 0) {
            zones().sauver();
            rafraichir(p.getWorld());
        }
    }

    @EventHandler
    public void quitter(PlayerQuitEvent e) {
        auto.remove(e.getPlayer().getName());
    }

    public boolean onCommand(org.bukkit.command.CommandSender s, org.bukkit.command.Command c, String label, String[] a) {
        if (s instanceof Player) {
            executer((Player) s, a);
        } else {
            SSNG.msg(s, "&cEn jeu seulement.");
        }
        return true;
    }

    // ------------------------------------------------------------------ outils

    /** Zone de l'admin : celle choisie (/f choisir, /f creer), sinon celle ou il se trouve. */
    private Zone maZone(Player p, boolean dire) {
        Zone z = plugin.carte() != null ? plugin.carte().zoneDe(p) : zones().zoneA(p.getLocation());
        if (z == null && dire) {
            SSNG.msg(p, "&cTu n'as pas de zone. &f/f creer <nom> &7ou &f/f choisir <zone>");
        }
        return z;
    }

    private void rafraichir(World w) {
        if (plugin.carte() == null || w == null) {
            return;
        }
        for (Player o : w.getPlayers()) {
            plugin.carte().territoires(o);
        }
    }

    /** {pris, refuses (deja dans une autre zone)} */
    private int[] claimer(Player p, Zone z, World w, int ccx, int ccz, int rayon, boolean ajouter) {
        int pris = 0;
        int refuses = 0;
        boolean voler = plugin.getConfig().getBoolean("carte.voler-chunks", false);
        for (int cx = ccx - rayon; cx <= ccx + rayon; cx++) {
            for (int cz = ccz - rayon; cz <= ccz + rayon; cz++) {
                Zone actuelle = zones().zoneChunk(w.getName(), cx, cz);
                if (ajouter) {
                    if (actuelle == z) {
                        continue;
                    }
                    if (actuelle != null && !voler) {
                        refuses++;
                        continue;
                    }
                    zones().claimChunk(z, w.getName(), cx, cz);
                    pris++;
                } else if (actuelle != null && (z == null || actuelle == z)) {
                    zones().unclaimChunk(w.getName(), cx, cz);
                    zones().reglerAccesChunk(w.getName(), cx, cz, null);
                    pris++;
                }
            }
        }
        return new int[] {pris, refuses};
    }

    private static String slug(String nom) {
        String s = java.text.Normalizer.normalize(nom.toLowerCase(), java.text.Normalizer.Form.NFD).replaceAll("[^a-z0-9]+", "-");
        s = s.replaceAll("^-+|-+$", "");
        return s.isEmpty() ? "zone" : (s.length() > 24 ? s.substring(0, 24) : s);
    }

    private static String join(String[] a, int debut) {
        StringBuilder sb = new StringBuilder();
        for (int i = debut; i < a.length; i++) {
            sb.append(i > debut ? " " : "").append(a[i]);
        }
        return sb.toString();
    }

    private static int rayon(String[] a, int i) {
        if (a.length <= i) {
            return 0;
        }
        try {
            return Math.max(0, Math.min(10, Integer.parseInt(a[i])));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // ------------------------------------------------------------------ commandes

    private void executer(Player p, String[] a) {
        String sous = a.length == 0 ? "aide" : a[0].toLowerCase();
        // ---- pour tout le monde
        if (sous.equals("map") || sous.equals("carte")) {
            zones().carte(p);
            return;
        }
        if (sous.equals("info") || sous.equals("show") || sous.equals("i")) {
            info(p, a.length > 1 ? zones().zone(a[1]) : null, a.length > 1);
            return;
        }
        if (sous.equals("liste") || sous.equals("list") || sous.equals("l")) {
            SSNG.msg(p, "&6Zones &7(" + zones().toutes().size() + ") :");
            for (Zone z : zones().toutes()) {
                SSNG.msg(p, "&7- " + z.couleur + z.nom + " &8(" + z.id + ", " + zones().nbChunks(z) + " chunks"
                        + (z.flag("pvp") ? ", &cPvP&8" : "") + (z.spawn() != null ? ", home" : "") + ")");
            }
            return;
        }
        if ((sous.equals("home") || sous.equals("tp")) && !admin(p) && a.length > 1) {
            Zone z = zones().zone(a[1]);
            if (z == null || z.spawn() == null || !z.flag("entree")) {
                SSNG.msg(p, "&cZone introuvable ou sans point d'arrivee.");
            } else {
                p.teleport(z.spawn());
            }
            return;
        }
        if (!admin(p)) {
            SSNG.msg(p, "&6/f map &7: carte des zones | &6/f info &7: zone ici | &6/f liste &7: toutes les zones");
            return;
        }
        // ---- admins
        if (sous.equals("aide") || sous.equals("help") || sous.equals("?")) {
            aide(p);
            return;
        }
        if (sous.equals("creer") || sous.equals("create")) {
            if (a.length < 2) {
                SSNG.msg(p, "&c/f creer <nom>");
                return;
            }
            String nom = join(a, 1);
            String id = slug(nom);
            if (zones().zone(id) != null) {
                SSNG.msg(p, "&cLa zone " + id + " existe deja. &f/f choisir " + id);
                return;
            }
            Zone z = zones().creer(id);
            z.nom = nom;
            zones().sauver();
            plugin.carte().choisir(p, z);
            SSNG.msg(p, "&aZone " + z.couleur + nom + " &acreee, tu la geres maintenant. &7Claim : &f/f claim [rayon] &7ou &f/f claim auto");
            return;
        }
        if (sous.equals("choisir") || sous.equals("select") || sous.equals("join") || sous.equals("rejoindre")) {
            Zone z = a.length > 1 ? zones().zone(a[1]) : zones().zoneA(p.getLocation());
            if (z == null) {
                SSNG.msg(p, "&c/f choisir <zone> &7(/f liste)");
                return;
            }
            plugin.carte().choisir(p, z);
            SSNG.msg(p, "&aTu geres maintenant " + z.couleur + z.nom + "&a.");
            return;
        }
        if (sous.equals("claim") || sous.equals("unclaim")) {
            boolean ajouter = sous.equals("claim");
            if (a.length > 1 && a[1].equalsIgnoreCase("auto")) {
                String mode = ajouter ? "claim" : "unclaim";
                if (mode.equals(auto.get(p.getName()))) {
                    auto.remove(p.getName());
                    SSNG.msg(p, "&7" + mode + " auto &cdesactive&7.");
                    return;
                } else {
                    if (ajouter && maZone(p, true) == null) {
                        return;
                    }
                    auto.put(p.getName(), mode);
                    SSNG.msg(p, "&7" + mode + " auto &aactive &7: chaque chunk ou tu marches est " + (ajouter ? "ajoute" : "retire")
                            + ". Retape la commande pour arreter.");
                }
            }
            if (!ajouter && a.length > 1 && (a[1].equalsIgnoreCase("all") || a[1].equalsIgnoreCase("tout"))) {
                Zone z = maZone(p, true);
                if (z == null) {
                    return;
                }
                int n = 0;
                for (Map.Entry<String, Zone> en : zones().chunksDuMonde(p.getWorld().getName()).entrySet()) {
                    if (en.getValue() == z) {
                        String[] xz = en.getKey().split(";");
                        zones().unclaimChunk(p.getWorld().getName(), Integer.parseInt(xz[0]), Integer.parseInt(xz[1]));
                        n++;
                    }
                }
                zones().sauver();
                rafraichir(p.getWorld());
                SSNG.msg(p, "&a" + n + " chunk(s) retires de " + z.couleur + z.nom + " &a(dans ce monde).");
                return;
            }
            if (a.length > 1 && a[1].equalsIgnoreCase("auto")) {
                // on traite aussi le chunk actuel
            } else if (rayon(a, 1) < 0) {
                SSNG.msg(p, "&c/f " + sous + " [rayon 0-10|auto" + (ajouter ? "" : "|all") + "]");
                return;
            }
            int r = a.length > 1 && !a[1].equalsIgnoreCase("auto") ? rayon(a, 1) : 0;
            Zone z = ajouter ? maZone(p, true) : (plugin.carte().zoneDe(p));
            if (ajouter && z == null) {
                return;
            }
            Chunk ch = p.getLocation().getChunk();
            int[] res = claimer(p, z, p.getWorld(), ch.getX(), ch.getZ(), r, ajouter);
            zones().sauver();
            rafraichir(p.getWorld());
            if (ajouter) {
                SSNG.msg(p, "&a" + res[0] + " chunk(s) ajoutes a " + z.couleur + z.nom + "&a."
                        + (res[1] > 0 ? " &e" + res[1] + " deja dans une autre zone (non pris)." : ""));
            } else {
                SSNG.msg(p, "&a" + res[0] + " chunk(s) liberes.");
            }
            return;
        }
        if (sous.equals("supprimer") || sous.equals("disband")) {
            Zone z = a.length > 1 ? zones().zone(a[1]) : null;
            if (z == null) {
                SSNG.msg(p, "&c/f supprimer <zone> &7(nom exact, /f liste)");
                return;
            }
            zones().supprimer(z);
            zones().sauver();
            rafraichir(p.getWorld());
            SSNG.msg(p, "&aZone " + z.id + " supprimee.");
            return;
        }
        if (sous.equals("flags") || sous.equals("regles")) {
            Zone z = a.length > 1 ? zones().zone(a[1]) : maZone(p, true);
            if (z != null) {
                zones().ouvrirFlags(p, z);
            }
            return;
        }
        if (sous.equals("flag") || sous.equals("regle")) {
            Zone z = maZone(p, true);
            if (z == null) {
                return;
            }
            if (a.length < 3 || !Zone.FLAGS.containsKey(a[1].toLowerCase())) {
                SSNG.msg(p, "&c/f flag <regle> <on|off> &7- regles : " + Zone.FLAGS.keySet() + " &7(ou &f/f flags&7 en interface)");
                return;
            }
            boolean v = a[2].equalsIgnoreCase("on") || a[2].equalsIgnoreCase("oui") || a[2].equalsIgnoreCase("true");
            z.regler(a[1].toLowerCase(), v);
            zones().sauver();
            SSNG.msg(p, "&a" + z.nom + " : " + a[1].toLowerCase() + " = " + (v ? "oui" : "non"));
            return;
        }
        if (sous.equals("acces") || sous.equals("perm") || sous.equals("access")) {
            acces(p, a);
            return;
        }
        if (sous.equals("sethome") || sous.equals("spawn")) {
            Zone z = maZone(p, true);
            if (z != null) {
                z.spawn = Zone.ecrire(p.getLocation());
                zones().sauver();
                SSNG.msg(p, "&aPoint d'arrivee de " + z.nom + " place ici. &7(/f home " + z.id + ")");
            }
            return;
        }
        if (sous.equals("home") || sous.equals("tp")) {
            Zone z = a.length > 1 ? zones().zone(a[1]) : maZone(p, true);
            if (z == null) {
                return;
            }
            if (z.spawn() == null) {
                SSNG.msg(p, "&cPas de point d'arrivee : &f/f sethome");
            } else {
                p.teleport(z.spawn());
            }
            return;
        }
        if ((sous.equals("nom") || sous.equals("rename") || sous.equals("tag")) && a.length > 1) {
            Zone z = maZone(p, true);
            if (z != null) {
                z.nom = join(a, 1);
                zones().sauver();
                rafraichir(p.getWorld());
                SSNG.msg(p, "&aNom : " + z.couleur + z.nom);
            }
            return;
        }
        if (sous.equals("couleur") && a.length > 1) {
            Zone z = maZone(p, true);
            if (z != null) {
                z.couleur = a[1].startsWith("&") ? a[1] : "&" + a[1];
                zones().sauver();
                rafraichir(p.getWorld());
                SSNG.msg(p, "&aCouleur : " + z.couleur + z.nom);
            }
            return;
        }
        if (sous.equals("vision")) {
            Zone z = maZone(p, true);
            if (z != null) {
                boolean v = !z.flag("vision-nocturne");
                z.regler("vision-nocturne", v);
                zones().sauver();
                SSNG.msg(p, "&a" + z.nom + " : vision de nuit pour tous " + (v ? "&aactivee" : "&cdesactivee") + "&a.");
            }
            return;
        }
        aide(p);
    }

    private void info(Player p, Zone z, boolean nommee) {
        if (nommee && z == null) {
            SSNG.msg(p, "&cZone inconnue (/f liste).");
            return;
        }
        Chunk ch = p.getLocation().getChunk();
        if (z == null) {
            z = zones().zoneA(p.getLocation());
            SSNG.msg(p, "&7Chunk &f" + ch.getX() + ", " + ch.getZ() + " &7: " + (z == null ? "&2zone libre (regles du monde)" : z.couleur + z.nom));
            List<String> acces = zones().accesChunk(p.getWorld().getName(), ch.getX(), ch.getZ());
            if (acces != null) {
                SSNG.msg(p, "&7Construction dans ce chunk : &f" + acces);
            }
        }
        if (z == null) {
            return;
        }
        SSNG.msg(p, "&6" + SSNG.c(z.couleur) + z.nom + " &8(" + z.id + ") &7- " + zones().nbChunks(z) + " chunks"
                + (z.spawn() != null ? ", home" : ""));
        StringBuilder sb = new StringBuilder();
        for (String f : Zone.FLAGS.keySet()) {
            sb.append(z.flag(f) ? "&a" : "&8").append(f).append(" ");
        }
        SSNG.msg(p, sb.toString());
        if (admin(p)) {
            Zone mienne = maZone(p, false);
            if (mienne != null) {
                SSNG.msg(p, "&7Zone que tu geres : " + mienne.couleur + mienne.nom);
            }
        }
    }

    /** /f acces <tous|aucun|reset|grade> [rayon]  ou  /f acces joueur <pseudo> [rayon] */
    private void acces(Player p, String[] a) {
        if (a.length < 2) {
            SSNG.msg(p, "&6/f acces tous &7: tout le monde construit ici");
            SSNG.msg(p, "&6/f acces Builder &7: a partir d'un grade (Builder, Testeur, Moderateur...)");
            SSNG.msg(p, "&6/f acces joueur <pseudo> &7: ajoute un joueur");
            SSNG.msg(p, "&6/f acces aucun &7: personne (sauf admins) | &6/f acces reset &7: regle de la zone");
            SSNG.msg(p, "&7Ajoute un rayon a la fin pour plusieurs chunks : &f/f acces Builder 3");
            return;
        }
        String quoi = a[1];
        int idxRayon = 2;
        String entree;
        if (quoi.equalsIgnoreCase("joueur") || quoi.equalsIgnoreCase("player")) {
            if (a.length < 3) {
                SSNG.msg(p, "&c/f acces joueur <pseudo> [rayon]");
                return;
            }
            entree = a[2];
            idxRayon = 3;
        } else if (quoi.equalsIgnoreCase("tous") || quoi.equalsIgnoreCase("all")) {
            entree = "tous";
        } else if (quoi.equalsIgnoreCase("aucun") || quoi.equalsIgnoreCase("personne")) {
            entree = "aucun";
        } else if (quoi.equalsIgnoreCase("reset") || quoi.equalsIgnoreCase("zone")) {
            entree = null;
        } else if (plugin.grades().indexGrade(quoi) >= 0) {
            entree = "grade:" + plugin.grades().ordre().get(plugin.grades().indexGrade(quoi));
        } else {
            SSNG.msg(p, "&cInconnu : " + quoi + ". &7Grades : " + plugin.grades().ordre());
            return;
        }
        int r = rayon(a, idxRayon);
        if (r < 0) {
            r = 0;
        }
        Chunk ch = p.getLocation().getChunk();
        String monde = p.getWorld().getName();
        int n = 0;
        for (int cx = ch.getX() - r; cx <= ch.getX() + r; cx++) {
            for (int cz = ch.getZ() - r; cz <= ch.getZ() + r; cz++) {
                List<String> l;
                if (entree == null) {
                    l = null;
                } else if (entree.equals("aucun")) {
                    l = new ArrayList<String>(Arrays.asList("aucun"));
                } else if (entree.equals("tous") || entree.startsWith("grade:")) {
                    l = new ArrayList<String>(Arrays.asList(entree));
                } else {
                    List<String> avant = zones().accesChunk(monde, cx, cz);
                    l = new ArrayList<String>();
                    if (avant != null) {
                        for (String x : avant) {
                            if (!x.equals("aucun")) {
                                l.add(x);
                            }
                        }
                    }
                    if (!l.contains(entree)) {
                        l.add(entree);
                    }
                }
                zones().reglerAccesChunk(monde, cx, cz, l);
                n++;
            }
        }
        zones().sauver();
        SSNG.msg(p, "&a" + n + " chunk(s) : construction "
                + (entree == null ? "selon la regle de la zone" : entree.equals("aucun") ? "interdite (sauf admins)"
                        : entree.equals("tous") ? "pour tout le monde" : entree.startsWith("grade:") ? "a partir de " + entree.substring(6)
                                : "ouverte a " + entree) + "&a.");
    }

    private void aide(Player p) {
        SSNG.msg(p, "&8&m-------&6 Zones (/f) &8&m-------");
        Zone z = maZone(p, false);
        SSNG.msg(p, "&7Tu geres : " + (z == null ? "&caucune zone" : z.couleur + z.nom));
        SSNG.msg(p, "&6/f creer <nom> &7| &6/f choisir <zone> &7| &6/f liste &7| &6/f map &7| &6/f info");
        SSNG.msg(p, "&6/f claim [rayon] &7| &6/f claim auto &7| &6/f unclaim [rayon|all|auto]");
        SSNG.msg(p, "&6/f flags &7(interface) | &6/f flag <regle> <on|off> &7| &6/f vision");
        SSNG.msg(p, "&6/f acces <tous|grade|aucun|reset> [rayon] &7| &6/f acces joueur <pseudo>");
        SSNG.msg(p, "&6/f sethome &7| &6/f home [zone] &7| &6/f nom <nom> &7| &6/f couleur <code> &7| &6/f supprimer <zone>");
    }
}
