package fr.armersindustries.ssng.assaut;

import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import com.google.gson.Gson;

import fr.armersindustries.ssng.core.ObjetsNG;
import fr.armersindustries.ssng.core.ReseauNG;
import fr.armersindustries.ssng.core.SSNG;

/**
 * Nations d'evenement et assaut grandeur nature (map Evenement ou tout autre monde).
 * - Le haut staff cree des nations (/nation creer), chacune avec un chef (haut staff), des membres et un territoire.
 * - Un chef lance un assaut sur une autre nation : le combat a lieu sur le territoire du defenseur ;
 *   la zone de capture (10x10, 1 pt / 5 s) et les colis (defenseurs +1 pt / 10 s tant qu'il tient,
 *   50 pts aux attaquants qui le detruisent) tombent au hasard sur ce territoire ; 5 pts par kill.
 * - Pendant l'assaut, un mur invisible garde chacun dans le territoire des deux nations.
 */
public class GrandAssaut implements Listener, CommandExecutor {

    private static final String N = "nations.";

    private final AssautNG plugin;
    private final org.bukkit.configuration.file.YamlConfiguration donnees;
    private final Random hasard = new Random();

    // assaut en cours
    private String att;
    private String def;
    private World monde;
    private int tache = -1;
    private int reste;
    private int ptsAtt;
    private int ptsDef;
    private int capX;
    private int capZ;
    private int capY;
    private int tenueAtt;
    private int tenueDef;
    private int prochaineCapture;
    private int prochainColis;
    private Entity colis;
    private int colisVie;
    private final Set<String> marques = new HashSet<String>();

    public GrandAssaut(AssautNG plugin) {
        this.plugin = plugin;
        this.donnees = SSNG.charger(plugin, "nations.yml");
    }

    // ------------------------------------------------------------------ nations (donnees.yml)

    private ConfigurationSection nation(String id) {
        return donnees.getConfigurationSection(N + id.toLowerCase());
    }

    public List<String> idsNations() {
        return ids();
    }

    private List<String> ids() {
        ConfigurationSection s = donnees.getConfigurationSection("nations");
        return s == null ? new ArrayList<String>() : new ArrayList<String>(s.getKeys(false));
    }

    private String nom(String id) {
        ConfigurationSection n = nation(id);
        return n == null ? id : n.getString("nom", id);
    }

    private String couleur(String id) {
        ConfigurationSection n = nation(id);
        return n == null ? "&f" : n.getString("couleur", "&f");
    }

    /** Nation du joueur (chef ou membre), ou null. */
    public String nationDe(String joueur) {
        for (String id : ids()) {
            ConfigurationSection n = nation(id);
            if (n.getString("chef", "").equalsIgnoreCase(joueur)) {
                return id;
            }
            for (String m : n.getStringList("membres")) {
                if (m.equalsIgnoreCase(joueur)) {
                    return id;
                }
            }
        }
        return null;
    }

    /** Territoire {x1, z1, x2, z2} ou null. */
    private int[] territoire(String id) {
        ConfigurationSection n = nation(id);
        String v = n == null ? null : n.getString("territoire", null);
        if (v == null) {
            return null;
        }
        String[] p = v.split(",");
        int x1 = Integer.parseInt(p[0]), z1 = Integer.parseInt(p[1]), x2 = Integer.parseInt(p[2]), z2 = Integer.parseInt(p[3]);
        return new int[] {Math.min(x1, x2), Math.min(z1, z2), Math.max(x1, x2), Math.max(z1, z2)};
    }

    private World mondeDe(String id) {
        ConfigurationSection n = nation(id);
        return n == null ? null : Bukkit.getWorld(n.getString("monde", ""));
    }

    private static boolean dans(int[] t, double x, double z) {
        return t != null && x >= t[0] && x <= t[2] + 1 && z >= t[1] && z <= t[3] + 1;
    }

    private boolean hautStaff(CommandSender s) {
        return SSNG.hautStaff(s);
    }

    private boolean chefOuStaff(CommandSender s, String id) {
        ConfigurationSection n = nation(id);
        return SSNG.admin(s) || (hautStaff(s) && n != null && n.getString("chef", "").equalsIgnoreCase(s.getName()));
    }

    private void sauver() {
        SSNG.sauver(plugin, "nations.yml", donnees);
    }

    // ------------------------------------------------------------------ /nation

    private void aide(CommandSender s) {
        SSNG.msg(s, "&6/nation &7- nations d'evenement :");
        SSNG.msg(s, "&e/nation liste &7| &e/nation info <id>");
        SSNG.msg(s, "&e/nation creer <id> <chef> <couleur &c&&ec> [nom] &7(haut staff) &8| &e/nation supprimer <id>");
        SSNG.msg(s, "&e/nation territoire <id> pos1|pos2 &7: coins du territoire (a ta position, dans le monde de l'event)");
        SSNG.msg(s, "&e/nation ajouter|retirer <id> <joueur> &7: membres");
        SSNG.msg(s, "&e/nation assaut <attaquant> <defenseur> &7: lance l'assaut grandeur nature &8| &e/nation stop");
    }

    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (a.length == 0) {
            aide(s);
            return true;
        }
        String q = a[0].toLowerCase();
        if (q.equals("liste")) {
            List<String> l = ids();
            SSNG.msg(s, "&6Nations : " + (l.isEmpty() ? "&7aucune" : ""));
            for (String id : l) {
                ConfigurationSection n = nation(id);
                SSNG.msg(s, couleur(id) + nom(id) + " &7(" + id + ") - chef &f" + n.getString("chef", "?") + " &7- "
                        + n.getStringList("membres").size() + " membre(s)" + (n.isSet("territoire") ? "" : " &c(sans territoire)"));
            }
            return true;
        }
        if (q.equals("info") && a.length > 1) {
            ConfigurationSection n = nation(a[1]);
            if (n == null) {
                SSNG.msg(s, "&cNation inconnue.");
                return true;
            }
            SSNG.msg(s, couleur(a[1]) + nom(a[1]) + " &7- chef &f" + n.getString("chef"));
            SSNG.msg(s, "&7Monde : &f" + n.getString("monde", "?") + " &7- territoire : &f" + n.getString("territoire", "aucun"));
            SSNG.msg(s, "&7Membres : &f" + n.getStringList("membres"));
            return true;
        }
        if (q.equals("stop")) {
            if (!hautStaff(s)) {
                SSNG.msg(s, "&cReserve au haut staff.");
                return true;
            }
            if (att == null) {
                SSNG.msg(s, "&7Aucun assaut de nations en cours.");
            } else {
                finir("arrete par " + s.getName());
            }
            return true;
        }
        if (q.equals("creer") && a.length >= 3) {
            if (!hautStaff(s)) {
                SSNG.msg(s, "&cReserve au haut staff.");
                return true;
            }
            String id = a[1].toLowerCase().replaceAll("[^a-z0-9_-]", "");
            if (id.isEmpty() || nation(id) != null) {
                SSNG.msg(s, "&cIdentifiant invalide ou deja pris.");
                return true;
            }
            boolean aCouleur = a.length > 3 && a[3].matches("&[0-9a-fA-F]");
            String col = aCouleur ? a[3] : "&e";
            StringBuilder nm = new StringBuilder();
            for (int i = aCouleur ? 4 : 3; i < a.length; i++) {
                nm.append(nm.length() > 0 ? " " : "").append(a[i]);
            }
            String b = N + id + ".";
            donnees.set(b + "nom", nm.length() > 0 ? nm.toString().trim() : a[1]);
            donnees.set(b + "chef", a[2]);
            donnees.set(b + "couleur", col);
            donnees.set(b + "membres", new ArrayList<String>());
            if (s instanceof Player) {
                donnees.set(b + "monde", ((Player) s).getWorld().getName());
            }
            sauver();
            SSNG.msg(s, "&aNation " + col + nom(id) + " &acreee (chef : &f" + a[2] + "&a). Place son territoire : &e/nation territoire " + id + " pos1");
            return true;
        }
        if (q.equals("supprimer") && a.length >= 2) {
            if (!hautStaff(s)) {
                SSNG.msg(s, "&cReserve au haut staff.");
                return true;
            }
            donnees.set(N + a[1].toLowerCase(), null);
            sauver();
            SSNG.msg(s, "&7Nation supprimee.");
            return true;
        }
        if (q.equals("territoire") && a.length >= 3 && s instanceof Player) {
            String id = a[1].toLowerCase();
            if (nation(id) == null || !chefOuStaff(s, id)) {
                SSNG.msg(s, "&cNation inconnue ou tu n'en es pas le chef.");
                return true;
            }
            Player p = (Player) s;
            String b = N + id + ".";
            String k = a[2].toLowerCase().equals("pos2") ? "pos2" : "pos1";
            donnees.set(b + k, p.getLocation().getBlockX() + "," + p.getLocation().getBlockZ());
            donnees.set(b + "monde", p.getWorld().getName());
            String p1 = donnees.getString(b + "pos1", null), p2 = donnees.getString(b + "pos2", null);
            if (p1 != null && p2 != null) {
                donnees.set(b + "territoire", p1 + "," + p2);
                SSNG.msg(s, "&aTerritoire de " + nom(id) + " : &e" + p1 + " -> " + p2);
                montrer(id, p.getWorld());
            } else {
                SSNG.msg(s, "&7" + k + " notee. Place l'autre coin.");
            }
            sauver();
            return true;
        }
        if ((q.equals("ajouter") || q.equals("retirer")) && a.length >= 3) {
            String id = a[1].toLowerCase();
            if (nation(id) == null || !chefOuStaff(s, id)) {
                SSNG.msg(s, "&cNation inconnue ou tu n'en es pas le chef.");
                return true;
            }
            List<String> m = new ArrayList<String>(nation(id).getStringList("membres"));
            if (q.equals("ajouter")) {
                String deja = nationDe(a[2]);
                if (deja != null) {
                    SSNG.msg(s, "&c" + a[2] + " est deja dans " + nom(deja) + ".");
                    return true;
                }
                m.add(a[2]);
            } else {
                for (int i = m.size() - 1; i >= 0; i--) {
                    if (m.get(i).equalsIgnoreCase(a[2])) {
                        m.remove(i);
                    }
                }
            }
            donnees.set(N + id + ".membres", m);
            sauver();
            SSNG.msg(s, "&a" + nom(id) + " : " + m.size() + " membre(s).");
            Player j = Bukkit.getPlayerExact(a[2]);
            if (j != null) {
                SSNG.msg(j, q.equals("ajouter") ? "&aTu rejoins la nation " + couleur(id) + nom(id) + "&a." : "&7Tu quittes la nation " + nom(id) + ".");
            }
            return true;
        }
        if (q.equals("assaut") && a.length >= 2) {
            String at, de;
            if (a.length >= 3) {
                at = a[1].toLowerCase();
                de = a[2].toLowerCase();
            } else {
                at = nationDe(s.getName());
                de = a[1].toLowerCase();
            }
            if (at == null || nation(at) == null || nation(de) == null || at.equals(de)) {
                SSNG.msg(s, "&cNations invalides. &7/nation assaut <attaquant> <defenseur>");
                return true;
            }
            if (!chefOuStaff(s, at)) {
                SSNG.msg(s, "&cSeul le chef de " + nom(at) + " ou le haut staff peut lancer cet assaut.");
                return true;
            }
            lancer(s, at, de);
            return true;
        }
        aide(s);
        return true;
    }

    /** Montre le territoire avec la bordure NG (lignes rouges) pendant 30 s. */
    private void montrer(final String id, final World w) {
        final int[] t = territoire(id);
        if (t == null || w == null) {
            return;
        }
        ObjetsNG.zone(w, "nation_" + id, (t[0] + t[2] + 1) / 2.0, (t[1] + t[3] + 1) / 2.0, (t[2] - t[0] + 1) / 2.0, (t[3] - t[1] + 1) / 2.0,
                w.getHighestBlockYAt((t[0] + t[2]) / 2, (t[1] + t[3]) / 2), 40, 20, 0x2563EB);
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            public void run() {
                if (att == null || (!id.equals(att) && !id.equals(def))) {
                    ObjetsNG.retirerZone(w, "nation_" + id);
                }
            }
        }, 600L);
    }

    // ------------------------------------------------------------------ deroulement

    private List<Player> joueurs(String id) {
        List<Player> l = new ArrayList<Player>();
        if (id == null || monde == null) {
            return l;
        }
        for (Player p : monde.getPlayers()) {
            if (id.equals(nationDe(p.getName()))) {
                l.add(p);
            }
        }
        return l;
    }

    private List<Player> tous() {
        List<Player> l = joueurs(att);
        l.addAll(joueurs(def));
        return l;
    }

    private void diffuser(String m) {
        for (Player p : monde.getPlayers()) {
            SSNG.msg(p, m);
        }
    }

    private Location pointDans(int[] t) {
        int x = t[0] + hasard.nextInt(Math.max(1, t[2] - t[0] + 1));
        int z = t[1] + hasard.nextInt(Math.max(1, t[3] - t[1] + 1));
        monde.loadChunk(x >> 4, z >> 4);
        return new Location(monde, x + 0.5, monde.getHighestBlockYAt(x, z) + 0.5, z + 0.5);
    }

    private void lancer(CommandSender s, String at, String de) {
        if (att != null) {
            SSNG.msg(s, "&cUn assaut de nations est deja en cours (/nation stop).");
            return;
        }
        int[] ta = territoire(at), td = territoire(de);
        World wa = mondeDe(at), wd = mondeDe(de);
        if (ta == null || td == null || wa == null || wa != wd) {
            SSNG.msg(s, "&cLes deux nations doivent avoir un territoire dans le meme monde.");
            return;
        }
        att = at;
        def = de;
        monde = wd;
        if (joueurs(at).isEmpty() || joueurs(de).isEmpty()) {
            SSNG.msg(s, "&cIl faut au moins un joueur de chaque nation dans le monde " + monde.getName() + ".");
            att = null;
            def = null;
            return;
        }
        reste = Math.max(60, plugin.getConfig().getInt("nations.assaut.duree-secondes", 900));
        ptsAtt = 0;
        ptsDef = 0;
        tenueAtt = 0;
        tenueDef = 0;
        prochaineCapture = 30;
        prochainColis = 90;
        capX = Integer.MIN_VALUE;
        // murs : bordures NG autour des deux territoires
        int y = monde.getHighestBlockYAt((td[0] + td[2]) / 2, (td[1] + td[3]) / 2);
        ObjetsNG.zone(monde, "nation_" + at, (ta[0] + ta[2] + 1) / 2.0, (ta[1] + ta[3] + 1) / 2.0, (ta[2] - ta[0] + 1) / 2.0, (ta[3] - ta[1] + 1) / 2.0, y, 60, 40, 0x2563EB);
        ObjetsNG.zone(monde, "nation_" + de, (td[0] + td[2] + 1) / 2.0, (td[1] + td[3] + 1) / 2.0, (td[2] - td[0] + 1) / 2.0, (td[3] - td[1] + 1) / 2.0, y, 60, 40, 0x2563EB);
        // chacun chez soi
        for (Player p : joueurs(at)) {
            if (!dans(ta, p.getLocation().getX(), p.getLocation().getZ())) {
                p.teleport(pointDans(ta));
            }
            SSNG.titre(p, "&c&lASSAUT !", "&7Tu attaques " + couleur(de) + nom(de), 80);
        }
        for (Player p : joueurs(de)) {
            if (!dans(td, p.getLocation().getX(), p.getLocation().getZ())) {
                p.teleport(pointDans(td));
            }
            SSNG.titre(p, "&9&lDEFENDS !", couleur(at) + nom(at) + " &7attaque ton territoire", 80);
        }
        Bukkit.broadcastMessage(SSNG.c("[*banner id=\"assault\"] " + couleur(at) + nom(at) + " &7lance un assaut sur " + couleur(de) + nom(de) + " &7!"));
        tache = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
            public void run() {
                tic();
            }
        }, 20L, 20L);
    }

    private void tic() {
        if (att == null) {
            return;
        }
        if (joueurs(att).isEmpty() || joueurs(def).isEmpty()) {
            finir(joueurs(att).isEmpty() ? "plus d'attaquants" : "plus de defenseurs");
            return;
        }
        int[] td = territoire(def);
        int demi = Math.max(1, plugin.getConfig().getInt("assaut.capture.taille", 10) / 2);
        // zone de capture : nouvelle position regulierement, au hasard sur le territoire du defenseur
        if (--prochaineCapture <= 0) {
            capX = td[0] + demi + hasard.nextInt(Math.max(1, td[2] - td[0] - 2 * demi));
            capZ = td[1] + demi + hasard.nextInt(Math.max(1, td[3] - td[1] - 2 * demi));
            capY = monde.getHighestBlockYAt(capX, capZ);
            ObjetsNG.zone(monde, "nation_capture", capX, capZ, demi, demi, capY, 24, 3, 0x910707);
            prochaineCapture = Math.max(30, plugin.getConfig().getInt("nations.assaut.capture-secondes", 180));
            diffuser("[*banner id=\"assault\"] &6Nouvelle zone de capture en &e" + capX + " " + capZ + "&6 !");
            marqueurs(true, false);
        }
        if (capX != Integer.MIN_VALUE) {
            int a = 0, d = 0;
            for (Player p : tous()) {
                Location l = p.getLocation();
                if (!p.isDead() && Math.abs(l.getX() - capX) <= demi && Math.abs(l.getZ() - capZ) <= demi && Math.abs(l.getY() - capY) < 16) {
                    if (att.equals(nationDe(p.getName()))) {
                        a++;
                    } else {
                        d++;
                    }
                }
            }
            int iv = Math.max(1, plugin.getConfig().getInt("assaut.capture.intervalle-secondes", 5));
            if (a > d && ++tenueAtt % iv == 0) {
                ptsAtt += plugin.getConfig().getInt("assaut.capture.points", 1);
            } else if (d > a && ++tenueDef % iv == 0) {
                ptsDef += plugin.getConfig().getInt("assaut.capture.points", 1);
            }
        }
        // colis
        if (colis != null && (colis.isDead() || !colis.isValid())) {
            colis = null;
            marqueurs(false, true);
        }
        if (colis == null && --prochainColis <= 0) {
            Location l = pointDans(td);
            l.setY(l.getY() + plugin.getConfig().getInt("assaut.colis.hauteur", 45));
            colis = ObjetsNG.colis(l, plugin.getConfig().getString("assaut.colis.entite", "care_package"), plugin.getConfig().getDouble("assaut.colis.pv", 500));
            colisVie = 0;
            prochainColis = Math.max(60, plugin.getConfig().getInt("nations.assaut.colis-secondes", 240));
            if (colis != null) {
                diffuser("[*banner id=\"assault\"] &6Un colis tombe en &e" + l.getBlockX() + " " + l.getBlockZ() + "&6 ! &7Les attaquants doivent le detruire.");
                marqueurs(false, true);
            }
        }
        if (colis != null) {
            int iv = Math.max(1, plugin.getConfig().getInt("assaut.colis.defense-intervalle-secondes", 10));
            if (++colisVie % iv == 0) {
                ptsDef += plugin.getConfig().getInt("assaut.colis.defense-points", 1);
            }
        }
        if (--reste <= 0) {
            finir("fin du temps");
            return;
        }
        if (reste % 5 == 0) {
            String hud = couleur(att) + nom(att) + " &f" + ptsAtt + " &7- &f" + ptsDef + " " + couleur(def) + nom(def) + " &8| &7" + (reste / 60) + ":"
                    + String.format("%02d", reste % 60);
            for (Player p : tous()) {
                SSNG.titre(p, "", hud, 30);
            }
        }
    }

    private void finir(String raison) {
        if (tache >= 0) {
            Bukkit.getScheduler().cancelTask(tache);
            tache = -1;
        }
        if (monde != null) {
            ObjetsNG.retirerZone(monde, "nation_capture");
            ObjetsNG.retirerZone(monde, "nation_" + att);
            ObjetsNG.retirerZone(monde, "nation_" + def);
            for (Player p : tous()) {
                retirerMarqueurs(p);
            }
        }
        if (colis != null && !colis.isDead()) {
            colis.remove();
        }
        colis = null;
        String g = ptsAtt > ptsDef ? att : ptsDef > ptsAtt ? def : null;
        Bukkit.broadcastMessage(SSNG.c("[*banner id=\"assault\"] &7Assaut " + nom(att) + " contre " + nom(def) + " termine (" + raison + ") : "
                + (g == null ? "&eegalite" : couleur(g) + nom(g) + " &7l'emporte") + " &7(" + ptsAtt + " - " + ptsDef + ")."));
        plugin.getLogger().info("Assaut " + att + " contre " + def + " : " + ptsAtt + " - " + ptsDef + " (" + raison + ")");
        marques.clear();
        att = null;
        def = null;
        monde = null;
    }

    public void arreter() {
        if (att != null) {
            finir("arret du serveur");
        }
    }

    // ------------------------------------------------------------------ marqueurs NG

    private void marqueur(Player p, String nom, String label, double x, double y, double z, String couleur) {
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
        final String json = new Gson().toJson(m);
        SSNG.reseau().envoyer(p, "MarkerPacket", new ReseauNG.Ecrivain() {
            public void ecrire(DataOutputStream out) throws Exception {
                out.writeUTF(json);
            }
        });
    }

    private void retirer(Player p, final String nom) {
        SSNG.reseau().envoyer(p, "MarkerRemovePacket", new ReseauNG.Ecrivain() {
            public void ecrire(DataOutputStream out) throws Exception {
                out.writeUTF(nom);
            }
        });
    }

    private void marqueurs(boolean zone, boolean coli) {
        for (Player p : tous()) {
            marques.add(p.getName());
            if (zone && capX != Integer.MIN_VALUE) {
                marqueur(p, "ssng_nation_zone", "Zone de capture", capX, capY, capZ, "FOUNDATIONS_ALLIANCES_RED_400");
            }
            if (coli) {
                if (colis != null) {
                    Location l = colis.getLocation();
                    marqueur(p, "ssng_nation_colis", "Colis", l.getX(), l.getY(), l.getZ(), "FOUNDATIONS_ALLIANCES_YELLOW_400");
                } else {
                    retirer(p, "ssng_nation_colis");
                }
            }
        }
    }

    private void retirerMarqueurs(Player p) {
        if (marques.contains(p.getName())) {
            retirer(p, "ssng_nation_zone");
            retirer(p, "ssng_nation_colis");
        }
    }

    // ------------------------------------------------------------------ regles

    /** Mur invisible : les participants restent dans les deux territoires, les autres n'y entrent pas. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void bouger(PlayerMoveEvent e) {
        if (att == null || e.getTo() == null || e.getTo().getWorld() != monde) {
            return;
        }
        if (e.getFrom().getBlockX() == e.getTo().getBlockX() && e.getFrom().getBlockZ() == e.getTo().getBlockZ()) {
            return;
        }
        Player p = e.getPlayer();
        if (SSNG.admin(p)) {
            return;
        }
        int[] ta = territoire(att), td = territoire(def);
        boolean dedans = dans(ta, e.getTo().getX(), e.getTo().getZ()) || dans(td, e.getTo().getX(), e.getTo().getZ());
        String n = nationDe(p.getName());
        boolean participant = att.equals(n) || def.equals(n);
        if (participant != dedans) {
            e.setTo(e.getFrom());
            SSNG.msg(p, participant ? "&cMur invisible : tu restes dans les territoires de l'assaut." : "&cUn assaut de nations est en cours ici.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void teleport(PlayerTeleportEvent e) {
        if (att == null || SSNG.admin(e.getPlayer())) {
            return;
        }
        String n = nationDe(e.getPlayer().getName());
        if ((att.equals(n) || def.equals(n)) && e.getFrom().getWorld() == monde && e.getTo() != null && e.getTo().getWorld() != monde
                && e.getCause() == PlayerTeleportEvent.TeleportCause.COMMAND) {
            e.setCancelled(true);
            SSNG.msg(e.getPlayer(), "&cPas pendant l'assaut de ta nation.");
        }
    }

    @EventHandler
    public void mort(PlayerDeathEvent e) {
        if (att == null || e.getEntity().getWorld() != monde) {
            return;
        }
        Player tue = e.getEntity(), tueur = tue.getKiller();
        if (tueur == null) {
            return;
        }
        String nt = nationDe(tueur.getName()), nm = nationDe(tue.getName());
        if (nt == null || nm == null || nt.equals(nm) || !(nt.equals(att) || nt.equals(def))) {
            return;
        }
        int pts = plugin.getConfig().getInt("assaut.points-kill", 5);
        if (nt.equals(att)) {
            ptsAtt += pts;
        } else {
            ptsDef += pts;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void reapparition(PlayerRespawnEvent e) {
        if (att == null) {
            return;
        }
        String n = nationDe(e.getPlayer().getName());
        if (att.equals(n) || def.equals(n)) {
            e.setRespawnLocation(pointDans(territoire(n)));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void colisFrappe(EntityDamageEvent e) {
        if (colis == null || !e.getEntity().getUniqueId().equals(colis.getUniqueId())) {
            return;
        }
        if (!(e instanceof EntityDamageByEntityEvent)) {
            e.setCancelled(true);
            return;
        }
        Entity d = ((EntityDamageByEntityEvent) e).getDamager();
        if (d instanceof org.bukkit.entity.Projectile && ((org.bukkit.entity.Projectile) d).getShooter() instanceof Entity) {
            d = (Entity) ((org.bukkit.entity.Projectile) d).getShooter();
        }
        boolean attaquant = d instanceof Player && att.equals(nationDe(((Player) d).getName()));
        e.setCancelled(!attaquant);
    }

    @EventHandler
    public void colisDetruit(EntityDeathEvent e) {
        if (colis == null || !e.getEntity().getUniqueId().equals(colis.getUniqueId())) {
            return;
        }
        colis = null;
        e.getDrops().clear();
        int pts = plugin.getConfig().getInt("assaut.colis.points", 50);
        ptsAtt += pts;
        diffuser("[*banner id=\"attacker\"] " + couleur(att) + nom(att) + " &7detruit le colis : &e+" + pts + " points&7 !");
        marqueurs(false, true);
    }
}
