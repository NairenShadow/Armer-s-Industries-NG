package fr.armersindustries.ssng.machines;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import com.google.gson.Gson;

import fr.armersindustries.ssng.core.SSNG;
import fr.armersindustries.ssng.core.ReseauNG;

/**
 * Ateliers NationsGlory (etablis energetique, armement, vehicules, nourriture, potions, debutant).
 * Le plugin prive du serveur officiel envoyait les recettes et traitait les depots, fabrications
 * et retraits ; ici les recettes sont dans plugins/SSNG-Machines/ateliers.yml et se gerent en jeu
 * avec /atelier (editeur : coffre ou l'on pose le resultat et les ingredients).
 */
public class Ateliers implements CommandExecutor, Listener {

    /** {id envoye par le bloc, nom affiche, categories par defaut "cle:Nom"...} */
    static final String[][] DEFAUT = {
        {"energie", "Établi énergétique", "machines:Machines", "energie:Énergie", "composants:Composants"},
        {"weapons", "Établi d'armement", "armes:Armes", "munitions:Munitions", "explosifs:Explosifs"},
        {"vehicle", "Établi de véhicules", "vehicules:Véhicules", "pieces:Pièces"},
        {"foods", "Établi de nourriture", "plats:Plats", "boissons:Boissons"},
        {"potions", "Établi de potions", "potions:Potions"},
        {"newbies", "Établi du débutant", "outils:Outils", "blocs:Blocs"}
    };

    static final Map<String, String> ALIAS = new HashMap<String, String>();

    static {
        ALIAS.put("energy", "energie");
        ALIAS.put("energetique", "energie");
        ALIAS.put("armement", "weapons");
        ALIAS.put("armes", "weapons");
        ALIAS.put("vehicules", "vehicle");
        ALIAS.put("vehicles", "vehicle");
        ALIAS.put("nourriture", "foods");
        ALIAS.put("food", "foods");
        ALIAS.put("potion", "potions");
        ALIAS.put("debutant", "newbies");
    }

    /** Une recette d'atelier. */
    static class Recette {
        int id;
        int meta;
        int qte = 1;
        String nbt;
        String nom;
        final List<int[]> ingredients = new ArrayList<int[]>();
        int secondes = 10;
        final Map<String, Integer> ressources = new LinkedHashMap<String, Integer>();
    }

    /** Edition en cours dans le coffre d'un admin. */
    static class Edition {
        String atelier;
        String cat;
        int index;
        int secondes;
    }

    private final MachinesNG plugin;
    private final ReseauNG reseau;
    private final File fichier;
    private YamlConfiguration conf;
    private final Map<String, Edition> editions = new HashMap<String, Edition>();

    public Ateliers(MachinesNG plugin, ReseauNG reseau) {
        this.plugin = plugin;
        this.reseau = reseau;
        this.fichier = new File(plugin.getDataFolder(), "ateliers.yml");
        charger();
    }

    // ------------------------------------------------------------------ fichier

    public void charger() {
        conf = fichier.exists() ? Yaml.charger(fichier) : new YamlConfiguration();
        boolean change = false;
        for (String[] d : DEFAUT) {
            String b = "ateliers." + d[0];
            if (!conf.isConfigurationSection(b)) {
                conf.set(b + ".nom", d[1]);
                for (int i = 2; i < d.length; i++) {
                    String[] c = d[i].split(":", 2);
                    conf.set(b + ".categories." + c[0] + ".nom", c[1]);
                    conf.set(b + ".categories." + c[0] + ".icone", "");
                    conf.set(b + ".categories." + c[0] + ".recettes", new ArrayList<Object>());
                }
                change = true;
            }
        }
        if (!conf.isSet("exemples-ajoutes")) {
            // deux recettes d'exemple dans l'etabli du debutant (a modifier ou retirer avec /atelier)
            List<Map<String, Object>> ex = new ArrayList<Map<String, Object>>();
            ex.add(exemple("274:0", 1, new String[] {"4:0 x3", "280:0 x2"}, 5));
            ex.add(exemple("50:0", 4, new String[] {"263:0 x1", "280:0 x1"}, 2));
            conf.set("ateliers.newbies.categories.outils.recettes", ex);
            conf.set("exemples-ajoutes", Boolean.TRUE);
            change = true;
        }
        if (change) {
            sauver();
        }
    }

    private static Map<String, Object> exemple(String res, int qte, String[] ingr, int sec) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("resultat", res);
        m.put("quantite", Integer.valueOf(qte));
        List<String> l = new ArrayList<String>();
        for (String s : ingr) {
            l.add(s);
        }
        m.put("ingredients", l);
        m.put("secondes", Integer.valueOf(sec));
        return m;
    }

    private void sauver() {
        try {
            Yaml.sauver(conf, fichier);
        } catch (Exception e) {
            plugin.getLogger().warning("ateliers.yml : " + e);
        }
    }

    static String atelierId(String s) {
        if (s == null) {
            return "";
        }
        String k = s.toLowerCase();
        return ALIAS.containsKey(k) ? ALIAS.get(k) : k;
    }

    private ConfigurationSection atelier(String id) {
        return conf.getConfigurationSection("ateliers." + atelierId(id));
    }

    private List<String> categories(String atelier) {
        List<String> l = new ArrayList<String>();
        ConfigurationSection s = conf.getConfigurationSection("ateliers." + atelierId(atelier) + ".categories");
        if (s != null) {
            l.addAll(s.getKeys(false));
        }
        return l;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> brut(String atelier, String cat) {
        List<Map<String, Object>> l = new ArrayList<Map<String, Object>>();
        List<?> src = conf.getList("ateliers." + atelierId(atelier) + ".categories." + cat + ".recettes");
        if (src != null) {
            for (Object o : src) {
                if (o instanceof Map) {
                    l.add(new LinkedHashMap<String, Object>((Map<String, Object>) o));
                } else if (o instanceof ConfigurationSection) {
                    l.add(new LinkedHashMap<String, Object>(((ConfigurationSection) o).getValues(true)));
                }
            }
        }
        return l;
    }

    private void ecrireBrut(String atelier, String cat, List<Map<String, Object>> l) {
        conf.set("ateliers." + atelierId(atelier) + ".categories." + cat + ".recettes", l);
        sauver();
    }

    List<Recette> recettes(String atelier, String cat) {
        List<Recette> l = new ArrayList<Recette>();
        for (Map<String, Object> m : brut(atelier, cat)) {
            Recette r = lire(m);
            if (r != null) {
                l.add(r);
            }
        }
        return l;
    }

    private static int[] idMeta(String s) {
        String[] p = s.trim().split(":");
        return new int[] {Integer.parseInt(p[0].trim()), p.length > 1 ? Integer.parseInt(p[1].trim()) : 0};
    }

    private static int nombre(Object o, int def) {
        if (o instanceof Number) {
            return ((Number) o).intValue();
        }
        try {
            return o == null ? def : Integer.parseInt(o.toString().trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private Recette lire(Map<String, Object> m) {
        try {
            Recette r = new Recette();
            int[] im = idMeta(String.valueOf(m.get("resultat")));
            r.id = im[0];
            r.meta = im[1];
            r.qte = Math.max(1, nombre(m.get("quantite"), 1));
            r.nbt = m.get("nbt") == null ? null : m.get("nbt").toString();
            r.nom = m.get("nom") == null ? null : m.get("nom").toString();
            r.secondes = Math.max(0, nombre(m.get("secondes"), 10));
            Object ingr = m.get("ingredients");
            if (ingr instanceof List) {
                for (Object o : (List<?>) ingr) {
                    String s = o.toString().trim();
                    int n = 1;
                    int x = s.toLowerCase().indexOf(" x");
                    if (x > 0) {
                        n = Integer.parseInt(s.substring(x + 2).trim());
                        s = s.substring(0, x);
                    }
                    int[] a = idMeta(s);
                    r.ingredients.add(new int[] {a[0], a[1], Math.max(1, n)});
                }
            }
            Object res = m.get("ressources");
            if (res instanceof Map) {
                for (Map.Entry<?, ?> e : ((Map<?, ?>) res).entrySet()) {
                    r.ressources.put(e.getKey().toString(), Integer.valueOf(nombre(e.getValue(), 0)));
                }
            } else if (res instanceof ConfigurationSection) {
                for (String k : ((ConfigurationSection) res).getKeys(false)) {
                    r.ressources.put(k, Integer.valueOf(((ConfigurationSection) res).getInt(k)));
                }
            }
            return r;
        } catch (Exception e) {
            plugin.getLogger().warning("Recette d'atelier illisible " + m + " : " + e);
            return null;
        }
    }

    /** Nom de categorie envoye au client (unique entre ateliers : le client garde un cache par nom). */
    private static String cle(String atelier, String cat) {
        return (atelierId(atelier) + "_" + cat).toLowerCase();
    }

    // ------------------------------------------------------------------ paquets

    public void demarrer() {
        reseau.ajouter("WorkStationAdditionalDataPacket", new ReseauNG.Recepteur() {
            public void recu(Player p, String paquet, DataInputStream in) throws Exception {
                envoyerAtelier(p, in.readUTF());
            }
        });
        reseau.ajouter("MachinePollRequestPacket", new ReseauNG.Recepteur() {
            public void recu(Player p, String paquet, DataInputStream in) throws Exception {
                int x = in.readInt();
                int y = in.readInt();
                int z = in.readInt();
                Object te = poste(p.getWorld(), x, y, z);
                if (te != null) {
                    sync(p, te, x, y, z);
                }
            }
        });
        reseau.ajouter("MachineDepositPacket", new ReseauNG.Recepteur() {
            public void recu(Player p, String paquet, DataInputStream in) throws Exception {
                int x = in.readInt(), y = in.readInt(), z = in.readInt(), id = in.readInt(), meta = in.readInt(), n = in.readInt();
                Object te = poste(p.getWorld(), x, y, z);
                if (te == null || n <= 0 || !peutUtiliser(p, x, y, z)) {
                    return;
                }
                int pris = retirerInventaire(p.getInventory(), id, meta, n);
                if (pris > 0) {
                    appeler(te, "addToStorage", new Class<?>[] {int.class, int.class, int.class}, id, meta, pris);
                    p.updateInventory();
                }
                sync(p, te, x, y, z);
            }
        });
        reseau.ajouter("MachineWithdrawPacket", new ReseauNG.Recepteur() {
            public void recu(Player p, String paquet, DataInputStream in) throws Exception {
                int x = in.readInt(), y = in.readInt(), z = in.readInt(), id = in.readInt(), meta = in.readInt(), n = in.readInt();
                Object te = poste(p.getWorld(), x, y, z);
                if (te == null || n <= 0 || !peutUtiliser(p, x, y, z)) {
                    return;
                }
                int stock = ((Number) appeler(te, "getStoredAmount", new Class<?>[] {int.class, int.class}, id, meta)).intValue();
                n = Math.min(n, stock);
                if (n > 0 && Boolean.TRUE.equals(appeler(te, "removeFromStorage", new Class<?>[] {int.class, int.class, int.class}, id, meta, n))) {
                    donner(p, id, meta, n, null);
                }
                sync(p, te, x, y, z);
            }
        });
        reseau.ajouter("MachineRetrievePacket", new ReseauNG.Recepteur() {
            public void recu(Player p, String paquet, DataInputStream in) throws Exception {
                int x = in.readInt(), y = in.readInt(), z = in.readInt(), id = in.readInt(), meta = in.readInt(), n = in.readInt();
                Object te = poste(p.getWorld(), x, y, z);
                if (te == null || n <= 0 || !peutUtiliser(p, x, y, z)) {
                    return;
                }
                appeler(te, "processCraftQueue", new Class<?>[0]);
                int dispo = ((Number) appeler(te, "getOutputAmount", new Class<?>[] {int.class, int.class}, id, meta)).intValue();
                n = Math.min(n, dispo);
                if (n > 0 && Boolean.TRUE.equals(appeler(te, "removeFromOutput", new Class<?>[] {int.class, int.class, int.class}, id, meta, n))) {
                    Recette r = trouver(String.valueOf(appeler(te, "getWorkstationId", new Class<?>[0])), id, meta);
                    donner(p, id, meta, n, r == null ? null : r.nbt);
                }
                sync(p, te, x, y, z);
            }
        });
        reseau.ajouter("MachineCraftRequestPacket", new ReseauNG.Recepteur() {
            public void recu(Player p, String paquet, DataInputStream in) throws Exception {
                int x = in.readInt(), y = in.readInt(), z = in.readInt(), id = in.readInt(), meta = in.readInt(), n = in.readInt();
                Object te = poste(p.getWorld(), x, y, z);
                if (te == null || n <= 0 || n > 10000 || !peutUtiliser(p, x, y, z)) {
                    return;
                }
                fabriquer(p, te, id, meta, n);
                sync(p, te, x, y, z);
            }
        });
    }

    /** Envoie les recettes (par categorie, en morceaux) puis les informations de l'atelier. */
    void envoyerAtelier(Player p, String demande) {
        final String id = atelierId(demande);
        ConfigurationSection a = atelier(id);
        if (a == null) {
            plugin.getLogger().info("Atelier inconnu demande par " + p.getName() + " : " + demande);
            return;
        }
        final Gson gson = new Gson();
        final List<String> cles = new ArrayList<String>();
        final Map<String, Object> infos = new LinkedHashMap<String, Object>();
        final Map<String, Object> metas = new LinkedHashMap<String, Object>();
        for (String cat : categories(id)) {
            final String cle = cle(id, cat);
            cles.add(cle);
            List<Recette> rs = recettes(id, cat);
            infos.put(cle, Integer.valueOf(rs.size()));
            Map<String, Object> mt = new LinkedHashMap<String, Object>();
            mt.put("name", SSNG.c(a.getString("categories." + cat + ".nom", cat)));
            mt.put("iconName", a.getString("categories." + cat + ".icone", ""));
            metas.put(cle, mt);
            // decoupage : un writeUTF ne depasse pas 64 Ko
            List<List<Map<String, Object>>> morceaux = new ArrayList<List<Map<String, Object>>>();
            List<Map<String, Object>> courant = new ArrayList<Map<String, Object>>();
            int taille = 0;
            for (Recette r : rs) {
                Map<String, Object> m = versClient(r);
                int t = gson.toJson(m).length() * 3;
                if (!courant.isEmpty() && taille + t > 45000) {
                    morceaux.add(courant);
                    courant = new ArrayList<Map<String, Object>>();
                    taille = 0;
                }
                courant.add(m);
                taille += t;
            }
            if (!courant.isEmpty()) {
                morceaux.add(courant);
            }
            final int total = morceaux.size();
            for (int i = 0; i < total; i++) {
                final int idx = i;
                final String json = gson.toJson(morceaux.get(i));
                reseau.envoyer(p, "LoadCategoryRecipesPacket", new ReseauNG.Ecrivain() {
                    public void ecrire(DataOutputStream out) throws Exception {
                        out.writeUTF(cle);
                        out.writeInt(idx);
                        out.writeInt(total);
                        out.writeUTF(json);
                        out.writeUTF("[]");
                    }
                });
            }
        }
        final Map<String, Object> d = new LinkedHashMap<String, Object>();
        d.put("workstationId", id);
        d.put("workstationName", SSNG.c(a.getString("nom", id)));
        d.put("workstationCategories", cles);
        d.put("categoriesInfo", infos);
        d.put("categoriesMetadata", metas);
        d.put("researchLevels", new HashMap<String, Object>());
        d.put("modules", new ArrayList<Object>());
        final String json = gson.toJson(d);
        reseau.envoyer(p, "WorkStationAdditionalDataPacket", new ReseauNG.Ecrivain() {
            public void ecrire(DataOutputStream out) throws Exception {
                out.writeUTF(json);
            }
        });
    }

    private Map<String, Object> versClient(Recette r) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        Map<String, Object> res = new LinkedHashMap<String, Object>();
        res.put("itemId", Integer.valueOf(r.id));
        res.put("metadata", Integer.valueOf(r.meta));
        res.put("quantity", Integer.valueOf(r.qte));
        m.put("result", res);
        List<Map<String, Object>> ing = new ArrayList<Map<String, Object>>();
        for (int[] i : r.ingredients) {
            Map<String, Object> e = new LinkedHashMap<String, Object>();
            e.put("itemId", Integer.valueOf(i[0]));
            e.put("metadata", Integer.valueOf(i[1]));
            e.put("quantity", Integer.valueOf(i[2]));
            e.put("name", nomObjet(i[0], i[1]));
            e.put("maxItemstacks", Integer.valueOf(Math.max(i[2] * plugin.getConfig().getInt("ateliers.stock-max-fabrications", 64), 64)));
            ing.add(e);
        }
        m.put("ingredients", ing);
        List<Map<String, Object>> rr = new ArrayList<Map<String, Object>>();
        for (Map.Entry<String, Integer> e : r.ressources.entrySet()) {
            if (e.getValue().intValue() > 0) {
                Map<String, Object> x = new LinkedHashMap<String, Object>();
                x.put("type", e.getKey());
                x.put("quantity", e.getValue());
                rr.add(x);
            }
        }
        m.put("resourceRequirements", rr);
        m.put("researchRequirements", new ArrayList<Object>());
        m.put("professionRequirements", new ArrayList<Object>());
        m.put("moduleRequirements", new ArrayList<Object>());
        m.put("craftTimeTicks", Integer.valueOf(r.secondes * 20));
        return m;
    }

    private Recette trouver(String atelier, int id, int meta) {
        for (String cat : categories(atelier)) {
            for (Recette r : recettes(atelier, cat)) {
                if (r.id == id && r.meta == meta) {
                    return r;
                }
            }
        }
        return null;
    }

    private void fabriquer(Player p, Object te, int id, int meta, int n) throws Exception {
        String atelier = String.valueOf(appeler(te, "getWorkstationId", new Class<?>[0]));
        Recette r = trouver(atelier, id, meta);
        if (r == null) {
            SSNG.msg(p, "&cAucune recette pour cet objet dans cet atelier.");
            return;
        }
        PlayerInventory inv = p.getInventory();
        // verification des ingredients (stock de l'atelier + inventaire)
        for (int[] i : r.ingredients) {
            int stock = ((Number) appeler(te, "getStoredAmount", new Class<?>[] {int.class, int.class}, i[0], i[1])).intValue();
            if (stock + compter(inv, i[0], i[1]) < i[2] * n) {
                SSNG.msg(p, "&cIl manque " + (i[2] * n - stock - compter(inv, i[0], i[1])) + " x " + nomObjet(i[0], i[1]) + ".");
                return;
            }
        }
        // ressources (electricite, gaz, petrole)
        for (Map.Entry<String, Integer> e : r.ressources.entrySet()) {
            float besoin = e.getValue().floatValue() * n;
            if (besoin > 0 && ressource(te, e.getKey()) < besoin) {
                SSNG.msg(p, "&cPas assez de " + e.getKey() + " dans l'atelier (" + (int) besoin + " requis).");
                return;
            }
        }
        for (Map.Entry<String, Integer> e : r.ressources.entrySet()) {
            float besoin = e.getValue().floatValue() * n;
            if (besoin > 0) {
                consommer(te, e.getKey(), besoin);
            }
        }
        for (int[] i : r.ingredients) {
            int reste = i[2] * n;
            int stock = ((Number) appeler(te, "getStoredAmount", new Class<?>[] {int.class, int.class}, i[0], i[1])).intValue();
            int duStock = Math.min(stock, reste);
            if (duStock > 0) {
                appeler(te, "removeFromStorage", new Class<?>[] {int.class, int.class, int.class}, i[0], i[1], duStock);
                reste -= duStock;
            }
            if (reste > 0) {
                retirerInventaire(inv, i[0], i[1], reste);
            }
        }
        p.updateInventory();
        appeler(te, "addToCraftQueue", new Class<?>[] {int.class, int.class, int.class, int.class, int.class, String.class},
            r.id, r.meta, n, r.secondes * 20, r.qte, atelier + ":" + r.id + ":" + r.meta);
        appeler(te, "processCraftQueue", new Class<?>[0]);
    }

    private static float ressource(Object te, String type) {
        String t = type.toLowerCase();
        String m = t.startsWith("elec") ? "getEnergyStored" : t.startsWith("gas") || t.startsWith("gaz") ? "getGasStored" : "getPetrolStored";
        try {
            return ((Number) te.getClass().getMethod(m).invoke(te)).floatValue();
        } catch (Exception e) {
            return 0f;
        }
    }

    private static void consommer(Object te, String type, float q) {
        String t = type.toLowerCase();
        String m = t.startsWith("elec") ? "consumeElectricity" : t.startsWith("gas") || t.startsWith("gaz") ? "consumeGas" : "consumePetrol";
        try {
            te.getClass().getMethod(m, float.class).invoke(te, Float.valueOf(q));
        } catch (Exception e) {
            // rien
        }
    }

    // ------------------------------------------------------------------ tuile

    private Object poste(World w, int x, int y, int z) {
        try {
            Object monde = Machines.monde(w);
            Object te = monde.getClass().getMethod("func_72796_p", int.class, int.class, int.class).invoke(monde, x, y, z);
            if (te == null) {
                return null;
            }
            te.getClass().getMethod("getIngredientStorage");
            return te;
        } catch (Throwable t) {
            return null;
        }
    }

    private static Object appeler(Object o, String nom, Class<?>[] types, Object... args) throws Exception {
        Method m = o.getClass().getMethod(nom, types);
        return m.invoke(o, args);
    }

    private static float flottant(Object te, String m, float def) {
        try {
            return ((Number) te.getClass().getMethod(m).invoke(te)).floatValue();
        } catch (Exception e) {
            return def;
        }
    }

    /** Equivalent de MachineSyncPacket.sendToPlayer (meme format). */
    void sync(Player p, Object te, final int x, final int y, final int z) {
        try {
            appeler(te, "processCraftQueue", new Class<?>[0]);
            Map<String, Object> d = new LinkedHashMap<String, Object>();
            d.put("storage", appeler(te, "getIngredientStorage", new Class<?>[0]));
            d.put("queue", appeler(te, "getCraftQueueAsMaps", new Class<?>[0]));
            d.put("output", appeler(te, "getOutputBuffer", new Class<?>[0]));
            long last = ((Number) appeler(te, "getLastUpdateTimestamp", new Class<?>[0])).longValue();
            d.put("lastUpdate", Long.valueOf(last));
            if (last > 0) {
                d.put("unitElapsedMs", Long.valueOf(Math.max(0, System.currentTimeMillis() - last)));
            }
            d.put("machineElectricity", Float.valueOf(flottant(te, "getEnergyStored", 0)));
            d.put("machineGas", Float.valueOf(flottant(te, "getGasStored", 0)));
            d.put("machinePetrol", Float.valueOf(flottant(te, "getPetrolStored", 0)));
            d.put("maxElectricity", Float.valueOf(flottant(te, "getMaxEnergyStored", 1000)));
            d.put("maxGas", Float.valueOf(flottant(te, "getMaxGasStored", 1000)));
            d.put("maxPetrol", Float.valueOf(flottant(te, "getMaxPetrolStored", 300)));
            d.put("hasStockage", appeler(te, "hasStockage", new Class<?>[0]));
            d.put("wood", appeler(te, "isWood", new Class<?>[0]));
            final String json = new Gson().toJson(d);
            reseau.envoyer(p, "MachineSyncPacket", new ReseauNG.Ecrivain() {
                public void ecrire(DataOutputStream out) throws Exception {
                    out.writeInt(x);
                    out.writeInt(y);
                    out.writeInt(z);
                    out.writeUTF(json);
                }
            });
        } catch (Throwable t) {
            plugin.getLogger().warning("Synchronisation de l'atelier impossible : " + t);
        }
    }

    private boolean peutUtiliser(Player p, int x, int y, int z) {
        if (SSNG.admin(p)) {
            return true;
        }
        Location l = new Location(p.getWorld(), x, y, z);
        if (plugin.type(p.getWorld()) == MachinesNG.TypeMonde.BUILD) {
            return plugin.peutInteragirIle(p, l);
        }
        return p.getLocation().distance(l) < 12;
    }

    // ------------------------------------------------------------------ objets

    private static int compter(PlayerInventory inv, int id, int meta) {
        int n = 0;
        for (ItemStack s : inv.getContents()) {
            if (s != null && s.getTypeId() == id && s.getDurability() == meta) {
                n += s.getAmount();
            }
        }
        return n;
    }

    private static int retirerInventaire(PlayerInventory inv, int id, int meta, int n) {
        int pris = 0;
        for (int i = 0; i < inv.getSize() && pris < n; i++) {
            ItemStack s = inv.getItem(i);
            if (s == null || s.getTypeId() != id || s.getDurability() != meta) {
                continue;
            }
            int t = Math.min(s.getAmount(), n - pris);
            pris += t;
            if (t >= s.getAmount()) {
                inv.setItem(i, null);
            } else {
                s.setAmount(s.getAmount() - t);
                inv.setItem(i, s);
            }
        }
        return pris;
    }

    private static Class<?> craftItemStack() throws ClassNotFoundException {
        return Class.forName(Bukkit.getServer().getClass().getPackage().getName() + ".inventory.CraftItemStack");
    }

    private static Class<?> nms(String nom) throws ClassNotFoundException {
        return Class.forName(nom, true, Bukkit.getServer().getClass().getClassLoader());
    }

    /** Objet Bukkit (avec NBT complet si fourni) en une seule pile. */
    static ItemStack objet(int id, int meta, int n, String nbt) {
        try {
            Object st;
            Class<?> cStack = nms("net.minecraft.item.ItemStack");
            if (nbt != null && !nbt.isEmpty()) {
                Class<?> cTag = nms("net.minecraft.nbt.NBTTagCompound");
                Object tag = nms("net.minecraft.nbt.CompressedStreamTools").getMethod("func_74792_a", byte[].class)
                    .invoke(null, (Object) java.util.Base64.getDecoder().decode(nbt));
                st = cStack.getMethod("func_77949_a", cTag).invoke(null, tag);
                cStack.getField("field_77994_a").setInt(st, n);
            } else {
                Class<?> cItem = nms("net.minecraft.item.Item");
                Object[] items = (Object[]) cItem.getField("field_77698_e").get(null);
                if (id < 0 || id >= items.length || items[id] == null) {
                    return new ItemStack(id, n, (short) meta);
                }
                st = cStack.getConstructor(int.class, int.class, int.class).newInstance(id, n, meta);
            }
            return (ItemStack) craftItemStack().getMethod("asCraftMirror", cStack).invoke(null, st);
        } catch (Throwable t) {
            return new ItemStack(id, n, (short) meta);
        }
    }

    static int pileMax(int id, int meta) {
        try {
            Class<?> cStack = nms("net.minecraft.item.ItemStack");
            Object st = cStack.getConstructor(int.class, int.class, int.class).newInstance(id, 1, meta);
            return Math.max(1, ((Number) cStack.getMethod("func_77976_d").invoke(st)).intValue());
        } catch (Throwable t) {
            return 64;
        }
    }

    static String nomObjet(int id, int meta) {
        try {
            Class<?> cStack = nms("net.minecraft.item.ItemStack");
            Object st = cStack.getConstructor(int.class, int.class, int.class).newInstance(id, 1, meta);
            return String.valueOf(cStack.getMethod("func_82833_r").invoke(st));
        } catch (Throwable t) {
            return id + ":" + meta;
        }
    }

    /** NBT complet d'un objet Bukkit (base64), ou null s'il n'en a pas. */
    static String nbt(ItemStack s) {
        try {
            Class<?> cStack = nms("net.minecraft.item.ItemStack");
            Class<?> cTag = nms("net.minecraft.nbt.NBTTagCompound");
            Object st = craftItemStack().getMethod("asNMSCopy", ItemStack.class).invoke(null, s);
            if (st == null || cStack.getField("field_77990_d").get(st) == null) {
                return null;
            }
            Object tag = cTag.newInstance();
            cStack.getMethod("func_77955_b", cTag).invoke(st, tag);
            byte[] b = (byte[]) nms("net.minecraft.nbt.CompressedStreamTools").getMethod("func_74798_a", cTag).invoke(null, tag);
            return java.util.Base64.getEncoder().encodeToString(b);
        } catch (Throwable t) {
            return null;
        }
    }

    private static void donner(Player p, int id, int meta, int n, String nbt) {
        int max = pileMax(id, meta);
        while (n > 0) {
            int t = Math.min(n, max);
            n -= t;
            for (ItemStack reste : p.getInventory().addItem(objet(id, meta, t, nbt)).values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), reste);
            }
        }
        p.updateInventory();
    }

    // ------------------------------------------------------------------ editeur

    private static final String TITRE = "Recette d'atelier";

    private void ouvrirEditeur(Player p, String atelier, String cat, int index, int secondes) {
        Inventory inv = Bukkit.createInventory(p, 27, TITRE);
        ItemStack vitre = new ItemStack(Material.THIN_GLASS, 1);
        ItemMeta im = vitre.getItemMeta();
        if (im != null) {
            im.setDisplayName(SSNG.c("&7<- resultat | ingredients en dessous"));
            vitre.setItemMeta(im);
        }
        for (int i = 1; i < 9; i++) {
            inv.setItem(i, vitre.clone());
        }
        if (index >= 0) {
            Recette r = recettes(atelier, cat).get(index);
            inv.setItem(0, objet(r.id, r.meta, Math.min(r.qte, pileMax(r.id, r.meta)), r.nbt));
            int slot = 9;
            for (int[] i : r.ingredients) {
                int reste = i[2];
                int max = pileMax(i[0], i[1]);
                while (reste > 0 && slot < 27) {
                    int t = Math.min(reste, max);
                    inv.setItem(slot++, objet(i[0], i[1], t, null));
                    reste -= t;
                }
            }
        }
        Edition e = new Edition();
        e.atelier = atelierId(atelier);
        e.cat = cat;
        e.index = index;
        e.secondes = secondes;
        editions.put(p.getName(), e);
        p.openInventory(inv);
        SSNG.msg(p, "&eCase 1 : le resultat (la quantite de la pile = quantite fabriquee). &7Les 18 cases du bas : les ingredients. Ferme le coffre pour enregistrer.");
    }

    @EventHandler
    public void clic(InventoryClickEvent ev) {
        if (!TITRE.equals(ev.getView().getTitle()) || !editions.containsKey(ev.getWhoClicked().getName())) {
            return;
        }
        int raw = ev.getRawSlot();
        if (raw >= 1 && raw <= 8) {
            ev.setCancelled(true);
        }
    }

    @EventHandler
    public void fermer(InventoryCloseEvent ev) {
        if (!TITRE.equals(ev.getView().getTitle())) {
            return;
        }
        final Player p = (Player) ev.getPlayer();
        Edition e = editions.remove(p.getName());
        if (e == null) {
            return;
        }
        Inventory inv = ev.getInventory();
        ItemStack res = inv.getItem(0);
        Map<String, Integer> ingr = new LinkedHashMap<String, Integer>();
        List<ItemStack> rendre = new ArrayList<ItemStack>();
        for (int i = 9; i < 27; i++) {
            ItemStack s = inv.getItem(i);
            if (s != null && s.getTypeId() > 0) {
                String k = s.getTypeId() + ":" + s.getDurability();
                ingr.put(k, Integer.valueOf((ingr.containsKey(k) ? ingr.get(k).intValue() : 0) + s.getAmount()));
                rendre.add(s.clone());
            }
            inv.setItem(i, null);
        }
        if (res != null && res.getTypeId() > 0) {
            rendre.add(res.clone());
        }
        inv.setItem(0, null);
        // les objets poses sont rendus a l'admin
        if (e.index < 0) {
            for (ItemStack s : rendre) {
                for (ItemStack r : p.getInventory().addItem(s).values()) {
                    p.getWorld().dropItemNaturally(p.getLocation(), r);
                }
            }
        }
        if (res == null || res.getTypeId() <= 0) {
            SSNG.msg(p, "&cPas de resultat dans la premiere case : recette non enregistree.");
            return;
        }
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("resultat", res.getTypeId() + ":" + res.getDurability());
        m.put("quantite", Integer.valueOf(res.getAmount()));
        String n = nbt(res);
        if (n != null) {
            m.put("nbt", n);
        }
        List<String> li = new ArrayList<String>();
        for (Map.Entry<String, Integer> x : ingr.entrySet()) {
            li.add(x.getKey() + " x" + x.getValue());
        }
        m.put("ingredients", li);
        List<Map<String, Object>> l = brut(e.atelier, e.cat);
        if (e.index >= 0 && e.index < l.size()) {
            Map<String, Object> ancien = l.get(e.index);
            m.put("secondes", ancien.containsKey("secondes") ? ancien.get("secondes") : Integer.valueOf(e.secondes));
            if (ancien.containsKey("ressources")) {
                m.put("ressources", ancien.get("ressources"));
            }
            l.set(e.index, m);
        } else {
            m.put("secondes", Integer.valueOf(e.secondes));
            l.add(m);
        }
        ecrireBrut(e.atelier, e.cat, l);
        p.updateInventory();
        SSNG.msg(p, "&aRecette enregistree dans " + e.atelier + "/" + e.cat + " : " + nomObjet(res.getTypeId(), res.getDurability())
            + " x" + res.getAmount() + " (" + li.size() + " ingredient(s), " + m.get("secondes") + " s). &7Rouvre l'atelier pour la voir.");
    }

    // ------------------------------------------------------------------ commande

    private void aide(CommandSender s) {
        SSNG.msg(s, "&6/atelier liste &7- ateliers et categories");
        SSNG.msg(s, "&6/atelier voir <atelier> <categorie> &7- recettes numerotees");
        SSNG.msg(s, "&6/atelier ajouter <atelier> <categorie> [secondes] &7- nouvelle recette (coffre)");
        SSNG.msg(s, "&6/atelier modifier <atelier> <categorie> <n> &7- modifier une recette (coffre)");
        SSNG.msg(s, "&6/atelier temps <atelier> <categorie> <n> <secondes>");
        SSNG.msg(s, "&6/atelier ressource <atelier> <categorie> <n> <electricity|gas|petrol> <quantite>");
        SSNG.msg(s, "&6/atelier retirer <atelier> <categorie> <n>");
        SSNG.msg(s, "&6/atelier categorie <atelier> <categorie> <nom affiche...> &7- creer / renommer");
        SSNG.msg(s, "&6/atelier supprcategorie <atelier> <categorie>");
        SSNG.msg(s, "&6/atelier recharger &7- relit ateliers.yml");
        SSNG.msg(s, "&7Ateliers : energie, weapons (armement), vehicle (vehicules), foods (nourriture), potions, newbies (debutant)");
    }

    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (!SSNG.admin(s)) {
            SSNG.msg(s, "&cReserve aux admins.");
            return true;
        }
        if (a.length == 0) {
            aide(s);
            return true;
        }
        String sc = a[0].toLowerCase();
        try {
            if (sc.equals("recharger") || sc.equals("reload")) {
                charger();
                SSNG.msg(s, "&aateliers.yml recharge.");
                return true;
            }
            if (sc.equals("autotest")) {
                autotest(s);
                return true;
            }
            if (sc.equals("liste")) {
                for (String at : conf.getConfigurationSection("ateliers").getKeys(false)) {
                    StringBuilder b = new StringBuilder();
                    for (String cat : categories(at)) {
                        b.append(cat).append(" (").append(recettes(at, cat).size()).append(") ");
                    }
                    SSNG.msg(s, "&e" + at + " &7- " + conf.getString("ateliers." + at + ".nom") + " : &f" + b);
                }
                return true;
            }
            if (a.length < 3) {
                aide(s);
                return true;
            }
            String at = atelierId(a[1]);
            String cat = a[2].toLowerCase();
            if (atelier(at) == null) {
                SSNG.msg(s, "&cAtelier inconnu : " + a[1]);
                return true;
            }
            if (sc.equals("categorie")) {
                String nom = a.length > 3 ? joindre(a, 3) : cat;
                conf.set("ateliers." + at + ".categories." + cat + ".nom", nom);
                if (!conf.isSet("ateliers." + at + ".categories." + cat + ".recettes")) {
                    conf.set("ateliers." + at + ".categories." + cat + ".icone", "");
                    conf.set("ateliers." + at + ".categories." + cat + ".recettes", new ArrayList<Object>());
                }
                sauver();
                SSNG.msg(s, "&aCategorie " + at + "/" + cat + " : " + nom);
                return true;
            }
            if (!categories(at).contains(cat)) {
                SSNG.msg(s, "&cCategorie inconnue (cree-la avec /atelier categorie " + at + " " + cat + " <nom>).");
                return true;
            }
            if (sc.equals("supprcategorie")) {
                conf.set("ateliers." + at + ".categories." + cat, null);
                sauver();
                SSNG.msg(s, "&aCategorie supprimee.");
                return true;
            }
            if (sc.equals("voir")) {
                List<Recette> l = recettes(at, cat);
                SSNG.msg(s, "&e" + at + "/" + cat + " : " + l.size() + " recette(s)");
                for (int i = 0; i < l.size(); i++) {
                    Recette r = l.get(i);
                    StringBuilder b = new StringBuilder();
                    for (int[] x : r.ingredients) {
                        b.append(x[2]).append(" ").append(nomObjet(x[0], x[1])).append(", ");
                    }
                    SSNG.msg(s, "&6#" + (i + 1) + " &f" + r.qte + " x " + nomObjet(r.id, r.meta) + " &7(" + r.secondes + " s"
                        + (r.ressources.isEmpty() ? "" : ", " + r.ressources) + ") &8<- " + b);
                }
                return true;
            }
            if (sc.equals("ajouter")) {
                if (!(s instanceof Player)) {
                    SSNG.msg(s, "&cEn jeu uniquement.");
                    return true;
                }
                ouvrirEditeur((Player) s, at, cat, -1, a.length > 3 ? Integer.parseInt(a[3]) : 10);
                return true;
            }
            if (a.length < 4) {
                aide(s);
                return true;
            }
            List<Map<String, Object>> l = brut(at, cat);
            int n = Integer.parseInt(a[3]) - 1;
            if (n < 0 || n >= l.size()) {
                SSNG.msg(s, "&cNumero de recette invalide (voir /atelier voir " + at + " " + cat + ").");
                return true;
            }
            if (sc.equals("modifier")) {
                if (!(s instanceof Player)) {
                    SSNG.msg(s, "&cEn jeu uniquement.");
                    return true;
                }
                ouvrirEditeur((Player) s, at, cat, n, nombre(l.get(n).get("secondes"), 10));
                return true;
            }
            if (sc.equals("retirer")) {
                l.remove(n);
                ecrireBrut(at, cat, l);
                SSNG.msg(s, "&aRecette retiree.");
                return true;
            }
            if (sc.equals("temps") && a.length > 4) {
                l.get(n).put("secondes", Integer.valueOf(Math.max(0, Integer.parseInt(a[4]))));
                ecrireBrut(at, cat, l);
                SSNG.msg(s, "&aTemps de fabrication : " + a[4] + " s.");
                return true;
            }
            if (sc.equals("ressource") && a.length > 5) {
                String type = a[4].toLowerCase();
                if (type.startsWith("elec")) {
                    type = "electricity";
                } else if (type.startsWith("gaz") || type.startsWith("gas")) {
                    type = "gas";
                } else {
                    type = "petrol";
                }
                Map<String, Object> res = new LinkedHashMap<String, Object>();
                Object anc = l.get(n).get("ressources");
                if (anc instanceof Map) {
                    for (Map.Entry<?, ?> e : ((Map<?, ?>) anc).entrySet()) {
                        res.put(e.getKey().toString(), e.getValue());
                    }
                }
                int q = Integer.parseInt(a[5]);
                if (q <= 0) {
                    res.remove(type);
                } else {
                    res.put(type, Integer.valueOf(q));
                }
                l.get(n).put("ressources", res);
                ecrireBrut(at, cat, l);
                SSNG.msg(s, "&aRessources de la recette : " + res);
                return true;
            }
            aide(s);
        } catch (NumberFormatException e) {
            SSNG.msg(s, "&cNombre invalide.");
        }
        return true;
    }

    /** Tuiles des machines chargees dans un monde (pour les tests). */
    static List<Object> tuiles(World w) {
        List<Object> l = new ArrayList<Object>();
        try {
            Object monde = Machines.monde(w);
            l.addAll((List<?>) monde.getClass().getField("field_73009_h").get(monde));
        } catch (Throwable t) {
            // rien
        }
        return l;
    }

    /** Test sans client : stock, file de fabrication, sortie, synchronisation, objets. */
    private void autotest(CommandSender s) {
        World w = plugin.monde(MachinesNG.TypeMonde.TEST);
        if (w == null) {
            SSNG.msg(s, "&cMonde Test introuvable.");
            return;
        }
        for (int cx = (Machines.VITRINE_X >> 4) - 1; cx <= ((Machines.VITRINE_X + Machines.ESPACE * Machines.COLONNES) >> 4) + 1; cx++) {
            for (int cz = (Machines.VITRINE_Z >> 4) - 1; cz <= ((Machines.VITRINE_Z + Machines.ESPACE * 12) >> 4) + 1; cz++) {
                w.loadChunk(cx, cz);
            }
        }
        int n = 0;
        for (Object te : tuiles(w)) {
            try {
                te.getClass().getMethod("getIngredientStorage");
            } catch (Exception e) {
                continue;
            }
            n++;
            try {
                String id = String.valueOf(appeler(te, "getWorkstationId", new Class<?>[0]));
                appeler(te, "addToStorage", new Class<?>[] {int.class, int.class, int.class}, 4, 0, 3);
                int st = ((Number) appeler(te, "getStoredAmount", new Class<?>[] {int.class, int.class}, 4, 0)).intValue();
                appeler(te, "removeFromStorage", new Class<?>[] {int.class, int.class, int.class}, 4, 0, 3);
                appeler(te, "addToCraftQueue", new Class<?>[] {int.class, int.class, int.class, int.class, int.class, String.class}, 274, 0, 2, 0, 1, "autotest");
                appeler(te, "processCraftQueue", new Class<?>[0]);
                int out = ((Number) appeler(te, "getOutputAmount", new Class<?>[] {int.class, int.class}, 274, 0)).intValue();
                appeler(te, "removeFromOutput", new Class<?>[] {int.class, int.class, int.class}, 274, 0, out);
                Map<String, Object> d = new LinkedHashMap<String, Object>();
                d.put("storage", appeler(te, "getIngredientStorage", new Class<?>[0]));
                d.put("queue", appeler(te, "getCraftQueueAsMaps", new Class<?>[0]));
                d.put("hasStockage", appeler(te, "hasStockage", new Class<?>[0]));
                d.put("elec", Float.valueOf(flottant(te, "getEnergyStored", -1)));
                SSNG.msg(s, "&aAtelier " + id + " (" + te.getClass().getSimpleName() + ") : stock " + st + "/3, sortie " + out + "/2, sync "
                    + new Gson().toJson(d) + ", recettes " + (atelier(id) == null ? "AUCUNE CONFIG" : categories(id).size() + " categories"));
            } catch (Throwable t) {
                SSNG.msg(s, "&cAtelier " + te.getClass().getSimpleName() + " : " + t);
                plugin.getLogger().log(java.util.logging.Level.WARNING, "autotest atelier", t);
            }
        }
        SSNG.msg(s, "&e" + n + " atelier(s) trouve(s) dans la vitrine.");
        ItemStack o = objet(274, 0, 1, null);
        SSNG.msg(s, "&eObjet 274 : " + o + " nom=" + nomObjet(274, 0) + " pileMax=" + pileMax(274, 0) + " nomCereale=" + nomObjet(296, 0));
        ItemStack ench = objet(276, 0, 1, null);
        ench.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.DAMAGE_ALL, 3);
        String b64 = nbt(ench);
        ItemStack retour = objet(276, 0, 1, b64);
        SSNG.msg(s, "&eNBT : " + (b64 == null ? "null" : b64.length() + " car.") + " -> " + retour.getEnchantments());
        for (String cat : categories("newbies")) {
            for (Recette r : recettes("newbies", cat)) {
                SSNG.msg(s, "&7json " + cat + " : " + new Gson().toJson(versClient(r)));
            }
        }
    }

    private static String joindre(String[] a, int debut) {
        StringBuilder b = new StringBuilder();
        for (int i = debut; i < a.length; i++) {
            b.append(i > debut ? " " : "").append(a[i]);
        }
        return b.toString();
    }
}
