package fr.armersindustries.ssng.iles;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import fr.armersindustries.ssng.core.SSNG;
import fr.armersindustries.ssng.core.ReseauNG;

/**
 * Partie serveur de l'interface officielle des iles NationsGlory (mod NationsGUI).
 * Chaque methode traite un paquet envoye par l'interface et repond dans le format
 * attendu par le client (voir IslandMainDataPacket, IslandListDataPacket...).
 */
public class PaquetsIles implements ReseauNG.Recepteur {

    private static final String SERVEUR = "1";
    private final IlesNG plugin;
    private final GestionIles iles;
    private final ReseauNG reseau;
    private final Gson gson = new Gson();
    /** Mots de passe deja valides : "joueur#id". */
    private final Set<String> passValides = new HashSet<String>();

    public PaquetsIles(IlesNG plugin, GestionIles iles, ReseauNG reseau) {
        this.plugin = plugin;
        this.iles = iles;
        this.reseau = reseau;
    }

    // ------------------------------------------------------------------ ouverture des menus

    public void ouvrirListe(Player p) {
        reseau.envoyer(p, "RemoteOpenIslandListPacket", null);
    }

    public void ouvrirIle(Player p) {
        if (ileDuMenu(p) == null) {
            // le menu d'une ile plante cote client s'il n'y a aucune ile : on montre la liste a la place
            SSNG.msg(p, "&7Tu n'es sur aucune ile et tu n'en as pas encore : voici la liste des iles.");
            ouvrirListe(p);
            return;
        }
        reseau.envoyer(p, "RemoteOpenIslandMainPacket", null);
    }

    /** Informations de l'ile courante pour l'interface du joueur (HUD NationsGUI). */
    public void envoyerIleCourante(Player p, Ile i) {
        final HashMap<String, String> m = new HashMap<String, String>();
        if (i != null) {
            m.put("id", String.valueOf(i.id));
            m.put("name", i.nom);
            m.put("creator", i.createur);
            m.put("description", i.description);
            m.put("serverNumber", SERVEUR);
        }
        reseau.envoyer(p, "IslandDataPacket", new ReseauNG.Ecrivain() {
            public void ecrire(DataOutputStream out) throws Exception {
                out.writeUTF(gson.toJson(m));
            }
        });
    }

    // ------------------------------------------------------------------ reception

    @Override
    public void recu(Player p, String paquet, DataInputStream in) throws Exception {
        if (paquet.equals("IslandListDataPacket")) {
            liste(p, in.readUTF(), in.readBoolean());
        } else if (paquet.equals("IslandGetImagePacket")) {
            image(p, in.readUTF());
        } else if (paquet.equals("IslandCreateCooldownPacket")) {
            reseau.envoyer(p, paquet, new ReseauNG.Ecrivain() {
                public void ecrire(DataOutputStream out) throws Exception {
                    out.writeLong(0L);
                }
            });
        } else if (paquet.equals("IslandCreatePacket")) {
            creer(p, in.readUTF(), in.readUTF(), in.readUTF(), in.readUTF(), in.readBoolean());
        } else if (paquet.equals("IslandTPPacket")) {
            teleporter(p, iles.ile(in.readUTF()), false);
        } else if (paquet.equals("IslandPasswordPacket")) {
            motDePasse(p, in.readUTF(), in.readUTF());
        } else if (paquet.equals("IslandAskVisitPacket")) {
            demanderVisite(p, iles.ile(in.readUTF()));
        } else if (paquet.equals("IslandMainDataPacket")) {
            principal(p);
        } else if (paquet.equals("IslandSavePermsPacket")) {
            permissions(p, iles.ile(in.readUTF()), in.readUTF());
        } else if (paquet.equals("IslandSaveSettingsPacket")) {
            reglages(p, iles.ile(in.readUTF()), in.readUTF());
        } else if (paquet.equals("IslandSaveFlagPacket")) {
            drapeau(p, iles.ile(in.readUTF()), in.readUTF());
        } else if (paquet.equals("IslandSavePropertiesPacket")) {
            proprietes(p, iles.ile(in.readUTF()), in.readUTF(), in.readUTF(), in.readUTF(), in.readUTF());
        } else if (paquet.equals("IslandExecuteActionPacket")) {
            action(p, iles.ile(in.readUTF()), in.readUTF());
        } else if (paquet.equals("IslandSetMemberPacket")) {
            membre(p, iles.ile(in.readUTF()), in.readUTF(), in.readUTF());
        } else if (paquet.equals("IslandDeletePacket")) {
            supprimer(p, iles.ile(in.readUTF()));
        } else if (paquet.equals("IslandResetPacket")) {
            reinitialiser(p, iles.ile(in.readUTF()));
        } else if (paquet.equals("IslandVotePacket")) {
            voter(p, iles.ile(in.readUTF()), in.readUTF());
        } else if (paquet.equals("IslandChangeGamemodePacket")) {
            modeDeJeu(p);
        } else if (paquet.equals("IslandChangeFlySpeedPacket")) {
            vitesseVol(p);
        } else if (paquet.equals("IslandClearInventoryPacket")) {
            if (iles.ileA(p.getLocation()) != null) {
                p.getInventory().clear();
                p.getInventory().setArmorContents(null);
                p.updateInventory();
            }
        } else if (paquet.equals("IslandTeamsDataPacket")) {
            equipes(p, in.readBoolean());
        } else if (paquet.equals("IslandTeamCreatePacket")) {
            in.readInt();
            creerEquipe(p, iles.ile(in.readInt()));
        } else if (paquet.equals("IslandTeamRemovePacket")) {
            supprimerEquipe(p, in.readInt());
        } else if (paquet.equals("IslandSaveTeamPacket")) {
            sauverEquipe(p, in.readUTF());
        } else if (paquet.equals("IslandGetTeamPrefixPacket")) {
            final String nom = in.readUTF();
            reseau.envoyer(p, paquet, new ReseauNG.Ecrivain() {
                public void ecrire(DataOutputStream out) throws Exception {
                    out.writeUTF(nom);
                    out.writeUTF("");
                }
            });
        } else if (paquet.equals("IslandSaveBackupPacket")) {
            sauvegarder(p, iles.ile(in.readUTF()), in.readUTF());
        } else if (paquet.equals("IslandRestoreBackupPacket")) {
            restaurer(p, iles.ile(in.readUTF()), in.readUTF());
        } else if (paquet.equals("IslandDeleteBackupPacket")) {
            supprimerSauvegarde(p, iles.ile(in.readUTF()), in.readUTF());
        }
    }

    /** Renvoie un paquet vide : declenche son handleClientPacket (en general : rafraichir l'interface). */
    private void rafraichir(Player p, String paquet) {
        reseau.envoyer(p, paquet, null);
    }

    // ------------------------------------------------------------------ liste et images

    private static String date(long t) {
        return new SimpleDateFormat("dd/MM/yyyy").format(new Date(t));
    }

    private int joueursSur(Ile i) {
        int n = 0;
        for (Player o : Bukkit.getOnlinePlayers()) {
            Ile x = iles.ileA(o.getLocation());
            if (x != null && x.id == i.id) {
                n++;
            }
        }
        return n;
    }

    private void liste(final Player p, final String filtre, boolean miennes) {
        List<Ile> l = new ArrayList<Ile>();
        for (Ile i : iles.toutes()) {
            if (!miennes || i.estMembre(p.getName())) {
                l.add(i);
            }
        }
        final Map<Integer, Integer> enLigne = new HashMap<Integer, Integer>();
        for (Ile i : l) {
            enLigne.put(i.id, joueursSur(i));
        }
        Collections.sort(l, new Comparator<Ile>() {
            public int compare(Ile a, Ile b) {
                if ("vote".equals(filtre)) {
                    return (b.votesPour() - b.votesContre()) - (a.votesPour() - a.votesContre());
                }
                if ("visit".equals(filtre)) {
                    return b.visites - a.visites;
                }
                return enLigne.get(b.id) - enLigne.get(a.id);
            }
        });
        final List<Map<String, String>> donnees = new ArrayList<Map<String, String>>();
        for (Ile i : l) {
            Map<String, String> m = new HashMap<String, String>();
            StringBuilder membres = new StringBuilder();
            for (String x : i.membres) {
                membres.append('#').append(x);
            }
            m.put("id", String.valueOf(i.id));
            m.put("name", i.nom);
            m.put("description", i.description);
            m.put("creator", i.createur);
            m.put("members", membres.toString());
            m.put("isPrivate", String.valueOf(i.privee));
            m.put("password", String.valueOf("password".equals(i.acces) && !i.motDePasse.isEmpty()));
            m.put("passwordValue", "");
            m.put("directJoin", String.valueOf(iles.permission(p, i, "direct_join")));
            m.put("playersOnline", String.valueOf(enLigne.get(i.id)));
            m.put("voteDiff", String.valueOf(i.votesPour() - i.votesContre()));
            m.put("voteUp", String.valueOf(i.votesPour()));
            m.put("voteDown", String.valueOf(i.votesContre()));
            m.put("visit", String.valueOf(i.visites));
            m.put("creationDate", date(i.creation));
            m.put("creationTime", String.valueOf(i.creation - 3600000L * 24));
            m.put("regenTime", String.valueOf(i.regeneration));
            m.put("image", i.image.isEmpty() ? "" : "1");
            m.put("serverNumber", SERVEUR);
            donnees.add(m);
        }
        final boolean op = SSNG.admin(p);
        reseau.envoyer(p, "IslandListDataPacket", new ReseauNG.Ecrivain() {
            public void ecrire(DataOutputStream out) throws Exception {
                out.writeUTF(gson.toJson(donnees));
                out.writeBoolean(true);
                out.writeBoolean(op);
                out.writeUTF(SERVEUR);
            }
        });
    }

    private void image(Player p, final String id) {
        Ile i = iles.ile(id);
        final String img = i == null ? "" : i.image;
        reseau.envoyer(p, "IslandGetImagePacket", new ReseauNG.Ecrivain() {
            public void ecrire(DataOutputStream out) throws Exception {
                out.writeUTF(id);
                out.writeUTF(SERVEUR);
                out.writeUTF(img);
            }
        });
    }

    // ------------------------------------------------------------------ creation et teleportation

    private void creer(Player p, String nom, String description, String taille, String biome, boolean privee) {
        int max = plugin.getConfig().getInt("iles.max-par-joueur", 5);
        if (!p.hasPermission("ssng.iles.creer")) {
            SSNG.msg(p, "&cTu n'as pas la permission de creer une ile.");
            return;
        }
        if (!SSNG.admin(p) && iles.ilesDe(p.getName()).size() >= max) {
            SSNG.msg(p, "&cTu as deja " + max + " ile(s), c'est le maximum.");
            return;
        }
        int t;
        try {
            t = Integer.parseInt(taille.trim());
        } catch (NumberFormatException e) {
            t = 100;
        }
        nom = nom.trim().isEmpty() ? "Ile de " + p.getName() : nom.trim();
        final Ile i = iles.creer(p, limiter(nom, 32), limiter(description.trim(), 120), t, biome, privee);
        SSNG.msg(p, "&aIle &f" + i.nom + " &acreee (" + i.taille + "x" + i.taille + ", " + i.biome + "). Preparation du terrain...");
        reseau.envoyer(p, "IslandCreatePacket", new ReseauNG.Ecrivain() {
            public void ecrire(DataOutputStream out) throws Exception {
                out.writeInt(i.id);
            }
        });
    }

    private static String limiter(String s, int n) {
        return s.length() > n ? s.substring(0, n) : s;
    }

    /** Teleporte le joueur sur l'ile si les regles d'acces le permettent. */
    public boolean teleporter(Player p, Ile i, boolean forcer) {
        if (i == null) {
            SSNG.msg(p, "&cCette ile n'existe plus.");
            return false;
        }
        String role = iles.role(p, i);
        if (!forcer && role.equals("banni")) {
            SSNG.msg(p, "&cTu es banni de cette ile.");
            return false;
        }
        if (!forcer && role.equals("visiteur") && !plugin.getConfig().getBoolean("iles.toutes-publiques", true)) {
            if (i.privee) {
                SSNG.msg(p, "&cCette ile est privee.");
                return false;
            }
            boolean mdp = "password".equals(i.acces) && !i.motDePasse.isEmpty();
            if (mdp && !iles.permission(p, i, "direct_join") && !passValides.contains(p.getName().toLowerCase() + "#" + i.id)) {
                SSNG.msg(p, "&cCette ile est protegee par un mot de passe.");
                return false;
            }
        }
        Location l = iles.spawn(i);
        p.teleport(l);
        return true;
    }

    private void motDePasse(Player p, final String id, String saisi) {
        Ile i = iles.ile(id);
        final boolean ok = i != null && i.motDePasse.equals(saisi);
        if (ok) {
            passValides.add(p.getName().toLowerCase() + "#" + i.id);
        }
        reseau.envoyer(p, "IslandPasswordPacket", new ReseauNG.Ecrivain() {
            public void ecrire(DataOutputStream out) throws Exception {
                out.writeUTF(id);
                out.writeBoolean(ok);
            }
        });
    }

    private void demanderVisite(Player p, Ile i) {
        if (i == null) {
            return;
        }
        Player createur = Bukkit.getPlayerExact(i.createur);
        if (createur != null) {
            SSNG.msg(createur, "&e" + p.getName() + " &7demande a visiter ton ile &f" + i.nom + "&7.");
        }
        SSNG.msg(p, "&7Demande envoyee au createur de l'ile.");
    }

    // ------------------------------------------------------------------ fenetre principale

    /** Ile affichee dans le menu principal : celle ou se trouve le joueur, sinon la premiere des siennes. */
    public Ile ileDuMenu(Player p) {
        Ile i = iles.ileA(p.getLocation());
        if (i == null) {
            List<Ile> l = iles.ilesDe(p.getName());
            i = l.isEmpty() ? null : l.get(0);
        }
        return i;
    }

    private void principal(Player p) {
        Ile i = ileDuMenu(p);
        if (i == null) {
            ouvrirListe(p);
            return;
        }
        final Map<String, Object> m = new LinkedHashMap<String, Object>();
        {
            String role = iles.role(p, i);
            boolean createur = role.equals("createur");
            m.put("id", String.valueOf(i.id));
            m.put("name", i.nom);
            m.put("description", i.description);
            m.put("creator", i.createur);
            m.put("biome", i.biome);
            m.put("creationDate", date(i.creation));
            m.put("image", i.image);
            m.put("imagePixels", i.pixels);
            m.put("visit", String.valueOf(i.visites));
            m.put("voteUp", String.valueOf(i.votesPour()));
            m.put("voteDown", String.valueOf(i.votesContre()));
            String v = i.votes.get(p.getName().toLowerCase());
            m.put("currentPlayerVotedUp", Boolean.valueOf("up".equals(v)));
            m.put("currentPlayerVotedDown", Boolean.valueOf("down".equals(v)));
            m.put("currentPlayerIslandCount", Double.valueOf(iles.ilesDe(p.getName()).size()));
            m.put("members", new ArrayList<String>(i.membres));
            m.put("visitors", new ArrayList<String>(i.visiteurs));
            m.put("isCreator", String.valueOf(createur));
            m.put("playerIsCreator", Boolean.valueOf(i.estCreateur(p.getName())));
            m.put("playerPermissions", iles.permissionsDe(p, i));
            m.put("permissions", java.util.Arrays.asList(Ile.PERMISSIONS));
            m.put("memberPermissions", gson.toJson(i.permsMembres));
            m.put("visitorPermissions", gson.toJson(i.permsVisiteurs));
            m.put("islandFlags", gson.toJson(i.drapeaux));
            m.put("flags", java.util.Arrays.asList(Ile.DRAPEAUX));
            m.put("isCreative", Boolean.valueOf(p.getGameMode() == GameMode.CREATIVE));
            m.put("flySpeed", Double.valueOf(Math.round(p.getFlySpeed() * 10) / 10.0));
            m.put("timeMode", i.heure);
            m.put("weatherMode", i.meteo);
            m.put("regenTime", String.valueOf(i.regeneration));
            m.put("backupsInfos", new ArrayList<String>(i.sauvegardes));
            m.put("lastBackup", String.valueOf(i.derniereSauvegarde));
            m.put("isPrivate", String.valueOf(i.privee));
            m.put("password", String.valueOf("password".equals(i.acces) && !i.motDePasse.isEmpty()));
            m.put("askBeforeJoin", "false");
            m.put("welcome", i.bienvenue);
        }
        final boolean op = SSNG.admin(p);
        reseau.envoyer(p, "IslandMainDataPacket", new ReseauNG.Ecrivain() {
            public void ecrire(DataOutputStream out) throws Exception {
                out.writeUTF(gson.toJson(m));
                out.writeBoolean(true);
                out.writeBoolean(op);
                out.writeUTF(SERVEUR);
            }
        });
    }

    private boolean createur(Player p, Ile i) {
        return i != null && iles.role(p, i).equals("createur");
    }

    private void permissions(Player p, Ile i, String json) {
        if (!createur(p, i)) {
            return;
        }
        Map<String, Map<String, Boolean>> modifs = gson.fromJson(json, new TypeToken<HashMap<String, HashMap<String, Boolean>>>() { }.getType());
        if (modifs != null) {
            if (modifs.get("member") != null) {
                i.permsMembres.putAll(modifs.get("member"));
            }
            if (modifs.get("visitor") != null) {
                i.permsVisiteurs.putAll(modifs.get("visitor"));
            }
        }
        iles.sauver(i);
        SSNG.msg(p, "&aPermissions de l'ile enregistrees.");
        rafraichir(p, "IslandSavePermsPacket");
    }

    private void reglages(Player p, Ile i, String json) {
        if (!createur(p, i)) {
            return;
        }
        Map<String, String> r = gson.fromJson(json, new TypeToken<HashMap<String, String>>() { }.getType());
        if (r != null) {
            if (r.get("name") != null && !r.get("name").trim().isEmpty()) {
                i.nom = limiter(r.get("name").trim(), 32);
            }
            if (r.get("description") != null) {
                i.description = limiter(r.get("description").trim(), 120);
            }
            if (r.get("welcome") != null) {
                i.bienvenue = limiter(r.get("welcome"), 120);
            }
            if (r.get("isPrivate") != null) {
                i.privee = Boolean.parseBoolean(r.get("isPrivate"));
            }
            if (r.get("openType") != null) {
                i.acces = "password".equals(r.get("openType")) ? "password" : "open";
            }
            if (r.get("password") != null && !r.get("password").isEmpty()) {
                i.motDePasse = r.get("password");
            }
        }
        iles.sauver(i);
        SSNG.msg(p, "&aReglages de l'ile enregistres.");
        rafraichir(p, "IslandSaveSettingsPacket");
    }

    private void drapeau(Player p, Ile i, String json) {
        if (i == null || !iles.permission(p, i, "tab_properties") && !createur(p, i)) {
            return;
        }
        Map<String, Object> d = gson.fromJson(json, new TypeToken<HashMap<String, Object>>() { }.getType());
        if (d != null) {
            if (d.get("image") instanceof String) {
                i.image = (String) d.get("image");
            }
            if (d.get("imagePixels") instanceof List) {
                List<Integer> px = new ArrayList<Integer>();
                for (Object o : (List<?>) d.get("imagePixels")) {
                    px.add(Integer.valueOf(o instanceof Number ? ((Number) o).intValue() : -1));
                }
                i.pixels = px;
            }
        }
        iles.sauver(i);
        rafraichir(p, "IslandSaveFlagPacket");
    }

    private void proprietes(Player p, Ile i, String jsonDrapeaux, String heure, String meteo, String biome) {
        if (i == null) {
            return;
        }
        if (iles.permission(p, i, "change_flags")) {
            Map<String, Boolean> d = gson.fromJson(jsonDrapeaux, new TypeToken<HashMap<String, Boolean>>() { }.getType());
            if (d != null) {
                i.drapeaux.putAll(d);
            }
        }
        if (iles.permission(p, i, "change_time") && ("day".equals(heure) || "night".equals(heure) || "cycle".equals(heure))) {
            i.heure = heure;
        }
        if (iles.permission(p, i, "change_weather") && ("sun".equals(meteo) || "rain".equals(meteo) || "storm".equals(meteo) || "cycle".equals(meteo))) {
            i.meteo = meteo;
        }
        if (iles.permission(p, i, "change_biome") && iles.biomeValide(biome) && !biome.equals(i.biome)) {
            i.biome = biome;
            iles.appliquerBiome(i);
        }
        iles.sauver(i);
        plugin.protectionIles().appliquerAmbiance(i);
        SSNG.msg(p, "&aProprietes de l'ile enregistrees.");
    }

    private void action(Player p, Ile i, String action) {
        if (i == null || !iles.permission(p, i, "execute_actions")) {
            return;
        }
        World w = iles.monde();
        int n = 0;
        if (action.equals("kill_hostile") || action.equals("kill_passive")) {
            for (Entity e : w.getEntities()) {
                Ile x = iles.ileA(e.getLocation());
                if (x != null && x.id == i.id && (action.equals("kill_hostile") ? e instanceof Monster : e instanceof Animals)) {
                    e.remove();
                    n++;
                }
            }
            SSNG.msg(p, "&a" + n + " creature(s) supprimee(s).");
        } else if (action.equals("set_spawn")) {
            Location l = p.getLocation();
            Ile x = iles.ileA(l);
            if (x == null || x.id != i.id) {
                SSNG.msg(p, "&cPlace-toi sur l'ile pour definir son spawn.");
                return;
            }
            i.spawnX = l.getX();
            i.spawnY = l.getY();
            i.spawnZ = l.getZ();
            i.spawnYaw = l.getYaw();
            iles.sauver(i);
            SSNG.msg(p, "&aSpawn de l'ile defini ici.");
        } else if (action.equals("kick_players") || action.equals("tp_players") || action.equals("respawn_all")) {
            for (Player o : Bukkit.getOnlinePlayers()) {
                Ile x = iles.ileA(o.getLocation());
                if (x == null || x.id != i.id) {
                    continue;
                }
                if (action.equals("kick_players")) {
                    if (iles.role(o, i).equals("visiteur")) {
                        o.teleport(plugin.spawn(IlesNG.TypeMonde.BUILD));
                        SSNG.msg(o, "&cTu as ete renvoye de l'ile " + i.nom + ".");
                        n++;
                    }
                } else {
                    o.teleport(iles.spawn(i));
                    n++;
                }
            }
            SSNG.msg(p, "&a" + n + " joueur(s) concerne(s).");
        } else {
            SSNG.msg(p, "&7Action \"" + action + "\" sans effet sur ce serveur.");
        }
    }

    private void membre(Player p, Ile i, String joueur, String action) {
        if (i == null || joueur == null) {
            return;
        }
        boolean gere = createur(p, i) || (action.equals("kick") && iles.permission(p, i, "kick"))
                || (action.equals("ban") && iles.permission(p, i, "ban"));
        if (!gere || i.estCreateur(joueur)) {
            return;
        }
        String nom = org.bukkit.ChatColor.stripColor(joueur);
        if (action.equals("promote")) {
            supprimerNom(i.visiteurs, nom);
            if (!i.estMembre(nom)) {
                i.membres.add(nom);
            }
        } else if (action.equals("demote")) {
            supprimerNom(i.membres, nom);
            if (!contient(i.visiteurs, nom)) {
                i.visiteurs.add(nom);
            }
        } else if (action.equals("ban")) {
            supprimerNom(i.membres, nom);
            supprimerNom(i.visiteurs, nom);
            if (!i.estBanni(nom)) {
                i.bannis.add(nom);
            }
            Player cible = Bukkit.getPlayerExact(nom);
            if (cible != null && iles.ileA(cible.getLocation()) == i) {
                cible.teleport(plugin.spawn(IlesNG.TypeMonde.BUILD));
            }
        } else if (action.equals("kick")) {
            Player cible = Bukkit.getPlayerExact(nom);
            Ile x = cible == null ? null : iles.ileA(cible.getLocation());
            if (x != null && x.id == i.id) {
                cible.teleport(plugin.spawn(IlesNG.TypeMonde.BUILD));
            }
        } else if (action.equals("unban")) {
            supprimerNom(i.bannis, nom);
        }
        iles.sauver(i);
        rafraichir(p, "IslandSetMemberPacket");
    }

    static boolean contient(List<String> l, String nom) {
        for (String s : l) {
            if (s.equalsIgnoreCase(nom)) {
                return true;
            }
        }
        return false;
    }

    static void supprimerNom(List<String> l, String nom) {
        for (int k = l.size() - 1; k >= 0; k--) {
            if (l.get(k).equalsIgnoreCase(nom)) {
                l.remove(k);
            }
        }
    }

    private void supprimer(Player p, Ile i) {
        if (i == null || !(i.estCreateur(p.getName()) || SSNG.admin(p))) {
            return;
        }
        for (Player o : Bukkit.getOnlinePlayers()) {
            Ile x = iles.ileA(o.getLocation());
            if (x != null && x.id == i.id) {
                o.teleport(plugin.spawn(IlesNG.TypeMonde.BUILD));
            }
        }
        i.supprimee = true;
        iles.sauver(i);
        SSNG.msg(p, "&cIle " + i.nom + " supprimee.");
    }

    private void reinitialiser(final Player p, final Ile i) {
        if (i == null || !iles.permission(p, i, "reset_island")) {
            return;
        }
        for (Player o : Bukkit.getOnlinePlayers()) {
            Ile x = iles.ileA(o.getLocation());
            if (x != null && x.id == i.id) {
                o.teleport(plugin.spawn(IlesNG.TypeMonde.BUILD));
                SSNG.msg(o, "&eL'ile " + i.nom + " est en cours de reinitialisation...");
            }
        }
        i.regeneration = System.currentTimeMillis();
        i.spawnY = 0;
        iles.sauver(i);
        iles.terrain(i, true, new Runnable() {
            public void run() {
                SSNG.msg(p, "&aIle " + i.nom + " reinitialisee.");
            }
        });
        rafraichir(p, "IslandResetPacket");
    }

    // ------------------------------------------------------------------ sauvegardes d'ile

    private static final java.util.Set<Integer> EN_COURS = new java.util.HashSet<Integer>();

    private java.io.File fichierSauvegarde(Ile i, String id) {
        return new java.io.File(plugin.getDataFolder(), "iles" + java.io.File.separator + "sauvegardes" + java.io.File.separator
                + i.id + java.io.File.separator + id.replaceAll("[^0-9]", "") + ".dat");
    }

    private boolean peutSauvegarder(Player p, Ile i) {
        return i != null && (iles.role(p, i).equals("createur") || iles.permission(p, i, "reset_island"));
    }

    private void sauvegarder(final Player p, final Ile i, String nom) {
        if (!peutSauvegarder(p, i)) {
            SSNG.msg(p, "&cTu n'as pas la permission de sauvegarder cette ile.");
            return;
        }
        if (i.sauvegardes.size() >= 6) {
            SSNG.msg(p, "&c6 sauvegardes maximum : supprimes-en une.");
            return;
        }
        if (!EN_COURS.add(Integer.valueOf(i.id))) {
            SSNG.msg(p, "&cUne operation est deja en cours sur cette ile.");
            return;
        }
        final String nomPropre = nom.replace("###", "").trim().isEmpty() ? "Sauvegarde" : nom.replace("###", "").trim();
        final long t = System.currentTimeMillis();
        int demi = i.taille / 2;
        SSNG.msg(p, "&7Sauvegarde de l'ile en cours...");
        Instantanes.sauver(plugin, iles.monde(), iles.centreX(i.id) - demi, 0, iles.centreZ(i.id) - demi,
                iles.centreX(i.id) + demi, 255, iles.centreZ(i.id) + demi, fichierSauvegarde(i, String.valueOf(t)), p.getName(),
                plugin.getConfig().getInt("iles.chunks-par-tick", 6),
                new Instantanes.Fin() {
                    public void fin(boolean ok, String message, Instantanes.Rapport r) {
                        EN_COURS.remove(Integer.valueOf(i.id));
                        if (ok) {
                            i.sauvegardes.add(t + "###" + nomPropre + "###" + t);
                            i.derniereSauvegarde = t;
                            iles.sauver(i);
                            SSNG.msg(p, "&aIle sauvegardee (&f" + nomPropre + "&a) : " + message);
                        } else {
                            SSNG.msg(p, "&c" + message);
                        }
                        rafraichir(p, "IslandSaveBackupPacket");
                    }
                });
    }

    private String trouverSauvegarde(Ile i, String id) {
        for (String s : i.sauvegardes) {
            if (s.split("###")[0].equals(id)) {
                return s;
            }
        }
        return null;
    }

    private void restaurer(final Player p, final Ile i, final String id) {
        if (!peutSauvegarder(p, i) || trouverSauvegarde(i, id) == null) {
            SSNG.msg(p, "&cSauvegarde introuvable ou permission manquante.");
            return;
        }
        if (!EN_COURS.add(Integer.valueOf(i.id))) {
            SSNG.msg(p, "&cUne operation est deja en cours sur cette ile.");
            return;
        }
        SSNG.msg(p, "&7Restauration de l'ile en cours...");
        Instantanes.restaurer(plugin, fichierSauvegarde(i, id), false,
                plugin.getConfig().getInt("iles.chunks-par-tick", 6),
                new Instantanes.Fin() {
                    public void fin(boolean ok, String message, Instantanes.Rapport r) {
                        EN_COURS.remove(Integer.valueOf(i.id));
                        SSNG.msg(p, (ok ? "&aIle restauree : " : "&c") + message);
                        rafraichir(p, "IslandRestoreBackupPacket");
                    }
                });
    }

    private void supprimerSauvegarde(Player p, Ile i, String id) {
        String s = i == null ? null : trouverSauvegarde(i, id);
        if (!peutSauvegarder(p, i) || s == null) {
            return;
        }
        i.sauvegardes.remove(s);
        iles.sauver(i);
        if (!fichierSauvegarde(i, id).delete()) {
            plugin.getLogger().fine("Sauvegarde " + id + " deja absente");
        }
        SSNG.msg(p, "&7Sauvegarde supprimee.");
        rafraichir(p, "IslandDeleteBackupPacket");
    }

    private void voter(Player p, Ile i, String vote) {
        if (i == null || i.estMembre(p.getName())) {
            return;
        }
        String cle = p.getName().toLowerCase();
        if (vote.equals(i.votes.get(cle))) {
            i.votes.remove(cle);
        } else {
            i.votes.put(cle, "up".equals(vote) ? "up" : "down");
        }
        iles.sauver(i);
        principal(p);
    }

    private void modeDeJeu(Player p) {
        Ile i = iles.ileA(p.getLocation());
        if (i == null || !iles.permission(p, i, "gamemode")) {
            return;
        }
        p.setGameMode(p.getGameMode() == GameMode.CREATIVE ? GameMode.SURVIVAL : GameMode.CREATIVE);
    }

    private void vitesseVol(Player p) {
        float v = Math.round(p.getFlySpeed() * 10) / 10f + 0.1f;
        if (v > 0.51f) {
            v = 0.1f;
        }
        p.setFlySpeed(v);
    }

    // ------------------------------------------------------------------ equipes (donnees seulement)

    private static final List<String> DRAPEAUX_EQUIPE = java.util.Arrays.asList("friendly_fire", "kit", "display_name", "tp_on_join", "kick_leave");

    private void equipes(Player p, final boolean creation) {
        Ile i = iles.ileA(p.getLocation());
        final List<Map<String, Object>> l = new ArrayList<Map<String, Object>>();
        if (i != null) {
            for (Map<String, Object> e : i.equipes) {
                Map<String, Object> c = new HashMap<String, Object>(e);
                Object f = c.get("flags");
                c.put("flags", f instanceof String ? f : gson.toJson(f));
                l.add(c);
            }
        }
        reseau.envoyer(p, "IslandTeamsDataPacket", new ReseauNG.Ecrivain() {
            public void ecrire(DataOutputStream out) throws Exception {
                out.writeUTF(gson.toJson(l));
                out.writeUTF(gson.toJson(DRAPEAUX_EQUIPE));
                out.writeBoolean(creation);
            }
        });
    }

    private void creerEquipe(Player p, Ile i) {
        if (i == null || !iles.permission(p, i, "teams")) {
            return;
        }
        int id = 1;
        for (Map<String, Object> e : i.equipes) {
            id = Math.max(id, Integer.parseInt(String.valueOf(e.get("id"))) + 1);
        }
        Map<String, Object> e = new HashMap<String, Object>();
        e.put("id", String.valueOf(id));
        e.put("name", "Equipe " + id);
        e.put("color", "f");
        e.put("maxPlayers", "10");
        e.put("score", "0");
        e.put("scoreGoal", "0");
        e.put("scoreType", "kill");
        Map<String, Boolean> f = new HashMap<String, Boolean>();
        for (String x : DRAPEAUX_EQUIPE) {
            f.put(x, Boolean.FALSE);
        }
        e.put("flags", gson.toJson(f));
        e.put("players", new ArrayList<String>());
        i.equipes.add(e);
        iles.sauver(i);
        rafraichir(p, "IslandTeamCreatePacket");
    }

    private void supprimerEquipe(Player p, int id) {
        Ile i = iles.ileA(p.getLocation());
        if (i == null || !iles.permission(p, i, "teams")) {
            return;
        }
        for (int k = i.equipes.size() - 1; k >= 0; k--) {
            if (String.valueOf(id).equals(String.valueOf(i.equipes.get(k).get("id")))) {
                i.equipes.remove(k);
            }
        }
        iles.sauver(i);
        rafraichir(p, "IslandTeamRemovePacket");
    }

    private void sauverEquipe(Player p, String json) {
        Ile i = iles.ileA(p.getLocation());
        if (i == null || !iles.permission(p, i, "teams")) {
            return;
        }
        Map<String, Object> e = gson.fromJson(json, new TypeToken<HashMap<String, Object>>() { }.getType());
        if (e == null || e.get("id") == null) {
            return;
        }
        for (int k = 0; k < i.equipes.size(); k++) {
            if (String.valueOf(e.get("id")).equals(String.valueOf(i.equipes.get(k).get("id")))) {
                i.equipes.set(k, e);
            }
        }
        iles.sauver(i);
        SSNG.msg(p, "&aEquipe enregistree.");
    }
}
