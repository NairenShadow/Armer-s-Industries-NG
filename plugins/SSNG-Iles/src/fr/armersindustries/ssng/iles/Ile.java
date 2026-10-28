package fr.armersindustries.ssng.iles;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Donnees d'une ile (enregistrees en JSON dans plugins/SSNG-Iles/iles/<id>.json). */
public class Ile {

    /** Toutes les permissions affichees dans l'onglet "Permissions" de l'interface NG. */
    public static final String[] PERMISSIONS = {
        "build", "interact", "chest_access", "item_drop", "item_pickup", "lighter", "bike", "weapon",
        "item_spawner", "worldedit", "gamemode", "direct_join", "voice", "cmds", "plates", "teams",
        "tab_properties", "change_biome", "change_time", "change_weather", "change_flags", "execute_actions",
        "reset_island", "kick", "ban"};

    /** Proprietes (onglet "Gestion") : affichees dans la liste + 3 interrupteurs a part. */
    public static final String[] DRAPEAUX = {
        "tnt", "creeper-explosion", "other-explosion", "fire-spread", "water-flow", "lava-flow", "mob-damage",
        "fall-damage", "auto-feed", "gun", "item-drop", "item-pickup", "items-death", "snow-melt"};

    public int id;
    public String nom = "";
    public String description = "";
    public String createur = "";
    public int taille = 100;
    public String biome = "plaine";
    public boolean privee;
    public String acces = "open";      // open | password
    public String motDePasse = "";
    public String bienvenue = "";
    public List<String> membres = new ArrayList<String>();
    public List<String> visiteurs = new ArrayList<String>();
    public List<String> bannis = new ArrayList<String>();
    public Map<String, Boolean> permsMembres = new LinkedHashMap<String, Boolean>();
    public Map<String, Boolean> permsVisiteurs = new LinkedHashMap<String, Boolean>();
    public Map<String, Boolean> drapeaux = new LinkedHashMap<String, Boolean>();
    public String heure = "cycle";     // day | night | cycle
    public String meteo = "cycle";     // storm | rain | sun | cycle
    public long creation;
    public long regeneration;
    public long derniereSauvegarde;
    public int visites;
    public Map<String, String> votes = new LinkedHashMap<String, String>();
    public String image = "";
    public List<Integer> pixels = new ArrayList<Integer>();
    public double spawnX;
    public double spawnY;
    public double spawnZ;
    public float spawnYaw;
    public List<Map<String, Object>> equipes = new ArrayList<Map<String, Object>>();
    public boolean supprimee;
    /** Sauvegardes : "id###nom###date" (format de l'interface NG). */
    public List<String> sauvegardes = new ArrayList<String>();

    /** Valeurs par defaut d'une nouvelle ile. */
    public void parDefaut() {
        for (String p : PERMISSIONS) {
            permsMembres.put(p, !(p.equals("reset_island") || p.equals("ban") || p.equals("kick") || p.equals("tab_properties")
                    || p.equals("worldedit") || p.equals("execute_actions") || p.equals("change_flags") || p.equals("teams")));
            permsVisiteurs.put(p, p.equals("direct_join") || p.equals("item_pickup") || p.equals("voice") || p.equals("interact"));
        }
        for (String d : DRAPEAUX) {
            drapeaux.put(d, d.equals("water-flow") || d.equals("lava-flow") || d.equals("auto-feed") || d.equals("item-drop")
                    || d.equals("item-pickup"));
        }
        drapeaux.put("mob_hostile", Boolean.FALSE);
        drapeaux.put("mob_passive", Boolean.TRUE);
        drapeaux.put("pvp", Boolean.FALSE);
    }

    /** Complete les cles manquantes (fichiers d'une ancienne version). */
    public void completer() {
        if (sauvegardes == null) {
            sauvegardes = new ArrayList<String>();
        }
        Ile d = new Ile();
        d.parDefaut();
        for (Map.Entry<String, Boolean> e : d.permsMembres.entrySet()) {
            if (!permsMembres.containsKey(e.getKey())) {
                permsMembres.put(e.getKey(), e.getValue());
            }
        }
        for (Map.Entry<String, Boolean> e : d.permsVisiteurs.entrySet()) {
            if (!permsVisiteurs.containsKey(e.getKey())) {
                permsVisiteurs.put(e.getKey(), e.getValue());
            }
        }
        for (Map.Entry<String, Boolean> e : d.drapeaux.entrySet()) {
            if (!drapeaux.containsKey(e.getKey())) {
                drapeaux.put(e.getKey(), e.getValue());
            }
        }
    }

    public boolean drapeau(String nom) {
        Boolean b = drapeaux.get(nom);
        return b != null && b.booleanValue();
    }

    public boolean estCreateur(String joueur) {
        return createur.equalsIgnoreCase(joueur);
    }

    public boolean estMembre(String joueur) {
        if (estCreateur(joueur)) {
            return true;
        }
        for (String m : membres) {
            if (m.equalsIgnoreCase(joueur)) {
                return true;
            }
        }
        return false;
    }

    public boolean estBanni(String joueur) {
        for (String m : bannis) {
            if (m.equalsIgnoreCase(joueur)) {
                return true;
            }
        }
        return false;
    }

    public int votesPour() {
        int n = 0;
        for (String v : votes.values()) {
            if ("up".equals(v)) {
                n++;
            }
        }
        return n;
    }

    public int votesContre() {
        return votes.size() - votesPour();
    }
}
