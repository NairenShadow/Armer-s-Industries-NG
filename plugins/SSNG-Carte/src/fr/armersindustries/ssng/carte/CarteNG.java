package fr.armersindustries.ssng.carte;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import fr.armersindustries.ssng.core.SSNG;
import fr.armersindustries.ssng.core.ReseauNG;

/**
 * Carte de NationsGlory (touche de la carte en jeu) branchee sur les zones du serveur :
 * - chaque zone apparait comme un territoire (nom + couleur) ;
 * - les admins ont l'onglet "Gestion du territoire" : selection de chunks, ajout / retrait de chunks
 *   dans la zone choisie (/zone carte <id>), et droits de construction par chunk
 *   (Acces a tous, Recrue = Builder, Membre = Testeur, Officier = Moderateur, et des joueurs nommes).
 * - si "carte.dynmap-url" est reglee (Dynmap installe), le fond de carte de NG affiche les mondes du serveur.
 */
public class CarteNG implements Listener {

    private static final Map<String, String> ROLES = new HashMap<String, String>();
    static {
        ROLES.put("recruit", "Builder");
        ROLES.put("member", "Testeur");
        ROLES.put("officer", "Moderateur");
    }

    private final Carte plugin;
    private final ReseauNG reseau;
    /** admin -> id de la zone sur laquelle il travaille depuis la carte. */
    private final Map<String, String> zoneChoisie = new HashMap<String, String>();
    /** joueur -> chunks deja envoyes (pour les effacer quand ils changent). */
    private final Map<String, Set<String>> envoyes = new HashMap<String, Set<String>>();

    public CarteNG(Carte plugin, ReseauNG reseau) {
        this.plugin = plugin;
        this.reseau = reseau;
    }

    private Zones zones() {
        return plugin.zones();
    }

    private boolean admin(Player p) {
        return SSNG.admin(p);
    }

    public void demarrer() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        reseau.ajouter("WorldMapClaimContextPacket", new ReseauNG.Recepteur() {
            public void recu(Player p, String paquet, DataInputStream in) throws Exception {
                territoires(p);
                contexte(p);
            }
        });
        reseau.ajouter("WorldMapMassClaimPacket", new ReseauNG.Recepteur() {
            public void recu(Player p, String paquet, DataInputStream in) throws Exception {
                String monde = mondeBukkit(in.readUTF(), p);
                claimer(p, monde, lireChunks(in), true);
            }
        });
        reseau.ajouter("WorldMapMassUnclaimPacket", new ReseauNG.Recepteur() {
            public void recu(Player p, String paquet, DataInputStream in) throws Exception {
                String monde = mondeBukkit(in.readUTF(), p);
                claimer(p, monde, lireChunks(in), false);
            }
        });
        reseau.ajouter("WorldMapClaimableZonePacket", new ReseauNG.Recepteur() {
            public void recu(Player p, String paquet, DataInputStream in) throws Exception {
                String monde = mondeBukkit(in.readUTF(), p);
                verdicts(p, monde, lireChunks(in));
            }
        });
        reseau.ajouter("WorldMapChunkAccessPacket", new ReseauNG.Recepteur() {
            public void recu(Player p, String paquet, DataInputStream in) throws Exception {
                String monde = mondeBukkit(in.readUTF(), p);
                acces(p, monde, lireChunks(in));
            }
        });
        reseau.ajouter("WorldMapChunkAccessSavePacket", new ReseauNG.Recepteur() {
            public void recu(Player p, String paquet, DataInputStream in) throws Exception {
                String monde = mondeBukkit(in.readUTF(), p);
                int[] ch = lireChunks(in);
                List<String> roles = lireListe(in);
                List<String> joueurs = lireListe(in);
                List<String> pays = lireListe(in);
                sauverAcces(p, monde, ch, roles, joueurs, pays);
            }
        });
        reseau.ajouter("WorldMapResolveAccessEntryPacket", new ReseauNG.Recepteur() {
            public void recu(Player p, String paquet, DataInputStream in) throws Exception {
                final String nom = in.readUTF();
                // pas de pays sur ce serveur : toute entree est un joueur
                reseau.envoyer(p, "WorldMapResolveAccessEntryPacket", new ReseauNG.Ecrivain() {
                    public void ecrire(DataOutputStream out) throws Exception {
                        out.writeUTF(nom);
                        out.writeBoolean(false);
                    }
                });
            }
        });
    }

    private static int[] lireChunks(DataInputStream in) throws Exception {
        int n = in.readInt();
        int sur = Math.max(0, Math.min(n, 16384));
        int[] t = new int[sur];
        for (int i = 0; i < sur; i++) {
            t[i] = in.readInt();
        }
        return t;
    }

    private static List<String> lireListe(DataInputStream in) throws Exception {
        List<String> l = new ArrayList<String>();
        int n = in.readInt();
        for (int i = 0; i < Math.min(n, 4096); i++) {
            l.add(in.readUTF());
        }
        return l;
    }

    // ------------------------------------------------------------------ zones sur la carte

    private static int couleur(Zone z) {
        String c = z.couleur == null ? "&e" : z.couleur;
        ChatColor cc = ChatColor.getByChar(c.length() > 1 ? c.charAt(c.length() - 1) : 'e');
        if (cc == null) {
            return 0xFFFF55;
        }
        switch (cc) {
            case BLACK: return 0x000000;
            case DARK_BLUE: return 0x0000AA;
            case DARK_GREEN: return 0x00AA00;
            case DARK_AQUA: return 0x00AAAA;
            case DARK_RED: return 0xAA0000;
            case DARK_PURPLE: return 0xAA00AA;
            case GOLD: return 0xFFAA00;
            case GRAY: return 0xAAAAAA;
            case DARK_GRAY: return 0x555555;
            case BLUE: return 0x5555FF;
            case GREEN: return 0x55FF55;
            case AQUA: return 0x55FFFF;
            case RED: return 0xFF5555;
            case LIGHT_PURPLE: return 0xFF55FF;
            case YELLOW: return 0xFFFF55;
            case WHITE: return 0xFFFFFF;
            default: return 0xFFFF55;
        }
    }

    /**
     * Nom du monde tel que la carte NG le connait : le mod NationsGUI (cote serveur) l'annonce au client avec
     * ServerEventHandler.dynmapWorldNameOf ("DIM3" pour un monde de plugin, par exemple). Les zones doivent
     * etre envoyees sous ce nom, sinon la carte ne les affiche pas.
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
            // NationsGUI absent ou ancien : nom Bukkit
        }
        return w.getName();
    }

    /** Monde Bukkit correspondant au nom envoye par la carte du client. */
    private static String mondeBukkit(String cle, Player p) {
        if (Bukkit.getWorld(cle) != null) {
            return cle;
        }
        if (cleCarte(p.getWorld()).equals(cle)) {
            return p.getWorld().getName();
        }
        for (World w : Bukkit.getWorlds()) {
            if (cleCarte(w).equals(cle)) {
                return w.getName();
            }
        }
        return cle;
    }

    /** Envoie les zones (territoires) du monde du joueur a sa carte. */
    public void territoires(Player p) {
        if (!plugin.getConfig().getBoolean("carte.zones", true)) {
            return;
        }
        final String monde = p.getWorld().getName();
        final String cleCarte = cleCarte(p.getWorld());
        Map<String, Zone> ch = zones().chunksDuMonde(monde);
        final Map<String, String> ajouts = new HashMap<String, String>();
        for (Map.Entry<String, Zone> e : ch.entrySet()) {
            String[] c = e.getKey().split(";");
            ajouts.put(c[0] + "#" + c[1], e.getValue().nom + "##" + String.format("%06X", couleur(e.getValue())));
        }
        Set<String> avant = envoyes.get(p.getName() + "|" + monde);
        if (avant != null) {
            final Set<String> retraits = new HashSet<String>(avant);
            retraits.removeAll(ajouts.keySet());
            if (!retraits.isEmpty()) {
                envoyerClaims(p, cleCarte, true, retraits, null);
            }
        }
        envoyerClaims(p, cleCarte, false, ajouts.keySet(), ajouts);
        envoyes.put(p.getName() + "|" + monde, new HashSet<String>(ajouts.keySet()));
    }

    private void envoyerClaims(Player p, final String monde, final boolean retirer, final Set<String> cles, final Map<String, String> valeurs) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream d = new DataOutputStream(new GZIPOutputStream(b));
            d.writeInt(cles.size());
            for (String k : cles) {
                String[] xz = k.split("#");
                d.writeInt(Integer.parseInt(xz[0]));
                d.writeInt(Integer.parseInt(xz[1]));
                String v = valeurs == null ? "##000000" : valeurs.get(k);
                String[] nc = v.split("##");
                d.writeUTF(nc[0]);
                d.writeInt(nc.length > 1 ? Integer.parseInt(nc[1], 16) : 0);
            }
            d.close();
            final byte[] gz = b.toByteArray();
            reseau.envoyer(p, "RefreshClaimMapPacket", new ReseauNG.Ecrivain() {
                public void ecrire(DataOutputStream out) throws Exception {
                    out.writeBoolean(retirer);
                    out.writeUTF(monde);
                    out.writeInt(gz.length);
                    out.write(gz);
                }
            });
        } catch (Exception ex) {
            plugin.getLogger().warning("Carte NG : " + ex);
        }
    }

    /** Ce que la carte peut faire : gestion des chunks pour les admins, sur la zone choisie. */
    public void contexte(final Player p) {
        final Zone z = zoneDe(p);
        final boolean adm = admin(p);
        final int n = z == null ? 0 : zones().nbChunks(z);
        reseau.envoyer(p, "WorldMapClaimContextPacket", new ReseauNG.Ecrivain() {
            public void ecrire(DataOutputStream out) throws Exception {
                out.writeBoolean(adm && z != null);
                out.writeUTF(z == null ? "" : z.nom);
                out.writeInt(-1);
                out.writeInt(n);
                out.writeInt(100000);
                out.writeBoolean(true);
                out.writeBoolean(adm);
            }
        });
    }

    /** Zone de travail d'un admin : celle choisie, sinon celle ou il se trouve. */
    public Zone zoneDe(Player p) {
        String id = zoneChoisie.get(p.getName());
        Zone z = id == null ? null : zones().zone(id);
        if (z == null) {
            z = zones().zoneA(p.getLocation());
        }
        return z;
    }

    public void choisir(Player p, Zone z) {
        zoneChoisie.put(p.getName(), z.id);
        contexte(p);
    }

    // ------------------------------------------------------------------ actions de la carte

    private void claimer(final Player p, String monde, int[] ch, boolean ajouter) {
        int total = ch.length / 2;
        int faits = 0;
        Zone z = zoneDe(p);
        if (admin(p) && z != null) {
            for (int i = 0; i + 1 < ch.length; i += 2) {
                Zone actuelle = zones().zoneChunk(monde, ch[i], ch[i + 1]);
                if (ajouter) {
                    if (actuelle == null || actuelle == z || plugin.getConfig().getBoolean("carte.voler-chunks", false)) {
                        zones().claimChunk(z, monde, ch[i], ch[i + 1]);
                        faits++;
                    }
                } else if (actuelle == z) {
                    zones().unclaimChunk(monde, ch[i], ch[i + 1]);
                    faits++;
                }
            }
            zones().sauver();
            SSNG.msg(p, "&7Zone " + z.couleur + z.nom + "&7 : " + faits + " chunk(s) " + (ajouter ? "ajoute(s)" : "retire(s)") + ".");
        } else if (admin(p)) {
            SSNG.msg(p, "&cChoisis d'abord une zone : &f/zone carte <id>");
        }
        final int f = faits;
        final int t = total;
        reseau.envoyer(p, ajouter ? "WorldMapMassClaimPacket" : "WorldMapMassUnclaimPacket", new ReseauNG.Ecrivain() {
            public void ecrire(DataOutputStream out) throws Exception {
                out.writeInt(f);
                out.writeInt(t);
            }
        });
        // la carte de tous les joueurs du monde est mise a jour
        World w = Bukkit.getWorld(monde);
        if (w != null) {
            for (Player o : w.getPlayers()) {
                territoires(o);
            }
        }
        contexte(p);
    }

    /** Verdicts pour la selection : 0 = libre ou deja dans la zone, 1 = dans une autre zone. */
    private void verdicts(Player p, String monde, final int[] ch) {
        Zone z = zoneDe(p);
        final byte[] v = new byte[ch.length / 2];
        final String[] pays = new String[ch.length / 2];
        for (int i = 0, j = 0; i + 1 < ch.length; i += 2, j++) {
            Zone a = zones().zoneChunk(monde, ch[i], ch[i + 1]);
            boolean libre = a == null || a == z || plugin.getConfig().getBoolean("carte.voler-chunks", false);
            v[j] = (byte) (libre ? 0 : 1);
            pays[j] = a == null || a == z ? "" : a.nom;
        }
        reseau.envoyer(p, "WorldMapClaimableZonePacket", new ReseauNG.Ecrivain() {
            public void ecrire(DataOutputStream out) throws Exception {
                out.writeInt(ch.length);
                for (int c : ch) {
                    out.writeInt(c);
                }
                out.writeInt(v.length);
                for (byte b : v) {
                    out.writeByte(b);
                }
                out.writeInt(pays.length);
                for (String s : pays) {
                    out.writeUTF(s == null ? "" : s);
                }
            }
        });
    }

    /** Droits du (des) chunk(s) selectionne(s). */
    private void acces(Player p, String monde, int[] ch) {
        if (ch.length < 2) {
            return;
        }
        List<String> premier = zones().accesChunk(monde, ch[0], ch[1]);
        boolean melange = false;
        for (int i = 2; i + 1 < ch.length; i += 2) {
            List<String> a = zones().accesChunk(monde, ch[i], ch[i + 1]);
            if (a == null ? premier != null : !a.equals(premier)) {
                melange = true;
                break;
            }
        }
        final List<String> roles = new ArrayList<String>();
        final List<String> joueurs = new ArrayList<String>();
        if (premier != null) {
            for (String a : premier) {
                if (a.equalsIgnoreCase("tous")) {
                    roles.add("all");
                } else if (a.startsWith("grade:")) {
                    for (Map.Entry<String, String> e : ROLES.entrySet()) {
                        if (e.getValue().equalsIgnoreCase(a.substring(6))) {
                            roles.add(e.getKey());
                        }
                    }
                } else {
                    joueurs.add(a);
                }
            }
        }
        final int cx = ch[0];
        final int cz = ch[1];
        final boolean m = melange;
        final boolean adm = admin(p);
        reseau.envoyer(p, "WorldMapChunkAccessPacket", new ReseauNG.Ecrivain() {
            public void ecrire(DataOutputStream out) throws Exception {
                out.writeInt(cx);
                out.writeInt(cz);
                out.writeBoolean(adm);
                out.writeBoolean(true);
                out.writeBoolean(m);
                out.writeInt(roles.size());
                for (String r : roles) {
                    out.writeUTF(r);
                }
                out.writeInt(joueurs.size());
                for (String j : joueurs) {
                    out.writeUTF(j);
                }
                out.writeInt(0);
            }
        });
    }

    private void sauverAcces(Player p, String monde, int[] ch, List<String> roles, List<String> joueurs, List<String> pays) {
        if (!admin(p)) {
            SSNG.msg(p, "&cReserve aux admins.");
            return;
        }
        List<String> acces = new ArrayList<String>();
        if (roles.contains("all")) {
            acces.add("tous");
        }
        for (String r : roles) {
            if (ROLES.containsKey(r)) {
                acces.add("grade:" + ROLES.get(r));
            }
        }
        Set<String> noms = new HashSet<String>();
        for (String j : joueurs) {
            if (!j.isEmpty() && noms.add(j.toLowerCase())) {
                acces.add(j);
            }
        }
        for (String j : pays) {
            if (!j.isEmpty() && noms.add(j.toLowerCase())) {
                acces.add(j);
            }
        }
        int n = 0;
        for (int i = 0; i + 1 < ch.length; i += 2) {
            zones().reglerAccesChunk(monde, ch[i], ch[i + 1], acces);
            n++;
        }
        zones().sauver();
        SSNG.msg(p, "&aDroits de construction enregistres pour " + n + " chunk(s) : &f"
                + (acces.isEmpty() ? "regles de la zone" : acces.toString()));
        acces(p, monde, ch);
    }

    // ------------------------------------------------------------------ fond de carte (Dynmap)

    @EventHandler(priority = EventPriority.MONITOR)
    public void connexion(final PlayerJoinEvent e) {
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            public void run() {
                dynmap(e.getPlayer());
                if (e.getPlayer().isOnline()) {
                    territoires(e.getPlayer());
                }
            }
        }, 60L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void changement(PlayerChangedWorldEvent e) {
        territoires(e.getPlayer());
    }

    @EventHandler
    public void depart(PlayerQuitEvent e) {
        String n = e.getPlayer().getName() + "|";
        java.util.Iterator<String> it = envoyes.keySet().iterator();
        while (it.hasNext()) {
            if (it.next().startsWith(n)) {
                it.remove();
            }
        }
    }

    /** Adresse Dynmap pour le fond de carte (canal NGMinimap du client), si elle est reglee. */
    public void dynmap(Player p) {
        String url = plugin.getConfig().getString("carte.dynmap-url", "");
        if (url == null || url.isEmpty() || p == null || !p.isOnline()) {
            return;
        }
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(b);
            out.writeByte(0);
            out.writeBoolean(true);
            out.writeUTF(url);
            out.writeUTF(plugin.getConfig().getString("carte.nom-serveur", "Serveur NG"));
            envoyerMinimap(p, b.toByteArray());
            ByteArrayOutputStream b2 = new ByteArrayOutputStream();
            DataOutputStream o2 = new DataOutputStream(b2);
            o2.writeByte(1);
            List<World> mondes = Bukkit.getWorlds();
            o2.writeInt(mondes.size());
            for (World w : mondes) {
                int dim = 0;
                try {
                    Object h = w.getClass().getMethod("getHandle").invoke(w);
                    Object prov = h.getClass().getField("field_73011_w").get(h);
                    dim = prov.getClass().getField("field_76574_g").getInt(prov);
                } catch (Throwable t) {
                    // dimension inconnue
                }
                o2.writeInt(dim);
                o2.writeUTF(w.getName());
                o2.writeUTF(w.getName());
                o2.writeBoolean(true);
            }
            envoyerMinimap(p, b2.toByteArray());
        } catch (Throwable t) {
            plugin.getLogger().warning("Carte NG (Dynmap) : " + t);
        }
    }

    private void envoyerMinimap(Player p, byte[] donnees) throws Exception {
        ClassLoader cl = Bukkit.getServer().getClass().getClassLoader();
        Class<?> dispatcher = Class.forName("cpw.mods.fml.common.network.PacketDispatcher", true, cl);
        Class<?> paquetMc = Class.forName("net.minecraft.network.packet.Packet", true, cl);
        Class<?> joueurFml = Class.forName("cpw.mods.fml.common.network.Player", true, cl);
        Object paquet = dispatcher.getMethod("getPacket", String.class, byte[].class).invoke(null, "NGMinimap", donnees);
        Object handle = p.getClass().getMethod("getHandle").invoke(p);
        dispatcher.getMethod("sendPacketToPlayer", paquetMc, joueurFml).invoke(null, paquet, handle);
    }
}
