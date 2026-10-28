package fr.armersindustries.ssng.assaut;

import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import com.google.gson.Gson;

import fr.armersindustries.ssng.core.Inventaire;
import fr.armersindustries.ssng.core.ObjetsNG;
import fr.armersindustries.ssng.core.ReseauNG;
import fr.armersindustries.ssng.core.SSNG;

/**
 * Assaut facon NationsGlory : Attaquants contre Defenseurs, en 6 etapes comme
 * l'interface officielle (Preparation, Phase 1 PvP, Phase 2 Capture, Phase 3 PvP,
 * Phase 4 Capture, Phase 5 Capture). Points : kills + temps passe en majorite
 * dans la zone de capture. L'equipe qui a le plus de points gagne. L'assaut se
 * relance tout seul apres une pause (5 min par defaut).
 *
 * Le HUD d'assaut et l'ecran de resultat sont ceux du mod NationsGUI
 * (AssaultDataPacket, AssaultResultDataPacket).
 */
public class Assaut implements Listener, CommandExecutor {

    static final String[] ETATS = {"start", "first_pvp", "first_capture", "second_pvp", "second_capture", "third_capture"};
    static final String[] NOMS_ETATS = {"Preparation", "Phase 1 - PvP", "Phase 2 - Capture", "Phase 3 - PvP", "Phase 4 - Capture", "Phase 5 - Capture"};
    static final int[] DUREES_DEFAUT = {30, 120, 120, 120, 120, 120};
    private static final List<String> COMMANDES_AUTORISEES = Arrays.asList("assaut", "msg", "m", "r", "w", "tell", "liste", "rang");

    enum Etat { ATTENTE, EN_COURS, PAUSE }

    private final AssautNG plugin;
    private final Set<String> inscrits = new LinkedHashSet<String>();
    /** joueur -> true si attaquant */
    private final Map<String, Boolean> equipes = new HashMap<String, Boolean>();
    private final Map<String, Integer> kills = new HashMap<String, Integer>();
    private Etat etat = Etat.ATTENTE;
    private int phase;
    private int reste;
    private int pause;
    private long debut;
    private int ptsKillAtt;
    private int ptsKillDef;
    private int ptsZoneAtt;
    private int ptsZoneDef;
    private int controle = -1;
    private Scoreboard tableau;
    // zone d'assaut NG (carre rouge) : position actuelle, qui glisse vers la cible de la phase
    private double zoneX = Double.NaN;
    private double zoneZ;
    private double zoneDX;
    private double zoneDZ;
    // colis d'assaut
    private Entity colis;
    private Location colisLieu;
    private int colisVie;
    // point de capture de la phase : carre tire au hasard sur le champ de bataille
    private int capX;
    private int capZ;
    private int capY = 64;
    private int tenueAtt;
    private int tenueDef;

    public Assaut(AssautNG plugin) {
        this.plugin = plugin;
    }

    public void demarrer() {
        Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            public void run() {
                try {
                    tic();
                } catch (Throwable t) {
                    plugin.getLogger().warning("Assaut : " + t);
                }
            }
        }, 40L, 20L);
    }

    /** Etat pour l'API. */
    public Map<String, Object> etatApi() {
        Map<String, Object> m = new java.util.LinkedHashMap<String, Object>();
        m.put("etat", etat.name().toLowerCase());
        m.put("inscrits", new ArrayList<String>(inscrits));
        m.put("min_joueurs", Integer.valueOf(minJoueurs()));
        if (etat == Etat.EN_COURS) {
            m.put("phase", Integer.valueOf(phase));
            m.put("phase_nom", NOMS_ETATS[phase]);
            m.put("secondes_restantes", Integer.valueOf(reste));
            m.put("score_attaquants", Integer.valueOf(scoreAtt()));
            m.put("score_defenseurs", Integer.valueOf(scoreDef()));
            List<String> att = new ArrayList<String>();
            List<String> def = new ArrayList<String>();
            for (Map.Entry<String, Boolean> e : equipes.entrySet()) {
                (e.getValue().booleanValue() ? att : def).add(e.getKey());
            }
            m.put("attaquants", att);
            m.put("defenseurs", def);
            m.put("nom_attaquants", nomEquipe(true));
            m.put("nom_defenseurs", nomEquipe(false));
        } else if (etat == Etat.PAUSE) {
            m.put("prochain_dans_secondes", Integer.valueOf(pause));
        }
        return m;
    }

    public boolean participe(Player p) {
        return etat == Etat.EN_COURS && equipes.containsKey(p.getName());
    }

    // ------------------------------------------------------------------ reglages

    private int duree(int i) {
        return Math.max(5, plugin.getConfig().getInt("assaut.durees." + ETATS[i], DUREES_DEFAUT[i]));
    }

    private int minJoueurs() {
        return Math.max(2, plugin.getConfig().getInt("assaut.min-joueurs", 2));
    }

    private int pauseSecondes() {
        return Math.max(10, plugin.getConfig().getInt("assaut.pause-secondes", 300));
    }

    private int pointsKill() {
        return plugin.getConfig().getInt("assaut.points-kill", 5);
    }

    private int demiCapture() {
        return Math.max(1, plugin.getConfig().getInt("assaut.capture.taille", 10) / 2);
    }

    private int intervalleZone() {
        return Math.max(1, plugin.getConfig().getInt("assaut.capture.intervalle-secondes", 5));
    }

    /** Tire un nouveau point de capture au hasard sur le champ de bataille. */
    private void tirerCapture() {
        java.util.Random r = new java.util.Random();
        int d = demiCapture() + 1;
        int[] ch = champ();
        capX = ch[0] + d + r.nextInt(Math.max(1, ch[2] - ch[0] - 2 * d));
        capZ = ch[1] + d + r.nextInt(Math.max(1, ch[3] - ch[1] - 2 * d));
        capY = sol(capX, capZ);
        tenueAtt = 0;
        tenueDef = 0;
    }

    private int pointsZone() {
        return plugin.getConfig().getInt("assaut.capture.points", 1);
    }

    private String nomEquipe(boolean att) {
        return att ? plugin.getConfig().getString("assaut.nom-attaquants", "Rouges") : plugin.getConfig().getString("assaut.nom-defenseurs", "Bleus");
    }

    private World monde() {
        return plugin.arene().monde();
    }

    /** Champ de bataille {x1, z1, x2, z2} : regle par /pvpadmin champ, sinon celui de la map d'origine. */
    private int[] champ() {
        return plugin.arene().champ();
    }

    /** Hauteur du sol (dessus du bloc le plus haut) a cette position de la map PvP. */
    private int sol(int x, int z) {
        World w = monde();
        if (w == null) {
            return 64;
        }
        w.loadChunk(x >> 4, z >> 4);
        return w.getHighestBlockYAt(x, z);
    }

    private Location attente() {
        return plugin.arene().point("attente");
    }

    private Location camp(boolean att) {
        return plugin.arene().point(att ? "attaquants" : "defenseurs");
    }

    private int capture() {
        return phase == 2 ? 0 : phase == 4 ? 1 : phase == 5 ? 2 : -1;
    }

    private int scoreAtt() {
        return ptsKillAtt + ptsZoneAtt;
    }

    private int scoreDef() {
        return ptsKillDef + ptsZoneDef;
    }

    private List<Player> joueurs(Boolean att) {
        List<Player> l = new ArrayList<Player>();
        for (Map.Entry<String, Boolean> e : equipes.entrySet()) {
            if (att == null || e.getValue().equals(att)) {
                Player p = Bukkit.getPlayerExact(e.getKey());
                if (p != null) {
                    l.add(p);
                }
            }
        }
        return l;
    }

    private void diffuser(String message) {
        String m = SSNG.c(message);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (inscrits.contains(p.getName()) || equipes.containsKey(p.getName())) {
                p.sendMessage(m);
            }
        }
    }

    private static String seize(String s) {
        return s.length() <= 16 ? s : s.substring(0, 16);
    }

    private static String temps(int s) {
        s = Math.max(0, s);
        return (s / 60) + ":" + (s % 60 < 10 ? "0" : "") + (s % 60);
    }

    // ------------------------------------------------------------------ deroulement

    private void tic() {
        World w = monde();
        if (w == null) {
            return;
        }
        // inscrits partis du monde PvP
        for (String n : new ArrayList<String>(inscrits)) {
            Player p = Bukkit.getPlayerExact(n);
            if (p == null || !p.getWorld().equals(w)) {
                inscrits.remove(n);
            }
        }
        if (etat == Etat.ATTENTE) {
            if (inscrits.size() >= minJoueurs()) {
                lancer();
            }
            return;
        }
        if (etat == Etat.PAUSE) {
            pause--;
            if (pause == 60 || pause == 30 || pause == 10) {
                diffuser("[*banner id=\"assault\"] &7Prochain assaut dans &e" + pause + " s&7.");
            }
            if (pause <= 0) {
                etat = Etat.ATTENTE;
                if (inscrits.size() >= minJoueurs()) {
                    lancer();
                } else {
                    diffuser("[*banner id=\"assault\"] &7En attente de joueurs (&e" + inscrits.size() + "/" + minJoueurs() + "&7).");
                }
            }
            return;
        }
        // EN_COURS
        if (joueurs(Boolean.TRUE).isEmpty() || joueurs(Boolean.FALSE).isEmpty()) {
            finir(joueurs(Boolean.TRUE).isEmpty() ? "defenseurs" : "attaquants", "l'autre equipe est vide");
            return;
        }
        // les non-participants sortent du terrain pendant l'assaut
        for (Player p : w.getPlayers()) {
            if (!equipes.containsKey(p.getName()) && !SSNG.admin(p)) {
                int[] ch = champ();
                Location pl = p.getLocation();
                if (pl.getX() >= ch[0] && pl.getX() <= ch[2] + 1 && pl.getZ() >= ch[1] && pl.getZ() <= ch[3] + 1) {
                    p.teleport(attente());
                    SSNG.msg(p, "&cUn assaut est en cours. &f/assaut rejoindre &cpour participer.");
                }
            }
        }
        int c = capture();
        if (c >= 0) {
            int att = 0;
            int def = 0;
            int dm = demiCapture();
            for (Player p : joueurs(null)) {
                Location l = p.getLocation();
                if (!p.isDead() && l.getWorld().equals(w) && Math.abs(l.getY() - capY) < 16
                        && Math.abs(l.getX() - capX) <= dm && Math.abs(l.getZ() - capZ) <= dm) {
                    if (equipes.get(p.getName()).booleanValue()) {
                        att++;
                    } else {
                        def++;
                    }
                }
            }
            int nouveau = att > def ? 14 : def > att ? 11 : 0;
            // 1 point toutes les 5 secondes passees a tenir la zone
            if (att > def && ++tenueAtt % intervalleZone() == 0) {
                ptsZoneAtt += pointsZone();
            } else if (def > att && ++tenueDef % intervalleZone() == 0) {
                ptsZoneDef += pointsZone();
            }
            if (nouveau != controle) {
                controle = nouveau;
                if (nouveau != 0) {
                    diffuser("[*banner id=\"" + (nouveau == 14 ? "attacker" : "defender") + "\"] &7Les "
                            + (nouveau == 14 ? "&c" + nomEquipe(true) : "&9" + nomEquipe(false)) + " &7tiennent la zone !");
                }
            }
        }
        reste--;
        if (reste <= 0) {
            if (phase + 1 < ETATS.length) {
                changerPhase(phase + 1);
            } else {
                int a = scoreAtt();
                int d = scoreDef();
                finir(a > d ? "attaquants" : d > a ? "defenseurs" : null, "fin du temps");
                return;
            }
        }
        hud();
        majZone();
        verifierColis();
        if (colis != null && etat == Etat.EN_COURS) {
            // tant que le colis tient, les defenseurs marquent
            int iv = Math.max(1, plugin.getConfig().getInt("assaut.colis.defense-intervalle-secondes", 10));
            if (++colisVie % iv == 0) {
                ptsZoneDef += plugin.getConfig().getInt("assaut.colis.defense-points", 1);
            }
        }
        majMarqueurs();
        if (reste % 5 == 0) {
            carteNG();
        }
    }

    // ------------------------------------------------------------------ zone d'assaut NG, colis, carte

    private boolean zoneActive() {
        return plugin.getConfig().getBoolean("assaut.zone-ng.actif", true);
    }

    /** Cible de la zone : tout le champ de bataille pendant le PvP, le point a tenir pendant les captures. */
    private double[] cibleZone() {
        int c = capture();
        if (c >= 0) {
            return new double[] {capX, capZ, demiCapture(), demiCapture()};
        }
        int[] ch = champ();
        return new double[] {(ch[0] + ch[2]) / 2.0 + 0.5, (ch[1] + ch[3]) / 2.0 + 0.5, (ch[2] - ch[0]) / 2.0 + 0.5, (ch[3] - ch[1]) / 2.0 + 0.5};
    }

    private static double vers(double de, double a, double pas) {
        return Math.abs(a - de) <= pas ? a : de + Math.signum(a - de) * pas;
    }

    private void majZone() {
        World w = monde();
        if (w == null || !zoneActive()) {
            return;
        }
        if (capture() < 0) {
            // la zone rouge ne sert qu'aux phases de capture
            if (!Double.isNaN(zoneX)) {
                retirerZone();
            }
            return;
        }
        double[] c = cibleZone();
        if (true) {
            zoneX = c[0];
            zoneZ = c[1];
            zoneDX = c[2];
            zoneDZ = c[3];
        } else {
            // la zone se deplace et se resserre petit a petit (blocs par seconde)
            double v = Math.max(1, plugin.getConfig().getInt("assaut.zone-ng.vitesse", 4));
            zoneX = vers(zoneX, c[0], v);
            zoneZ = vers(zoneZ, c[1], v);
            zoneDX = vers(zoneDX, c[2], v);
            zoneDZ = vers(zoneDZ, c[3], v);
        }
        ObjetsNG.zone(w, "assaut", zoneX, zoneZ, zoneDX, zoneDZ, capY, 24, 3,
                Integer.parseInt(plugin.getConfig().getString("assaut.zone-ng.couleur", "910707"), 16));
    }

    private void retirerZone() {
        World w = monde();
        zoneX = Double.NaN;
        if (w != null) {
            ObjetsNG.retirerZone(w, "assaut");
        }
    }

    /** Un colis tombe sur le champ de bataille : celui qui le detruit rapporte des points et du materiel a son equipe. */
    private void lancerColis() {
        World w = monde();
        if (w == null || !plugin.getConfig().getBoolean("assaut.colis.actif", true)) {
            return;
        }
        retirerColis();
        java.util.Random r = new java.util.Random();
        int[] ch = champ();
        int mx = Math.min(5, (ch[2] - ch[0]) / 4), mz = Math.min(25, (ch[3] - ch[1]) / 4);
        int x = ch[0] + mx + r.nextInt(Math.max(1, ch[2] - ch[0] - 2 * mx));
        int z = ch[1] + mz + r.nextInt(Math.max(1, ch[3] - ch[1] - 2 * mz));
        int y0 = sol(x, z);
        Location l = new Location(w, x + 0.5, y0 + plugin.getConfig().getInt("assaut.colis.hauteur", 45), z + 0.5);
        colis = ObjetsNG.colis(l, plugin.getConfig().getString("assaut.colis.entite", "care_package"), plugin.getConfig().getDouble("assaut.colis.pv", 500));
        colisVie = 0;
        if (colis == null) {
            plugin.getLogger().warning("Assaut : colis impossible a faire apparaitre.");
            return;
        }
        colisLieu = new Location(w, x, y0, z);
        diffuser("[*banner id=\"assault\"] &6Un colis tombe du ciel en &e" + x + " " + z
                + "&6 ! &7Detruis-le pour gagner &e" + plugin.getConfig().getInt("assaut.colis.points", 50) + " points &7et du materiel.");
        for (Player p : joueurs(null)) {
            SSNG.titre(p, "&6Colis !", "&7Il tombe en &e" + x + " " + z, 50);
        }
    }

    private void verifierColis() {
        if (colis != null && (colis.isDead() || !colis.isValid())) {
            colis = null;
            colisLieu = null;
        } else if (colis != null) {
            colisLieu = colis.getLocation();
        }
    }

    private void retirerColis() {
        if (colis != null && !colis.isDead()) {
            colis.remove();
        }
        colis = null;
        colisLieu = null;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void colisFrappe(org.bukkit.event.entity.EntityDamageEvent e) {
        if (colis == null || !e.getEntity().getUniqueId().equals(colis.getUniqueId())) {
            return;
        }
        if (!(e instanceof org.bukkit.event.entity.EntityDamageByEntityEvent)) {
            // chute, feu, explosions... : le colis ne craint que les attaquants
            e.setCancelled(true);
            return;
        }
        Entity d = ((org.bukkit.event.entity.EntityDamageByEntityEvent) e).getDamager();
        if (d instanceof org.bukkit.entity.Projectile && ((org.bukkit.entity.Projectile) d).getShooter() instanceof Entity) {
            d = (Entity) ((org.bukkit.entity.Projectile) d).getShooter();
        }
        Boolean att = d instanceof Player ? equipes.get(((Player) d).getName()) : null;
        if (att == null || !att.booleanValue() || etat != Etat.EN_COURS) {
            e.setCancelled(true);
            if (d instanceof Player && att != null) {
                SSNG.titre((Player) d, "", "&9Defends le colis, ne le casse pas !", 20);
            }
        } else {
            e.setCancelled(false);
        }
    }

    @EventHandler
    public void colisDetruit(org.bukkit.event.entity.EntityDeathEvent e) {
        if (colis == null || !e.getEntity().getUniqueId().equals(colis.getUniqueId())) {
            return;
        }
        colis = null;
        colisLieu = null;
        e.getDrops().clear();
        e.setDroppedExp(0);
        Player tueur = e.getEntity().getKiller();
        Boolean att = tueur == null ? null : equipes.get(tueur.getName());
        if (att == null || etat != Etat.EN_COURS) {
            diffuser("[*banner id=\"assault\"] &7Le colis a ete detruit.");
            return;
        }
        int pts = plugin.getConfig().getInt("assaut.colis.points", 50);
        ptsZoneAtt += pts;
        att = Boolean.TRUE;
        List<String> contenu = plugin.getConfig().getStringList("assaut.colis.contenu");
        if (!plugin.getConfig().isSet("assaut.colis.contenu")) {
            contenu = Arrays.asList("322:0:2", "373:16421:2");
        }
        for (String s : contenu) {
            try {
                String[] p = s.split(":");
                e.getDrops().add(new org.bukkit.inventory.ItemStack(Integer.parseInt(p[0].trim()), p.length > 2 ? Integer.parseInt(p[2].trim()) : 1,
                        p.length > 1 ? Short.parseShort(p[1].trim()) : 0));
            } catch (Exception ex) {
                plugin.getLogger().warning("assaut.colis.contenu : objet invalide " + s);
            }
        }
        diffuser("[*banner id=\"" + (att.booleanValue() ? "attacker" : "defender") + "\"] &e" + tueur.getName() + " &7a recupere le colis : &e+"
                + pts + " points &7pour les " + (att.booleanValue() ? "&c" + nomEquipe(true) : "&9" + nomEquipe(false)) + "&7 !");
        hud();
    }

    /** Carte et minimap NG des participants : zone d'assaut, colis, points a capturer. */
    private void carteNG() {
        final Map<String, Object> donnees = new HashMap<String, Object>();
        donnees.put("concerned", Boolean.TRUE);
        Map<String, Object> a = new HashMap<String, Object>();
        a.put("attacker", nomEquipe(true));
        a.put("defender", nomEquipe(false));
        List<String> att = new ArrayList<String>();
        List<String> def = new ArrayList<String>();
        for (Map.Entry<String, Boolean> e : equipes.entrySet()) {
            (e.getValue().booleanValue() ? att : def).add(e.getKey());
        }
        a.put("attackerHelpers", att);
        a.put("defenderHelpers", def);
        a.put("areaX", Integer.valueOf((int) (Double.isNaN(zoneX) ? 0 : zoneX)));
        a.put("areaZ", Integer.valueOf((int) (Double.isNaN(zoneX) ? 0 : zoneZ)));
        a.put("packageX", Integer.valueOf(colisLieu == null ? 0 : colisLieu.getBlockX()));
        a.put("packageZ", Integer.valueOf(colisLieu == null ? 0 : colisLieu.getBlockZ()));
        List<Map<String, Object>> pois = new ArrayList<Map<String, Object>>();
        if (capture() >= 0) {
            Map<String, Object> poi = new HashMap<String, Object>();
            poi.put("category", "capture");
            poi.put("name", "Zone de capture");
            poi.put("x", Double.valueOf(capX));
            poi.put("z", Double.valueOf(capZ));
            pois.add(poi);
        }
        a.put("pois", pois);
        donnees.put("assaults", java.util.Collections.singletonList(a));
        final String json = new Gson().toJson(donnees);
        for (Player p : joueurs(null)) {
            SSNG.reseau().envoyer(p, "AssaultMapDataPacket", new ReseauNG.Ecrivain() {
                public void ecrire(DataOutputStream out) throws Exception {
                    out.writeUTF(json);
                }
            });
        }
    }

    // ------------------------------------------------------------------ marqueurs NG (zone et colis) des participants

    private static final String MARQ_ZONE = "ssng_assaut_zone";
    private static final String MARQ_COLIS = "ssng_assaut_colis";
    /** derniere position envoyee de chaque marqueur (null = absent chez les participants). */
    private String marqZone;
    private String marqColis;
    private final java.util.Set<String> ontMarqueurs = new java.util.HashSet<String>();

    private void envoyerMarqueur(Player p, String nom, String label, double x, double y, double z, String couleur) {
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
        SSNG.reseau().envoyer(p, "MarkerPacket", new ReseauNG.Ecrivain() {
            public void ecrire(DataOutputStream out) throws Exception {
                out.writeUTF(json);
            }
        });
    }

    private void retirerMarqueur(Player p, final String nom) {
        SSNG.reseau().envoyer(p, "MarkerRemovePacket", new ReseauNG.Ecrivain() {
            public void ecrire(DataOutputStream out) throws Exception {
                out.writeUTF(nom);
            }
        });
    }

    /** Met a jour les marqueurs : ajoutes quand la zone / le colis existent, retires des qu'ils disparaissent. */
    private void majMarqueurs() {
        String z = capture() >= 0 && !Double.isNaN(zoneX) ? capX + " " + capZ : null;
        String c = colisLieu != null ? colisLieu.getBlockX() / 3 + " " + colisLieu.getBlockY() / 3 + " " + colisLieu.getBlockZ() / 3 : null;
        boolean zChange = z == null ? marqZone != null : !z.equals(marqZone);
        boolean cChange = c == null ? marqColis != null : !c.equals(marqColis);
        for (Player p : joueurs(null)) {
            boolean nouveau = ontMarqueurs.add(p.getName());
            if (zChange || nouveau) {
                if (z != null) {
                    envoyerMarqueur(p, MARQ_ZONE, "Zone de capture", capX, capY, capZ, "FOUNDATIONS_ALLIANCES_RED_400");
                } else if (!nouveau) {
                    retirerMarqueur(p, MARQ_ZONE);
                }
            }
            if (cChange || nouveau) {
                if (c != null) {
                    envoyerMarqueur(p, MARQ_COLIS, "Colis", colisLieu.getX(), colisLieu.getY(), colisLieu.getZ(), "FOUNDATIONS_ALLIANCES_YELLOW_400");
                } else if (!nouveau) {
                    retirerMarqueur(p, MARQ_COLIS);
                }
            }
        }
        marqZone = z;
        marqColis = c;
    }

    private void retirerMarqueurs(Player p) {
        if (ontMarqueurs.remove(p.getName())) {
            retirerMarqueur(p, MARQ_ZONE);
            retirerMarqueur(p, MARQ_COLIS);
        }
    }

    private void effacerCarteNG(Player p) {
        retirerMarqueurs(p);
        SSNG.reseau().envoyer(p, "AssaultMapDataPacket", new ReseauNG.Ecrivain() {
            public void ecrire(DataOutputStream out) throws Exception {
                out.writeUTF("{\"concerned\":false,\"assaults\":[]}");
            }
        });
    }

    private void lancer() {
        World w = monde();
        List<String> l = new ArrayList<String>();
        for (String n : inscrits) {
            Player p = Bukkit.getPlayerExact(n);
            if (p != null) {
                l.add(n);
            }
        }
        if (l.size() < 2) {
            etat = Etat.ATTENTE;
            return;
        }
        Collections.shuffle(l);
        equipes.clear();
        ontMarqueurs.clear();
        marqZone = null;
        marqColis = null;
        kills.clear();
        ptsKillAtt = 0;
        ptsKillDef = 0;
        ptsZoneAtt = 0;
        ptsZoneDef = 0;
        controle = -1;
        creerTableau();
        etat = Etat.EN_COURS;
        debut = System.currentTimeMillis();
        for (int i = 0; i < l.size(); i++) {
            equipes.put(l.get(i), Boolean.valueOf(i % 2 == 0));
        }
        changerPhase(0);
        for (Player p : joueurs(null)) {
            equiper(p, true);
        }
        Bukkit.broadcastMessage(SSNG.c("[*banner id=\"assault\"] &7Un assaut commence : &c" + nomEquipe(true) + " &7("
                + joueurs(Boolean.TRUE).size() + ") contre &9" + nomEquipe(false) + " &7(" + joueurs(Boolean.FALSE).size() + ")."));
        hud();
    }

    private void changerPhase(int i) {
        int ancienne = capture();
        phase = i;
        reste = duree(i);
        controle = -1;
        if (capture() >= 0) {
            tirerCapture();
            majZone();
        }
        List<Integer> phasesColis = plugin.getConfig().getIntegerList("assaut.colis.phases");
        if (phasesColis.isEmpty()) {
            phasesColis = Arrays.asList(1, 3);
        }
        if (phasesColis.contains(Integer.valueOf(i))) {
            lancerColis();
        }
        int c = capture();
        for (Player p : joueurs(null)) {
            SSNG.titre(p, "&6" + NOMS_ETATS[i], c >= 0 ? "&fZone en " + capX + " " + capZ : i == 0 ? "&7Prepare-toi, le PvP arrive" : "&7Les kills rapportent des points", 60);
        }
        if (c >= 0) {
            diffuser("[*banner id=\"assault\"] &6" + NOMS_ETATS[i] + " &7: tiens la zone rouge en &e" + capX + " " + capZ
                    + " &7(" + pointsZone() + " pt / " + intervalleZone() + " s, " + temps(reste) + ").");
        } else if (i > 0) {
            diffuser("[*banner id=\"assault\"] &6" + NOMS_ETATS[i] + " &7: " + pointsKill() + " points par kill (" + temps(reste) + ").");
        } else {
            diffuser("[*banner id=\"assault\"] &6Preparation &7: " + temps(reste) + " avant le combat.");
        }
    }

    /** Teleporte au camp avec le kit de l'equipe. */
    private void equiper(Player p, boolean teleporter) {
        Boolean att = equipes.get(p.getName());
        if (att == null) {
            return;
        }
        if (teleporter) {
            p.teleport(camp(att.booleanValue()));
        }
        String kit = plugin.getConfig().getString(att.booleanValue() ? "assaut.kit-attaquants" : "assaut.kit-defenseurs",
                att.booleanValue() ? "attaquant" : "defenseur");
        if (!plugin.arene().donnerKit(p, kit)) {
            Inventaire.vider(p);
        }
        Inventaire.soigner(p);
        if (tableau != null) {
            p.setScoreboard(tableau);
            Team t = tableau.getTeam(att.booleanValue() ? "att" : "def");
            if (t != null && !t.hasPlayer(p)) {
                t.addPlayer(p);
            }
        }
    }

    private void creerTableau() {
        try {
            tableau = Bukkit.getScoreboardManager().getNewScoreboard();
            Team a = tableau.registerNewTeam("att");
            a.setPrefix(SSNG.c("&c"));
            a.setAllowFriendlyFire(false);
            a.setCanSeeFriendlyInvisibles(true);
            Team d = tableau.registerNewTeam("def");
            d.setPrefix(SSNG.c("&9"));
            d.setAllowFriendlyFire(false);
            d.setCanSeeFriendlyInvisibles(true);
            if (plugin.getConfig().getBoolean("assaut.tableau-lateral", true)) {
                Objective o = tableau.registerNewObjective("assaut", "dummy");
                o.setDisplayName(SSNG.c("&4&lAssaut"));
                o.setDisplaySlot(DisplaySlot.SIDEBAR);
            }
        } catch (Throwable t) {
            tableau = null;
        }
    }

    @SuppressWarnings("deprecation")
    private void hud() {
        if (tableau != null && tableau.getObjective("assaut") != null) {
            Objective o = tableau.getObjective("assaut");
            o.getScore(Bukkit.getOfflinePlayer(seize(SSNG.c("&cPts " + nomEquipe(true))))).setScore(scoreAtt());
            o.getScore(Bukkit.getOfflinePlayer(seize(SSNG.c("&9Pts " + nomEquipe(false))))).setScore(scoreDef());
            o.getScore(Bukkit.getOfflinePlayer(SSNG.c("&ePhase"))).setScore(phase);
            o.getScore(Bukkit.getOfflinePlayer(SSNG.c("&7Secondes"))).setScore(reste);
        }
        final Map<String, String> m = new HashMap<String, String>();
        m.put("currentState", ETATS[phase]);
        m.put("remainingTimeInCurrentState", temps(reste));
        m.put("currentStateNumber", String.valueOf(phase));
        m.put("progressTime", String.valueOf(1f - (float) reste / duree(phase)));
        m.put("multiRatioAttackersByDefenders", "1.0");
        m.put("attackerFactionName", nomEquipe(true));
        m.put("defenderFactionName", nomEquipe(false));
        m.put("attackerScore", String.valueOf(scoreAtt()));
        m.put("defenderScore", String.valueOf(scoreDef()));
        m.put("attackerPlayersCount", String.valueOf(joueurs(Boolean.TRUE).size()));
        m.put("defenderPlayersCount", String.valueOf(joueurs(Boolean.FALSE).size()));
        final String json = new Gson().toJson(m);
        for (Player p : joueurs(null)) {
            SSNG.reseau().envoyer(p, "AssaultDataPacket", new ReseauNG.Ecrivain() {
                public void ecrire(DataOutputStream out) throws Exception {
                    out.writeUTF(json);
                }
            });
        }
    }

    private void effacerHud(Player p) {
        SSNG.reseau().envoyer(p, "AssaultDataPacket", new ReseauNG.Ecrivain() {
            public void ecrire(DataOutputStream out) throws Exception {
                out.writeUTF("{}");
            }
        });
        p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    /** gagnant : "attaquants", "defenseurs" ou null (egalite). */
    private void finir(String gagnant, String raison) {
        World w = monde();
        retirerZone();
        retirerColis();
        for (Player p : joueurs(null)) {
            effacerCarteNG(p);
        }
        long duree = System.currentTimeMillis() - debut;
        String titre = gagnant == null ? "&7Egalite" : gagnant.equals("attaquants") ? "&cVictoire des " + nomEquipe(true) : "&9Victoire des " + nomEquipe(false);
        Bukkit.broadcastMessage(SSNG.c("[*banner id=\"assault\"] &6Fin de l'assaut &7(" + raison + ") : " + titre
                + " &7- &c" + scoreAtt() + " &7a &9" + scoreDef()));
        for (Player p : joueurs(null)) {
            final boolean att = equipes.get(p.getName()).booleanValue();
            String resultat = gagnant == null ? "equality" : (gagnant.equals("attaquants") == att ? "victory" : "defeat");
            plugin.stats().ajouter(p.getName(), "assaut-participations", 1);
            if (!resultat.equals("equality")) {
                plugin.stats().ajouter(p.getName(), resultat.equals("victory") ? "assaut-victoires" : "assaut-defaites", 1);
            }
            final Map<String, String> m = new HashMap<String, String>();
            m.put("result", resultat);
            m.put("score_total", String.valueOf(att ? scoreAtt() : scoreDef()));
            m.put("score_mode", String.valueOf(att ? ptsZoneAtt : ptsZoneDef));
            m.put("score_kill", String.valueOf(att ? ptsKillAtt : ptsKillDef));
            m.put("score_missile", "0");
            m.put("mmr", "0");
            m.put("duration", String.valueOf(duree));
            Integer k = kills.get(p.getName());
            m.put("kills", String.valueOf(k == null ? 0 : k.intValue()));
            m.put("missile_blocks_destroyed", "0");
            m.put("missile_blocks_destroyed_own", "0");
            effacerHud(p);
            SSNG.reseau().envoyer(p, "AssaultResultDataPacket", new ReseauNG.Ecrivain() {
                public void ecrire(DataOutputStream out) throws Exception {
                    out.writeUTF(new Gson().toJson(m));
                }
            });
            SSNG.titre(p, titre, "&c" + scoreAtt() + " &7- &9" + scoreDef(), 80);
            if (!p.isDead()) {
                Inventaire.vider(p);
                Inventaire.soigner(p);
                p.teleport(attente());
            }
        }
        java.util.Map<String, Object> ev = new java.util.LinkedHashMap<String, Object>();
        ev.put("gagnant", gagnant == null ? "egalite" : gagnant);
        ev.put("score_attaquants", Integer.valueOf(scoreAtt()));
        ev.put("score_defenseurs", Integer.valueOf(scoreDef()));
        ev.put("duree_ms", Long.valueOf(duree));
        ev.put("raison", raison);
        plugin.getLogger().info("Fin de l'assaut (" + raison + ") : "
                + (gagnant == null ? "egalite" : "victoire des " + nomEquipe(gagnant.equals("attaquants"))) + " " + scoreAtt() + " - " + scoreDef() + " " + ev);
        equipes.clear();
        ontMarqueurs.clear();
        marqZone = null;
        marqColis = null;
        tableau = null;
        etat = Etat.PAUSE;
        pause = pauseSecondes();
        diffuser("[*banner id=\"assault\"] &7Prochain assaut dans &e" + temps(pause) + "&7. Tu restes inscrit (&f/assaut quitter&7 pour arreter).");
    }

    private void retirer(Player p, boolean teleporter) {
        inscrits.remove(p.getName());
        if (equipes.remove(p.getName()) != null) {
            if (tableau != null) {
                for (Team t : tableau.getTeams()) {
                    t.removePlayer(p);
                }
            }
            effacerHud(p);
            effacerCarteNG(p);
            if (teleporter && p.isOnline() && !p.isDead()) {
                Inventaire.vider(p);
                p.teleport(attente());
            }
        }
    }

    // ------------------------------------------------------------------ evenements

    @EventHandler(priority = EventPriority.HIGHEST)
    public void mort(PlayerDeathEvent e) {
        Player p = e.getEntity();
        if (!participe(p)) {
            return;
        }
        e.getDrops().clear();
        e.setDroppedExp(0);
        e.setDeathMessage(null);
        boolean att = equipes.get(p.getName()).booleanValue();
        Player tueur = p.getKiller();
        if (tueur != null && participe(tueur) && equipes.get(tueur.getName()).booleanValue() != att && phase > 0) {
            if (att) {
                ptsKillDef += pointsKill();
            } else {
                ptsKillAtt += pointsKill();
            }
            Integer k = kills.get(tueur.getName());
            kills.put(tueur.getName(), Integer.valueOf(k == null ? 1 : k.intValue() + 1));
            plugin.stats().ajouter(tueur.getName(), "assaut-kills", 1);
            diffuser("[*banner id=\"" + (att ? "defender" : "attacker") + "\"] " + (att ? "&9" : "&c") + tueur.getName()
                    + " &7a tue [*banner id=\"" + (att ? "attacker" : "defender") + "\"] " + (att ? "&c" : "&9") + p.getName()
                    + " &8(+" + pointsKill() + ")");
        } else {
            diffuser("[*banner id=\"" + (att ? "attacker" : "defender") + "\"] " + (att ? "&c" : "&9") + p.getName() + " &7est mort.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void reapparition(PlayerRespawnEvent e) {
        final Player p = e.getPlayer();
        if (!participe(p)) {
            return;
        }
        e.setRespawnLocation(camp(equipes.get(p.getName()).booleanValue()));
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            public void run() {
                if (participe(p)) {
                    equiper(p, false);
                }
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void degats(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player)) {
            return;
        }
        Entity source = e.getDamager();
        if (source instanceof Projectile && ((Projectile) source).getShooter() instanceof Player) {
            source = (Entity) ((Projectile) source).getShooter();
        }
        if (!(source instanceof Player)) {
            return;
        }
        Player a = (Player) source;
        Player v = (Player) e.getEntity();
        boolean pa = participe(a);
        boolean pv = participe(v);
        if (!pa && !pv) {
            return;
        }
        if (pa != pv || phase == 0 || equipes.get(a.getName()).equals(equipes.get(v.getName()))) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void quitter(PlayerQuitEvent e) {
        retirer(e.getPlayer(), false);
        if (e.getPlayer().getScoreboard() != Bukkit.getScoreboardManager().getMainScoreboard()) {
            e.getPlayer().setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    @EventHandler
    public void changerMonde(PlayerChangedWorldEvent e) {
        if (inscrits.contains(e.getPlayer().getName()) || equipes.containsKey(e.getPlayer().getName())) {
            retirer(e.getPlayer(), false);
            SSNG.msg(e.getPlayer(), "&7Tu ne participes plus a l'assaut.");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void commandes(PlayerCommandPreprocessEvent e) {
        if (!participe(e.getPlayer()) || SSNG.admin(e.getPlayer())) {
            return;
        }
        String cmd = e.getMessage().substring(1).split("\\s+")[0].toLowerCase();
        if (!COMMANDES_AUTORISEES.contains(cmd)) {
            e.setCancelled(true);
            SSNG.msg(e.getPlayer(), "&cPas pendant l'assaut. &7(/assaut quitter pour partir)");
        }
    }

    // ------------------------------------------------------------------ /assaut

    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        String sous = a.length == 0 ? "info" : a[0].toLowerCase();
        if (sous.equals("info")) {
            if (etat == Etat.EN_COURS) {
                SSNG.msg(s, "&6Assaut en cours : &e" + NOMS_ETATS[phase] + " &7(" + temps(reste) + ") - &c" + nomEquipe(true) + " "
                        + scoreAtt() + " &7/ &9" + nomEquipe(false) + " " + scoreDef());
            } else if (etat == Etat.PAUSE) {
                SSNG.msg(s, "&6Prochain assaut dans &e" + temps(pause) + " &7(" + inscrits.size() + " inscrit(s)).");
            } else {
                SSNG.msg(s, "&6En attente de joueurs : &e" + inscrits.size() + "/" + minJoueurs() + "&7. &f/assaut rejoindre");
            }
            return true;
        }
        if (sous.equals("testng") && SSNG.admin(s)) {
            // verifie la zone et le colis NG sans lancer d'assaut
            World w = monde();
            if (a.length > 1 && a[1].equalsIgnoreCase("fin")) {
                ObjetsNG.retirerZone(w, "test");
                int n = 0;
                for (Entity e : w.getEntities()) {
                    if (e.getType().name().toLowerCase().contains("care_package") && (colis == null || !e.getUniqueId().equals(colis.getUniqueId()))) {
                        e.remove();
                        n++;
                    }
                }
                SSNG.msg(s, "&7Test termine : zone retiree, " + n + " colis enleve(s).");
                return true;
            }
            if (w == null) {
                SSNG.msg(s, "&cArene non reglee : /assautadmin");
                return true;
            }
            int[] ch = champ();
            int mx = (ch[0] + ch[2]) / 2, mz = (ch[1] + ch[3]) / 2;
            boolean z = ObjetsNG.zone(w, "test", mx + 0.5, mz + 0.5, 5, 5, sol(mx, mz), 24, 3, 0x910707);
            Entity e = ObjetsNG.colis(new Location(w, mx + 0.5, sol(mx, mz) + 45, mz + 0.5), plugin.getConfig().getString("assaut.colis.entite", "care_package"), 500);
            SSNG.msg(s, "&7Zone NG : " + (z ? "&aOK" : "&cimpossible") + " &7- colis : " + (e != null ? "&aOK (" + e.getType() + ")" : "&cimpossible")
                    + " &7- &f/assaut testng fin &7pour enlever");
            return true;
        }
        if (sous.equals("start") || sous.equals("stop")) {
            if (!SSNG.admin(s)) {
                SSNG.msg(s, "&cReserve aux admins.");
                return true;
            }
            if (sous.equals("stop")) {
                if (etat == Etat.EN_COURS) {
                    finir(null, "arrete par un admin");
                }
                etat = Etat.ATTENTE;
                SSNG.msg(s, "&7Assaut arrete. Il reprendra quand assez de joueurs seront inscrits.");
                return true;
            }
            if (etat == Etat.EN_COURS) {
                SSNG.msg(s, "&cUn assaut est deja en cours.");
            } else if (inscrits.size() < 2) {
                SSNG.msg(s, "&cIl faut au moins 2 joueurs inscrits.");
            } else {
                lancer();
            }
            return true;
        }
        if (!(s instanceof Player)) {
            SSNG.msg(s, "&c/assaut info | start | stop");
            return true;
        }
        Player p = (Player) s;
        if (sous.equals("quitter")) {
            boolean etait = inscrits.contains(p.getName()) || equipes.containsKey(p.getName());
            retirer(p, true);
            SSNG.msg(p, etait ? "&7Tu ne participes plus a l'assaut." : "&cTu n'es pas inscrit.");
            return true;
        }
        if (sous.equals("rejoindre")) {
            if (monde() == null) {
                SSNG.msg(p, "&cL'arene d'assaut n'est pas encore reglee (admins : /assautadmin).");
                return true;
            }
            if (participe(p)) {
                SSNG.msg(p, "&cTu participes deja.");
                return true;
            }
            inscrits.add(p.getName());
            if (etat == Etat.EN_COURS) {
                boolean att = joueurs(Boolean.TRUE).size() <= joueurs(Boolean.FALSE).size();
                equipes.put(p.getName(), Boolean.valueOf(att));
                equiper(p, true);
                diffuser("[*banner id=\"" + (att ? "attacker" : "defender") + "\"] &e" + p.getName() + " &7rejoint les "
                        + (att ? "&c" + nomEquipe(true) : "&9" + nomEquipe(false)) + "&7.");
                hud();
            } else {
                if (p.getWorld() != monde() || !plugin.arene().dansArene(p.getLocation())) {
                    p.teleport(attente());
                }
                SSNG.msg(p, "&aInscrit a l'assaut ! " + (etat == Etat.PAUSE ? "&7Debut dans &e" + temps(pause)
                        : "&7Joueurs : &e" + inscrits.size() + "/" + minJoueurs()));
            }
            return true;
        }
        SSNG.msg(p, "&6/assaut rejoindre &7| &6quitter &7| &6info" + (SSNG.admin(p) ? " &7| &6start &7| &6stop" : ""));
        return true;
    }
}
