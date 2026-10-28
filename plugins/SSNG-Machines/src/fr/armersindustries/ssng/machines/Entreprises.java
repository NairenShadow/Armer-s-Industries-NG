package fr.armersindustries.ssng.machines;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.google.gson.Gson;

import fr.armersindustries.ssng.core.SSNG;
import fr.armersindustries.ssng.core.ReseauNG;

/**
 * Entreprises NationsGlory (petrole, electricite, agriculture, commerce...) et marches.
 * Remplace la partie "entreprises" du plugin prive du serveur officiel :
 * - achats / ventes aux machines (citerne, accumulateur a plasma, groupe electrogene, silo) ;
 * - interface NG des entreprises (liste, fiche, onglets petrole / electricite / agriculture / commerce, creation) ;
 * - reserves et prix definis par les admins (/entreprise), marche des cereales configurable.
 * Donnees : plugins/SSNG-Machines/entreprises.yml (UTF-8).
 */
public class Entreprises implements CommandExecutor {

    static final String[] TYPES = {"petrol", "electric", "farm", "trader", "pvp", "repair", "loan", "realestate", "casino", "bet"};

    private final MachinesNG plugin;
    private final ReseauNG reseau;
    private final File fichier;
    private YamlConfiguration conf;
    private final Gson gson = new Gson();
    /** cle de cereale (ex. cereal_ble) -> {item id, meta}, nom affiche */
    private final Map<String, int[]> cereales = new LinkedHashMap<String, int[]>();
    private final Map<String, String> nomsCereales = new LinkedHashMap<String, String>();

    public Entreprises(MachinesNG plugin, ReseauNG reseau) {
        this.plugin = plugin;
        this.reseau = reseau;
        this.fichier = new File(plugin.getDataFolder(), "entreprises.yml");
        chargerCereales();
        charger();
    }

    // ------------------------------------------------------------------ donnees

    private void chargerCereales() {
        try {
            File mj = new File("ngcore_cache" + File.separator + "machines.json");
            if (mj.exists()) {
                com.google.gson.JsonObject o = new com.google.gson.JsonParser().parse(new String(java.nio.file.Files.readAllBytes(mj.toPath()), "UTF-8")).getAsJsonObject();
                for (com.google.gson.JsonElement e : o.getAsJsonArray("cereals")) {
                    com.google.gson.JsonObject c = e.getAsJsonObject();
                    String k = c.get("name_key").getAsString();
                    cereales.put(k, new int[] {c.get("item_id").getAsInt(), c.has("meta") ? c.get("meta").getAsInt() : 0});
                    nomsCereales.put(k, c.has("label_fr") ? c.get("label_fr").getAsString() : k);
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Cereales NG illisibles (ngcore_cache/machines.json) : " + t);
        }
        if (cereales.isEmpty()) {
            cereales.put("wheat", new int[] {296, 0});
            nomsCereales.put("wheat", "Ble");
        }
    }

    public void charger() {
        conf = fichier.exists() ? Yaml.charger(fichier) : new YamlConfiguration();
        boolean change = false;
        if (!conf.isSet("reglages")) {
            conf.set("reglages.paiement", Boolean.TRUE);
            conf.set("reglages.creation-par-les-joueurs", Boolean.TRUE);
            conf.set("reglages.prix-creation", Integer.valueOf(15000));
            conf.set("reglages.types-autorises", java.util.Arrays.asList("petrol", "electric", "farm", "trader"));
            conf.set("reglages.membres-max", Integer.valueOf(20));
            change = true;
        }
        if (!conf.isSet("marche")) {
            conf.set("marche.taxe-vente", Double.valueOf(0.2));
            conf.set("marche.marge-vente", Integer.valueOf(100));
            conf.set("marche.stock-illimite", Boolean.TRUE);
            change = true;
        }
        for (String k : cereales.keySet()) {
            if (!conf.isSet("marche.cereales." + k)) {
                conf.set("marche.cereales." + k, Integer.valueOf(1000));
                change = true;
            }
        }
        if (!conf.isConfigurationSection("entreprises")) {
            // entreprises d'Etat de depart (reserves infinies), modifiables avec /entreprise
            creer("PetroleEtat", "petrol", "Serveur", false);
            conf.set("entreprises.petroleetat.infinie", Boolean.TRUE);
            conf.set("entreprises.petroleetat.prix", Integer.valueOf(150));
            creer("ElecEtat", "electric", "Serveur", false);
            conf.set("entreprises.elecetat.infinie", Boolean.TRUE);
            conf.set("entreprises.elecetat.prix", Integer.valueOf(2000));
            creer("FermeEtat", "farm", "Serveur", false);
            conf.set("entreprises.fermeetat.infinie", Boolean.TRUE);
            change = true;
        }
        if (change) {
            sauver();
        }
    }

    private void sauver() {
        try {
            Yaml.sauver(conf, fichier);
        } catch (Exception e) {
            plugin.getLogger().warning("entreprises.yml : " + e);
        }
    }

    private static String cle(String nom) {
        return nom == null ? "" : nom.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
    }

    ConfigurationSection ent(String nom) {
        return conf.getConfigurationSection("entreprises." + cle(nom));
    }

    List<ConfigurationSection> toutes() {
        List<ConfigurationSection> l = new ArrayList<ConfigurationSection>();
        ConfigurationSection s = conf.getConfigurationSection("entreprises");
        if (s != null) {
            for (String k : s.getKeys(false)) {
                l.add(s.getConfigurationSection(k));
            }
        }
        return l;
    }

    List<ConfigurationSection> deType(String type) {
        List<ConfigurationSection> l = new ArrayList<ConfigurationSection>();
        for (ConfigurationSection e : toutes()) {
            if (type.equals(e.getString("type"))) {
                l.add(e);
            }
        }
        return l;
    }

    private ConfigurationSection creer(String nom, String type, String chef, boolean sauve) {
        String b = "entreprises." + cle(nom);
        conf.set(b + ".nom", nom);
        conf.set(b + ".type", type);
        conf.set(b + ".chef", chef);
        conf.set(b + ".membres", new ArrayList<String>());
        conf.set(b + ".description", "");
        conf.set(b + ".ouverte", Boolean.TRUE);
        conf.set(b + ".creation", Long.valueOf(System.currentTimeMillis()));
        conf.set(b + ".prix", Integer.valueOf(type.equals("electric") ? 2000 : 150));
        conf.set(b + ".reserve", Double.valueOf(0));
        conf.set(b + ".infinie", Boolean.FALSE);
        conf.set(b + ".chiffre", Double.valueOf(0));
        conf.set(b + ".total", Double.valueOf(0));
        if (sauve) {
            sauver();
        }
        return conf.getConfigurationSection(b);
    }

    private static boolean membre(ConfigurationSection e, String joueur) {
        if (e == null) {
            return false;
        }
        if (joueur.equalsIgnoreCase(e.getString("chef", ""))) {
            return true;
        }
        for (String m : e.getStringList("membres")) {
            if (m.equalsIgnoreCase(joueur)) {
                return true;
            }
        }
        return false;
    }

    /** Premiere entreprise du type dont le joueur fait partie. */
    ConfigurationSection entrepriseDe(String joueur, String type) {
        for (ConfigurationSection e : deType(type)) {
            if (membre(e, joueur)) {
                return e;
            }
        }
        return null;
    }

    private boolean estChef(Player p, ConfigurationSection e) {
        return SSNG.admin(p) || p.getName().equalsIgnoreCase(e.getString("chef", ""));
    }

    private static double reserve(ConfigurationSection e) {
        return e.getBoolean("infinie") ? Double.MAX_VALUE : e.getDouble("reserve");
    }

    private void ajouterReserve(ConfigurationSection e, double q) {
        if (!e.getBoolean("infinie")) {
            e.set("reserve", Double.valueOf(Math.max(0, e.getDouble("reserve") + q)));
        }
    }

    // ------------------------------------------------------------------ argent (Essentials)

    private Method ecoHas;
    private Method ecoSub;
    private Method ecoAdd;
    private Method ecoGet;
    private boolean ecoCherche;

    private boolean eco() {
        if (!ecoCherche) {
            ecoCherche = true;
            try {
                if (Bukkit.getPluginManager().getPlugin("Essentials") != null) {
                    Class<?> c = Class.forName("com.earth2me.essentials.api.Economy", true,
                        Bukkit.getPluginManager().getPlugin("Essentials").getClass().getClassLoader());
                    ecoHas = c.getMethod("hasEnough", String.class, double.class);
                    ecoSub = c.getMethod("subtract", String.class, double.class);
                    ecoAdd = c.getMethod("add", String.class, double.class);
                    ecoGet = c.getMethod("getMoney", String.class);
                }
            } catch (Throwable t) {
                plugin.getLogger().info("Economie Essentials indisponible, achats gratuits : " + t);
            }
        }
        return ecoHas != null && conf.getBoolean("reglages.paiement", true);
    }

    /** Debite le joueur ; false s'il n'a pas assez. */
    boolean payer(Player p, double montant) {
        if (montant <= 0 || !eco()) {
            return true;
        }
        try {
            if (!((Boolean) ecoHas.invoke(null, p.getName(), Double.valueOf(montant))).booleanValue()) {
                SSNG.msg(p, "&cTu n'as pas assez d'argent (" + format(montant) + " $ requis).");
                return false;
            }
            ecoSub.invoke(null, p.getName(), Double.valueOf(montant));
            return true;
        } catch (Throwable t) {
            SSNG.msg(p, "&cPaiement impossible : " + t.getClass().getSimpleName());
            return false;
        }
    }

    void crediter(String joueur, double montant) {
        if (montant <= 0 || !eco()) {
            return;
        }
        try {
            ecoAdd.invoke(null, joueur, Double.valueOf(montant));
        } catch (Throwable t) {
            // joueur inconnu de l'economie
        }
    }

    private double solde(String joueur) {
        if (!eco()) {
            return 1.0E9;
        }
        try {
            return ((Number) ecoGet.invoke(null, joueur)).doubleValue();
        } catch (Throwable t) {
            return 0;
        }
    }

    static String format(double d) {
        return String.format(Locale.FRANCE, "%,.2f", Double.valueOf(d));
    }

    // ------------------------------------------------------------------ tuiles

    private static Object tuile(World w, int x, int y, int z) {
        try {
            Object monde = Machines.monde(w);
            return monde.getClass().getMethod("func_72796_p", int.class, int.class, int.class).invoke(monde, x, y, z);
        } catch (Throwable t) {
            return null;
        }
    }

    private static float getF(Object o, String m) {
        try {
            return ((Number) o.getClass().getMethod(m).invoke(o)).floatValue();
        } catch (Exception e) {
            return 0;
        }
    }

    private static Field champ(Class<?> c, String nom) {
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            try {
                Field f = k.getDeclaredField(nom);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException e) {
                // parent
            }
        }
        return null;
    }

    private static void setF(Object o, String champ, float v) {
        Field f = champ(o.getClass(), champ);
        if (f != null) {
            try {
                f.setFloat(o, v);
            } catch (Exception e) {
                // rien
            }
        }
    }

    private static void energie(Object te, float v) {
        try {
            te.getClass().getMethod("setEnergyStored", float.class).invoke(te, Float.valueOf(v));
        } catch (Exception e) {
            setF(te, "energyStored", v);
        }
    }

    private static void majBloc(Player p, int x, int y, int z) {
        try {
            Object monde = Machines.monde(p.getWorld());
            monde.getClass().getMethod("func_72845_h", int.class, int.class, int.class).invoke(monde, x, y, z);
        } catch (Throwable t) {
            // pas grave
        }
    }

    private static double arrondi(double d) {
        return Math.ceil(d * 100.0) / 100.0;
    }

    // ------------------------------------------------------------------ paquets

    public void demarrer() {
        // --- fenetres des machines
        reseau.ajouter("FluidTankInfosMachinePacket", new ReseauNG.Recepteur() {
            public void recu(Player p, String paquet, DataInputStream in) {
                infosCiterne(p);
            }
        });
        reseau.ajouter("PlasmaAccumulatorInfosMachinePacket", new ReseauNG.Recepteur() {
            public void recu(Player p, String paquet, DataInputStream in) {
                infosAccumulateur(p);
            }
        });
        reseau.ajouter("ElectricGeneratorInfosMachinePacket", new ReseauNG.Recepteur() {
            public void recu(Player p, String paquet, DataInputStream in) {
                infosGenerateur(p);
            }
        });
        reseau.ajouter("SiloInfosMachinePacket", new ReseauNG.Recepteur() {
            public void recu(Player p, String paquet, DataInputStream in) {
                infosSilo(p);
            }
        });
        reseau.ajouter("NEISiloPricesRequestPacket", new ReseauNG.Recepteur() {
            public void recu(Player p, String paquet, DataInputStream in) {
                final String json = gson.toJson(prixCereales());
                reseau.envoyer(p, "NEISiloPricesResponsePacket", new ReseauNG.Ecrivain() {
                    public void ecrire(DataOutputStream out) throws Exception {
                        out.writeUTF(json);
                    }
                });
            }
        });
        // --- achats / ventes
        reseau.ajouter("FluidTankBuyPacket", new ReseauNG.Recepteur() {
            public void recu(Player p, String paquet, DataInputStream in) throws Exception {
                String ent = in.readUTF();
                int qte = in.readInt();
                in.readInt();
                acheterPetrole(p, ent, qte, in.readInt(), in.readInt(), in.readInt());
                infosCiterne(p);
            }
        });
        reseau.ajouter("FluidTankSellPacket", new ReseauNG.Recepteur() {
            public void recu(Player p, String paquet, DataInputStream in) throws Exception {
                int qte = in.readInt();
                vendrePetrole(p, qte, in.readInt(), in.readInt(), in.readInt());
                infosCiterne(p);
            }
        });
        reseau.ajouter("PlasmaAccumulatorBuyPacket", new ReseauNG.Recepteur() {
            public void recu(Player p, String paquet, DataInputStream in) throws Exception {
                String ent = in.readUTF();
                int qte = in.readInt();
                in.readInt();
                acheterElec(p, ent, qte, in.readInt(), in.readInt(), in.readInt());
                infosAccumulateur(p);
            }
        });
        reseau.ajouter("PlasmaAccumulatorSellPacket", new ReseauNG.Recepteur() {
            public void recu(Player p, String paquet, DataInputStream in) throws Exception {
                int qte = in.readInt();
                vendreElec(p, qte, in.readInt(), in.readInt(), in.readInt());
                infosAccumulateur(p);
            }
        });
        reseau.ajouter("ElectricGeneratorBuyPacket", new ReseauNG.Recepteur() {
            public void recu(Player p, String paquet, DataInputStream in) throws Exception {
                String ent = in.readUTF();
                int qte = in.readInt();
                in.readInt();
                acheterElec(p, ent, qte, in.readInt(), in.readInt(), in.readInt());
                infosGenerateur(p);
            }
        });
        reseau.ajouter("SiloBuyPacket", new ReseauNG.Recepteur() {
            public void recu(Player p, String paquet, DataInputStream in) throws Exception {
                String cereale = in.readUTF();
                int qte = in.readInt();
                in.readInt();
                acheterCereale(p, cereale, qte, in.readInt(), in.readInt(), in.readInt());
                infosSilo(p);
            }
        });
        reseau.ajouter("SiloSellPacket", new ReseauNG.Recepteur() {
            public void recu(Player p, String paquet, DataInputStream in) throws Exception {
                String cereale = in.readUTF();
                int qte = in.readInt();
                in.readInt();
                vendreCereale(p, cereale, qte, in.readInt(), in.readInt(), in.readInt());
            }
        });
        reseau.ajouter("SiloGetCerealsPacket", new ReseauNG.Recepteur() {
            public void recu(Player p, String paquet, DataInputStream in) throws Exception {
                recupererCereales(p, in.readInt(), in.readInt(), in.readInt());
            }
        });
        // --- interface des entreprises
        reseau.ajouter("EnterpriseListDataPacket", new ReseauNG.Recepteur() {
            public void recu(Player p, String paquet, DataInputStream in) {
                envoyerListe(p);
            }
        });
        reseau.ajouter("EnterpriseMainDataPacket", new ReseauNG.Recepteur() {
            public void recu(Player p, String paquet, DataInputStream in) throws Exception {
                envoyerFiche(p, in.readUTF());
            }
        });
        reseau.ajouter("EnterprisePlayerDataPacket", new ReseauNG.Recepteur() {
            public void recu(Player p, String paquet, DataInputStream in) throws Exception {
                final String joueur = in.readUTF();
                final String nom = in.readUTF();
                ConfigurationSection e = ent(nom);
                final Map<String, Object> m = new LinkedHashMap<String, Object>();
                m.put("role", e != null && joueur.equalsIgnoreCase(e.getString("chef")) ? "leader" : "member");
                m.put("offline_days", Bukkit.getPlayerExact(joueur) != null ? "online" : "?");
                reseau.envoyer(p, paquet, new ReseauNG.Ecrivain() {
                    public void ecrire(DataOutputStream out) throws Exception {
                        out.writeUTF(joueur);
                        out.writeUTF(nom);
                        out.writeUTF(gson.toJson(m));
                    }
                });
            }
        });
        reseau.ajouter("EnterprisePetrolDataPacket", new ReseauNG.Recepteur() {
            public void recu(Player p, String paquet, DataInputStream in) throws Exception {
                envoyerOngletEnergie(p, paquet, in.readUTF());
            }
        });
        reseau.ajouter("EnterpriseElectricDataPacket", new ReseauNG.Recepteur() {
            public void recu(Player p, String paquet, DataInputStream in) throws Exception {
                envoyerOngletEnergie(p, paquet, in.readUTF());
            }
        });
        reseau.ajouter("EnterpriseFarmDataPacket", new ReseauNG.Recepteur() {
            public void recu(Player p, String paquet, DataInputStream in) throws Exception {
                ConfigurationSection e = ent(in.readUTF());
                if (e == null) {
                    return;
                }
                final Map<String, Object> m = new LinkedHashMap<String, Object>();
                m.put("name", e.getString("nom"));
                m.put("today", Double.valueOf(e.getDouble("aujourdhui")));
                m.put("total", Double.valueOf(e.getDouble("total")));
                List<String> top = new ArrayList<String>();
                ConfigurationSection rc = e.getConfigurationSection("reserves-cereales");
                if (rc != null) {
                    for (String k : rc.getKeys(false)) {
                        top.add(k + "##" + rc.getDouble(k));
                    }
                }
                m.put("collected_top", top);
                reseau.envoyer(p, paquet, new ReseauNG.Ecrivain() {
                    public void ecrire(DataOutputStream out) throws Exception {
                        out.writeUTF(gson.toJson(m));
                        out.writeUTF("{}");
                    }
                });
            }
        });
        reseau.ajouter("EnterpriseTraderDataPacket", new ReseauNG.Recepteur() {
            public void recu(Player p, String paquet, DataInputStream in) throws Exception {
                ConfigurationSection e = ent(in.readUTF());
                if (e == null) {
                    return;
                }
                final Map<String, Object> m = new LinkedHashMap<String, Object>();
                m.put("name", e.getString("nom"));
                m.put("my_investment", Double.valueOf(e.getDouble("investissements." + p.getName().toLowerCase())));
                ConfigurationSection inv = e.getConfigurationSection("investissements");
                double somme = 0;
                int n = 0;
                if (inv != null) {
                    for (String k : inv.getKeys(false)) {
                        somme += inv.getDouble(k);
                        n++;
                    }
                }
                m.put("investors", Double.valueOf(n));
                m.put("sum_investment", Double.valueOf(somme));
                m.put("total_generated", Double.valueOf(e.getDouble("chiffre")));
                m.put("today_percent", Double.valueOf(e.getDouble("variation-jour")));
                reseau.envoyer(p, paquet, new ReseauNG.Ecrivain() {
                    public void ecrire(DataOutputStream out) throws Exception {
                        out.writeUTF(gson.toJson(m));
                        out.writeUTF("{}");
                    }
                });
            }
        });
        ReseauNG.Recepteur prix = new ReseauNG.Recepteur() {
            public void recu(Player p, String paquet, DataInputStream in) throws Exception {
                ConfigurationSection e = ent(in.readUTF());
                int px = in.readInt();
                boolean tous = in.readBoolean();
                boolean pays = in.readBoolean();
                boolean allies = in.readBoolean();
                if (e == null || !estChef(p, e) || px < 0) {
                    SSNG.msg(p, "&cSeul le chef de l'entreprise peut changer le prix.");
                    return;
                }
                e.set("prix", Integer.valueOf(px));
                e.set("autoriser-tous", Boolean.valueOf(tous));
                e.set("autoriser-pays", Boolean.valueOf(pays));
                e.set("autoriser-allies", Boolean.valueOf(allies));
                sauver();
                SSNG.msg(p, "&aPrix de " + e.getString("nom") + " : " + px + " $" + (e.getString("type").equals("electric") ? " / 1 000 000 UE" : " / 100 L"));
            }
        };
        reseau.ajouter("EnterprisePetrolPricePacket", prix);
        reseau.ajouter("EnterpriseElectricPricePacket", prix);
        reseau.ajouter("EnterpriseGlobalMarketDataPacket", new ReseauNG.Recepteur() {
            public void recu(Player p, String paquet, DataInputStream in) {
                final Map<String, Integer> px = new LinkedHashMap<String, Integer>();
                final Map<String, String> var = new LinkedHashMap<String, String>();
                for (Map.Entry<String, Double> e : prixCereales().entrySet()) {
                    px.put(e.getKey(), Integer.valueOf(e.getValue().intValue()));
                    var.put(e.getKey(), "0");
                }
                reseau.envoyer(p, paquet, new ReseauNG.Ecrivain() {
                    public void ecrire(DataOutputStream out) throws Exception {
                        out.writeUTF(gson.toJson(px));
                        out.writeUTF(gson.toJson(var));
                        out.writeUTF("{}");
                        out.writeUTF("{}");
                        out.writeUTF("{}");
                    }
                });
            }
        });
        reseau.ajouter("EnterpriseCreateDataPacket", new ReseauNG.Recepteur() {
            public void recu(Player p, String paquet, DataInputStream in) {
                final Map<String, Object> m = new LinkedHashMap<String, Object>();
                int n = 0;
                List<String> possedes = new ArrayList<String>();
                for (ConfigurationSection e : toutes()) {
                    if (membre(e, p.getName())) {
                        n++;
                        possedes.add(e.getString("type"));
                    }
                }
                m.put("count", Integer.valueOf(n));
                m.put("canCreate", Integer.valueOf(conf.getBoolean("reglages.creation-par-les-joueurs", true) || SSNG.admin(p) ? 1 : 0));
                m.put("canJoin", Integer.valueOf(1));
                m.put("toxicityTax", Integer.valueOf(0));
                m.put("forbiddenTypes", new ArrayList<String>());
                m.put("blockedByResearchTypes", new ArrayList<String>());
                m.put("types", possedes);
                m.put("opOnlyTypes", new ArrayList<String>());
                m.put("priceNGCoins", Integer.valueOf(0));
                m.put("researchRequirements", new HashMap<String, Object>());
                m.put("playerBalance", Double.valueOf(solde(p.getName())));
                final List<String> types = conf.getStringList("reglages.types-autorises");
                reseau.envoyer(p, paquet, new ReseauNG.Ecrivain() {
                    public void ecrire(DataOutputStream out) throws Exception {
                        out.writeUTF(gson.toJson(m));
                        out.writeUTF(gson.toJson(types));
                    }
                });
            }
        });
        reseau.ajouter("EnterpriseNameCheckPacket", new ReseauNG.Recepteur() {
            public void recu(Player p, String paquet, DataInputStream in) throws Exception {
                final String nom = in.readUTF();
                final boolean libre = nomValide(nom) && ent(nom) == null;
                reseau.envoyer(p, "EnterpriseNameCheckResponsePacket", new ReseauNG.Ecrivain() {
                    public void ecrire(DataOutputStream out) throws Exception {
                        out.writeUTF(nom);
                        out.writeBoolean(libre);
                    }
                });
            }
        });
        reseau.ajouter("EnterpriseCreatePacket", new ReseauNG.Recepteur() {
            public void recu(Player p, String paquet, DataInputStream in) throws Exception {
                String nom = in.readUTF();
                String type = in.readUTF();
                final String err = creationJoueur(p, nom, type);
                reseau.envoyer(p, paquet, new ReseauNG.Ecrivain() {
                    public void ecrire(DataOutputStream out) throws Exception {
                        out.writeUTF(err);
                    }
                });
            }
        });
        reseau.ajouter("EnterpriseLeavePacket", new ReseauNG.Recepteur() {
            public void recu(Player p, String paquet, DataInputStream in) throws Exception {
                ConfigurationSection e = ent(in.readUTF());
                if (e != null && retirerMembre(e, p.getName())) {
                    sauver();
                    SSNG.msg(p, "&aTu as quitte " + e.getString("nom") + ".");
                }
            }
        });
        reseau.ajouter("EnterpriseDisbandPacket", new ReseauNG.Recepteur() {
            public void recu(Player p, String paquet, DataInputStream in) throws Exception {
                ConfigurationSection e = ent(in.readUTF());
                if (e != null && estChef(p, e)) {
                    SSNG.msg(p, "&aEntreprise " + e.getString("nom") + " dissoute.");
                    conf.set("entreprises." + e.getName(), null);
                    sauver();
                }
            }
        });
    }

    // ------------------------------------------------------------------ machines : infos

    /** "nom##prix##disponible" pour les entreprises ouvertes d'un type. */
    private List<String> offres(String type) {
        List<String> l = new ArrayList<String>();
        for (ConfigurationSection e : deType(type)) {
            if (!e.getBoolean("ouverte", true)) {
                continue;
            }
            double r = e.getBoolean("infinie") ? 1.0E12 : e.getDouble("reserve");
            l.add(e.getString("nom") + "##" + e.getInt("prix") + "##" + (long) r);
        }
        return l;
    }

    private void infosCiterne(Player p) {
        final String liste = gson.toJson(offres("petrol"));
        final ConfigurationSection mienne = entrepriseDe(p.getName(), "petrol");
        reseau.envoyer(p, "FluidTankInfosMachinePacket", new ReseauNG.Ecrivain() {
            public void ecrire(DataOutputStream out) throws Exception {
                out.writeUTF(liste);
                out.writeBoolean(mienne != null);
                out.writeUTF(mienne == null ? "" : mienne.getString("drapeau", ""));
                out.writeBoolean(true);
                out.writeBoolean(false);
            }
        });
    }

    private void infosAccumulateur(Player p) {
        final String liste = gson.toJson(offres("electric"));
        final ConfigurationSection mienne = entrepriseDe(p.getName(), "electric");
        reseau.envoyer(p, "PlasmaAccumulatorInfosMachinePacket", new ReseauNG.Ecrivain() {
            public void ecrire(DataOutputStream out) throws Exception {
                out.writeUTF(liste);
                out.writeBoolean(mienne != null);
                out.writeUTF(mienne == null ? "" : mienne.getString("nom"));
                out.writeBoolean(true);
                out.writeBoolean(false);
            }
        });
    }

    private void infosGenerateur(Player p) {
        final String liste = gson.toJson(offres("electric"));
        reseau.envoyer(p, "ElectricGeneratorInfosMachinePacket", new ReseauNG.Ecrivain() {
            public void ecrire(DataOutputStream out) throws Exception {
                out.writeUTF(liste);
                out.writeBoolean(false);
            }
        });
    }

    Map<String, Double> prixCereales() {
        Map<String, Double> m = new LinkedHashMap<String, Double>();
        ConfigurationSection s = conf.getConfigurationSection("marche.cereales");
        if (s != null) {
            for (String k : s.getKeys(false)) {
                m.put(k, Double.valueOf(s.getDouble(k)));
            }
        }
        return m;
    }

    private void infosSilo(Player p) {
        final String prix = gson.toJson(prixCereales());
        final ConfigurationSection mienne = entrepriseDe(p.getName(), "farm");
        reseau.envoyer(p, "SiloInfosMachinePacket", new ReseauNG.Ecrivain() {
            public void ecrire(DataOutputStream out) throws Exception {
                out.writeUTF(prix);
                out.writeBoolean(mienne != null || SSNG.admin(p));
                out.writeUTF(mienne == null ? "" : mienne.getString("drapeau", ""));
                out.writeBoolean(true);
            }
        });
    }

    // ------------------------------------------------------------------ machines : transactions

    private void acheterPetrole(Player p, String nomEnt, int litres, int x, int y, int z) {
        ConfigurationSection e = ent(nomEnt);
        Object te = tuile(p.getWorld(), x, y, z);
        if (e == null || te == null || litres <= 0 || p.getLocation().distance(new org.bukkit.Location(p.getWorld(), x, y, z)) > 12) {
            return;
        }
        float stocke = getF(te, "getFuelStored");
        float max = getF(te, "getMaxFuelStored");
        litres = (int) Math.min(litres, Math.max(0, max - stocke));
        if (litres <= 0) {
            SSNG.msg(p, "&cLa citerne est pleine.");
            return;
        }
        if (reserve(e) < litres) {
            SSNG.msg(p, "&c" + e.getString("nom") + " n'a plus que " + (long) e.getDouble("reserve") + " L en reserve.");
            return;
        }
        double cout = Math.max(arrondi(e.getInt("prix") / 100.0 * litres), litres > 0 ? 0.01 : 0);
        if (!payer(p, cout)) {
            return;
        }
        ajouterReserve(e, -litres);
        e.set("chiffre", Double.valueOf(e.getDouble("chiffre") + cout));
        e.set("vendu", Double.valueOf(e.getDouble("vendu") + litres));
        crediter(e.getString("chef"), cout);
        sauver();
        setF(te, "storedFuel", stocke + litres);
        majBloc(p, x, y, z);
        SSNG.msg(p, "&aAchat : " + litres + " L de carburant chez " + e.getString("nom") + " pour " + format(cout) + " $.");
    }

    private void vendrePetrole(Player p, int litres, int x, int y, int z) {
        ConfigurationSection e = entrepriseDe(p.getName(), "petrol");
        Object te = tuile(p.getWorld(), x, y, z);
        if (e == null || te == null || litres <= 0) {
            SSNG.msg(p, "&cIl faut faire partie d'une entreprise de petrole pour vendre.");
            return;
        }
        float stocke = getF(te, "getFuelStored");
        litres = (int) Math.min(litres, stocke);
        if (litres <= 0) {
            return;
        }
        setF(te, "storedFuel", stocke - litres);
        majBloc(p, x, y, z);
        ajouterReserve(e, litres);
        e.set("total", Double.valueOf(e.getDouble("total") + litres));
        sauver();
        SSNG.msg(p, "&a" + litres + " L ajoutes a la reserve de " + e.getString("nom") + ".");
    }

    /** Achat d'electricite : la quantite saisie est en milliers d'UE. */
    private void acheterElec(Player p, String nomEnt, int kilo, int x, int y, int z) {
        ConfigurationSection e = ent(nomEnt);
        Object te = tuile(p.getWorld(), x, y, z);
        if (e == null || te == null || kilo <= 0 || p.getLocation().distance(new org.bukkit.Location(p.getWorld(), x, y, z)) > 12) {
            return;
        }
        float stocke = getF(te, "getEnergyStored");
        float max = getF(te, "getMaxEnergyStored");
        double ue = Math.min(kilo * 1000.0, Math.max(0, max - stocke));
        if (ue <= 0) {
            SSNG.msg(p, "&cLa machine est deja pleine.");
            return;
        }
        if (reserve(e) < ue) {
            SSNG.msg(p, "&c" + e.getString("nom") + " n'a plus que " + (long) e.getDouble("reserve") + " UE en reserve.");
            return;
        }
        double cout = Math.max(1.0, arrondi(e.getInt("prix") / 1000.0 * (ue / 1000.0)));
        if (!payer(p, cout)) {
            return;
        }
        ajouterReserve(e, -ue);
        e.set("chiffre", Double.valueOf(e.getDouble("chiffre") + cout));
        e.set("vendu", Double.valueOf(e.getDouble("vendu") + ue));
        crediter(e.getString("chef"), cout);
        sauver();
        energie(te, (float) (stocke + ue));
        majBloc(p, x, y, z);
        SSNG.msg(p, "&aAchat : " + (long) ue + " UE chez " + e.getString("nom") + " pour " + format(cout) + " $.");
    }

    private void vendreElec(Player p, int kilo, int x, int y, int z) {
        ConfigurationSection e = entrepriseDe(p.getName(), "electric");
        Object te = tuile(p.getWorld(), x, y, z);
        if (e == null || te == null || kilo <= 0) {
            SSNG.msg(p, "&cIl faut faire partie d'une entreprise d'electricite pour vendre.");
            return;
        }
        float stocke = getF(te, "getEnergyStored");
        double ue = Math.min(kilo * 1000.0, stocke);
        if (ue <= 0) {
            return;
        }
        energie(te, (float) (stocke - ue));
        majBloc(p, x, y, z);
        ajouterReserve(e, ue);
        e.set("total", Double.valueOf(e.getDouble("total") + ue));
        sauver();
        SSNG.msg(p, "&a" + (long) ue + " UE ajoutes a la reserve de " + e.getString("nom") + ".");
    }

    private void acheterCereale(Player p, String cereale, int qte, int x, int y, int z) throws Exception {
        Object te = tuile(p.getWorld(), x, y, z);
        Double prix = prixCereales().get(cereale);
        int[] item = cereales.get(cereale);
        if (te == null || prix == null || item == null || qte <= 0) {
            return;
        }
        Class<?> c = te.getClass();
        int q = c.getField("quantity").getInt(te);
        String type = (String) c.getField("cerealType").get(te);
        int max = ((Number) c.getMethod("maxQuantity").invoke(te)).intValue();
        if (type != null && !type.isEmpty() && !type.equals(cereale)) {
            SSNG.msg(p, "&cLe silo contient deja une autre cereale.");
            return;
        }
        qte = Math.min(qte, max - q);
        if (qte <= 0) {
            SSNG.msg(p, "&cLe silo est plein.");
            return;
        }
        // stock : l'entreprise agricole qui en a le plus, sinon le marche (si stock illimite)
        ConfigurationSection source = null;
        double meilleur = 0;
        for (ConfigurationSection e : deType("farm")) {
            double r = e.getBoolean("infinie") ? Double.MAX_VALUE : e.getDouble("reserves-cereales." + cereale);
            if (r >= qte && r > meilleur) {
                meilleur = r;
                source = e;
            }
        }
        if (source == null && !conf.getBoolean("marche.stock-illimite", true)) {
            SSNG.msg(p, "&cAucune entreprise agricole n'a " + qte + " de " + nomsCereales.get(cereale) + " en stock.");
            return;
        }
        double cout = arrondi(prix.doubleValue() / 1000.0 * qte);
        if (!payer(p, cout)) {
            return;
        }
        if (source != null) {
            if (!source.getBoolean("infinie")) {
                source.set("reserves-cereales." + cereale, Double.valueOf(source.getDouble("reserves-cereales." + cereale) - qte));
            }
            source.set("chiffre", Double.valueOf(source.getDouble("chiffre") + cout));
            crediter(source.getString("chef"), cout);
            sauver();
        }
        if (type == null || type.isEmpty()) {
            Object okType = c.getMethod("setCerealType", int.class).invoke(te, Integer.valueOf(item[0]));
            if (!Boolean.TRUE.equals(okType) || !cereale.equals(c.getField("cerealType").get(te))) {
                c.getField("cerealType").set(te, cereale);
            }
        }
        c.getMethod("addQuantity", int.class).invoke(te, Integer.valueOf(qte));
        forcer(te, p, x, y, z);
        SSNG.msg(p, "&aAchat : " + qte + " " + nomsCereales.get(cereale) + " pour " + format(cout) + " $"
            + (source != null ? " (" + source.getString("nom") + ")" : "") + ".");
    }

    private void vendreCereale(Player p, String cereale, int qte, int x, int y, int z) throws Exception {
        Object te = tuile(p.getWorld(), x, y, z);
        ConfigurationSection e = entrepriseDe(p.getName(), "farm");
        Double prix = prixCereales().get(cereale);
        if (te == null || prix == null || qte <= 0) {
            return;
        }
        if (e == null && !SSNG.admin(p)) {
            SSNG.msg(p, "&cIl faut faire partie d'une entreprise agricole pour vendre au marche.");
            return;
        }
        Class<?> c = te.getClass();
        if (!cereale.equals(c.getField("cerealType").get(te))) {
            return;
        }
        int retire = ((Number) c.getMethod("withdrawQuantity", int.class).invoke(te, Integer.valueOf(qte))).intValue();
        if (retire <= 0) {
            return;
        }
        double brut = arrondi(Math.max(0, prix.doubleValue() - conf.getInt("marche.marge-vente", 100)) / 1000.0 * retire);
        double net = arrondi(brut * (1 - conf.getDouble("marche.taxe-vente", 0.2)));
        crediter(p.getName(), net);
        if (e != null) {
            e.set("reserves-cereales." + cereale, Double.valueOf(e.getDouble("reserves-cereales." + cereale) + retire));
            e.set("total", Double.valueOf(e.getDouble("total") + retire));
            e.set("aujourdhui", Double.valueOf(e.getDouble("aujourdhui") + retire));
            e.set("chiffre", Double.valueOf(e.getDouble("chiffre") + net));
            sauver();
        }
        forcer(te, p, x, y, z);
        SSNG.msg(p, "&aVente : " + retire + " " + nomsCereales.get(cereale) + " pour " + format(net) + " $ (taxe deduite).");
    }

    private void recupererCereales(Player p, int x, int y, int z) throws Exception {
        Object te = tuile(p.getWorld(), x, y, z);
        if (te == null || p.getLocation().distance(new org.bukkit.Location(p.getWorld(), x, y, z)) > 12) {
            return;
        }
        Class<?> c = te.getClass();
        String type = (String) c.getField("cerealType").get(te);
        int q = c.getField("quantity").getInt(te);
        int[] item = cereales.get(type);
        if (item == null) {
            int id = ((Number) c.getMethod("getItemId").invoke(te)).intValue();
            item = id > 0 ? new int[] {id, 0} : null;
        }
        if (item == null || q <= 0) {
            SSNG.msg(p, "&7Le silo est vide.");
            return;
        }
        int place = 0;
        int max = Ateliers.pileMax(item[0], item[1]);
        for (ItemStack s : p.getInventory().getContents()) {
            if (s == null || s.getTypeId() == 0) {
                place += max;
            } else if (s.getTypeId() == item[0] && s.getDurability() == item[1]) {
                place += Math.max(0, max - s.getAmount());
            }
        }
        int n = Math.min(q, place);
        if (n <= 0) {
            SSNG.msg(p, "&cInventaire plein.");
            return;
        }
        int retire = ((Number) c.getMethod("withdrawQuantity", int.class).invoke(te, Integer.valueOf(n))).intValue();
        while (retire > 0) {
            int t = Math.min(retire, max);
            retire -= t;
            p.getInventory().addItem(Ateliers.objet(item[0], item[1], t, null));
        }
        p.updateInventory();
        forcer(te, p, x, y, z);
    }

    private static void forcer(Object te, Player p, int x, int y, int z) {
        try {
            te.getClass().getMethod("forcerEnvoi").invoke(te);
        } catch (Exception e) {
            // ancienne version
        }
        majBloc(p, x, y, z);
    }

    // ------------------------------------------------------------------ interface des entreprises

    private Map<String, String> resume(ConfigurationSection e) {
        Map<String, String> m = new LinkedHashMap<String, String>();
        List<String> tous = new ArrayList<String>();
        tous.add(e.getString("chef", ""));
        tous.addAll(e.getStringList("membres"));
        int enLigne = 0;
        for (String j : tous) {
            if (Bukkit.getPlayerExact(j) != null) {
                enLigne++;
            }
        }
        m.put("name", e.getString("nom"));
        m.put("type", e.getString("type"));
        m.put("owner", e.getString("chef", ""));
        m.put("members", join(tous, ","));
        m.put("description", e.getString("description", ""));
        m.put("online_players", String.valueOf(enLigne));
        m.put("max_players", String.valueOf(conf.getInt("reglages.membres-max", 20)));
        m.put("open", String.valueOf(e.getBoolean("ouverte", true)));
        m.put("satisfaction", "100%");
        m.put("turnover", String.valueOf((int) Math.min(Integer.MAX_VALUE, e.getDouble("chiffre"))));
        m.put("creation_time", String.valueOf(e.getLong("creation", System.currentTimeMillis())));
        return m;
    }

    void envoyerListe(Player p) {
        List<Map<String, String>> l = new ArrayList<Map<String, String>>();
        for (ConfigurationSection e : toutes()) {
            l.add(resume(e));
        }
        final String json = gson.toJson(l);
        final String types = gson.toJson(conf.getStringList("reglages.types-autorises"));
        reseau.envoyer(p, "EnterpriseListDataPacket", new ReseauNG.Ecrivain() {
            public void ecrire(DataOutputStream out) throws Exception {
                out.writeUTF(json);
                out.writeUTF(types);
            }
        });
    }

    void envoyerFiche(Player p, String nom) {
        ConfigurationSection e = ent(nom);
        if (e == null) {
            return;
        }
        boolean dedans = membre(e, p.getName());
        boolean chef = p.getName().equalsIgnoreCase(e.getString("chef", ""));
        List<String> enLigne = new ArrayList<String>();
        List<String> horsLigne = new ArrayList<String>();
        List<String> tous = new ArrayList<String>();
        tous.add(e.getString("chef", ""));
        tous.addAll(e.getStringList("membres"));
        for (int i = 0; i < tous.size(); i++) {
            String j = tous.get(i);
            if (j.isEmpty()) {
                continue;
            }
            String ligne = (i == 0 ? "leader" : "member") + "#" + j;
            if (Bukkit.getPlayerExact(j) != null) {
                enLigne.add(ligne);
            } else {
                horsLigne.add(ligne);
            }
        }
        final Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("name", e.getString("nom"));
        m.put("type", e.getString("type"));
        m.put("flagImage", e.getString("drapeau", ""));
        m.put("isOpen", Boolean.valueOf(e.getBoolean("ouverte", true)));
        m.put("toxicityTax", "0");
        m.put("playerRole", chef ? "leader" : dedans ? "member" : "");
        m.put("isInEnterprise", Boolean.valueOf(dedans));
        m.put("isPlayerOp", Boolean.FALSE);
        m.put("position", "");
        m.put("age", Long.valueOf((System.currentTimeMillis() - e.getLong("creation", System.currentTimeMillis())) / 86400000L));
        m.put("description", e.getString("description", ""));
        m.put("turnover", format(e.getDouble("chiffre")) + " $");
        m.put("contractsDone", "0");
        m.put("contractsSuccess", "0");
        m.put("disputes", "0");
        m.put("players_online", enLigne);
        m.put("players_offline", horsLigne);
        m.put("services", e.getString("services", ""));
        m.put("permissions", new ArrayList<String>());
        m.put("allTypes", conf.getStringList("reglages.types-autorises"));
        m.put("canRequestContract", Boolean.FALSE);
        m.put("enterpriseInAbsence", Boolean.FALSE);
        m.put("playersAbsenceData", new HashMap<String, Object>());
        final String json = gson.toJson(m);
        reseau.envoyer(p, "EnterpriseMainDataPacket", new ReseauNG.Ecrivain() {
            public void ecrire(DataOutputStream out) throws Exception {
                out.writeUTF(json);
            }
        });
    }

    private void envoyerOngletEnergie(Player p, String paquet, String nom) {
        ConfigurationSection e = ent(nom);
        if (e == null) {
            return;
        }
        final Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("name", e.getString("nom"));
        m.put("price", Double.valueOf(e.getInt("prix")));
        m.put("avgPrice", Double.valueOf(moyennePrix(e.getString("type"))));
        m.put("available", Double.valueOf(e.getBoolean("infinie") ? 1.0E12 : e.getDouble("reserve")));
        m.put("total", Double.valueOf(e.getDouble("total") + e.getDouble("vendu")));
        m.put("countries", new ArrayList<String>());
        m.put("allowAll", Boolean.valueOf(e.getBoolean("autoriser-tous", true)));
        m.put("allowCountry", Boolean.valueOf(e.getBoolean("autoriser-pays", true)));
        m.put("allowAlly", Boolean.valueOf(e.getBoolean("autoriser-allies", true)));
        m.put("associatedCountry", "");
        final int base = e.getInt("prix");
        reseau.envoyer(p, paquet, new ReseauNG.Ecrivain() {
            public void ecrire(DataOutputStream out) throws Exception {
                out.writeUTF(gson.toJson(m));
                out.writeUTF("{}");
                out.writeUTF("{}");
                out.writeInt(base);
            }
        });
    }

    private double moyennePrix(String type) {
        double s = 0;
        int n = 0;
        for (ConfigurationSection e : deType(type)) {
            s += e.getInt("prix");
            n++;
        }
        return n == 0 ? 0 : s / n;
    }

    private static boolean nomValide(String nom) {
        return nom != null && nom.matches("[A-Za-z0-9_-]{3,16}");
    }

    private String creationJoueur(Player p, String nom, String type) {
        if (!conf.getBoolean("reglages.creation-par-les-joueurs", true) && !SSNG.admin(p)) {
            return "La creation d'entreprise est reservee aux admins.";
        }
        if (!nomValide(nom)) {
            return "Nom invalide (3 a 16 lettres, chiffres, _ ou -).";
        }
        if (ent(nom) != null) {
            return "Ce nom est deja pris.";
        }
        if (!conf.getStringList("reglages.types-autorises").contains(type)) {
            return "Type d'entreprise non autorise.";
        }
        if (entrepriseDe(p.getName(), type) != null) {
            return "Tu fais deja partie d'une entreprise de ce type.";
        }
        double prix = SSNG.admin(p) ? 0 : conf.getDouble("reglages.prix-creation", 15000);
        if (!payer(p, prix)) {
            return "Pas assez d'argent (" + format(prix) + " $).";
        }
        creer(nom, type, p.getName(), true);
        SSNG.msg(p, "&aEntreprise " + nom + " creee (" + type + "). &7Les admins peuvent definir ses reserves avec /entreprise.");
        plugin.evenements().publier("entreprise", p.getName() + " a cree l'entreprise " + nom + " (" + type + ").", null);
        return "";
    }

    private static boolean retirerMembre(ConfigurationSection e, String joueur) {
        List<String> m = new ArrayList<String>(e.getStringList("membres"));
        boolean ok = false;
        for (int i = m.size() - 1; i >= 0; i--) {
            if (m.get(i).equalsIgnoreCase(joueur)) {
                m.remove(i);
                ok = true;
            }
        }
        e.set("membres", m);
        return ok;
    }

    private static String join(List<String> l, String sep) {
        StringBuilder b = new StringBuilder();
        for (String s : l) {
            if (s == null || s.isEmpty()) {
                continue;
            }
            if (b.length() > 0) {
                b.append(sep);
            }
            b.append(s);
        }
        return b.toString();
    }

    // ------------------------------------------------------------------ commande

    private void aide(CommandSender s, boolean admin) {
        SSNG.msg(s, "&6/entreprise &7- liste des entreprises (interface NG)");
        SSNG.msg(s, "&6/entreprise voir <nom> &7- fiche / &6/entreprise rejoindre|quitter <nom>");
        if (admin) {
            SSNG.msg(s, "&e-- admins --");
            SSNG.msg(s, "&6/entreprise creer <nom> <petrol|electric|farm|trader|...> [chef]");
            SSNG.msg(s, "&6/entreprise supprimer <nom>");
            SSNG.msg(s, "&6/entreprise chef <nom> <joueur> &7| &6membre <nom> ajouter|retirer <joueur>");
            SSNG.msg(s, "&6/entreprise reserve <nom> <quantite|infinie> &7(petrole : L, electricite : UE)");
            SSNG.msg(s, "&6/entreprise reserve <nom> <cereale> <quantite> &7(agriculture)");
            SSNG.msg(s, "&6/entreprise prix <nom> <prix> &7(petrole : $/100 L, electricite : $/1 000 000 UE)");
            SSNG.msg(s, "&6/entreprise ouverte <nom> oui|non &7| &6description <nom> <texte...>");
            SSNG.msg(s, "&6/entreprise info <nom>");
            SSNG.msg(s, "&6/entreprise marche &7- prix des cereales | &6marche <cereale> <prix $/T>");
            SSNG.msg(s, "&6/entreprise marche taxe <0-1> | marge <$> | stock-illimite oui|non");
            SSNG.msg(s, "&6/entreprise reglage paiement|creation-joueurs oui|non | prix-creation <$> | types <t1,t2..>");
            SSNG.msg(s, "&6/entreprise recharger");
        }
    }

    private static boolean oui(String s) {
        return s.equalsIgnoreCase("oui") || s.equalsIgnoreCase("on") || s.equalsIgnoreCase("true") || s.equalsIgnoreCase("1");
    }

    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        boolean admin = SSNG.admin(s);
        if (a.length == 0) {
            if (s instanceof Player) {
                reseau.envoyer((Player) s, "RemoteOpenEnterpriseListPacket", null);
            } else {
                for (ConfigurationSection e : toutes()) {
                    s.sendMessage(e.getString("nom") + " (" + e.getString("type") + ") chef=" + e.getString("chef"));
                }
            }
            return true;
        }
        String sc = a[0].toLowerCase();
        try {
            if (sc.equals("aide") || sc.equals("help")) {
                aide(s, admin);
                return true;
            }
            if (sc.equals("voir") && a.length > 1 && s instanceof Player) {
                ConfigurationSection e = ent(a[1]);
                if (e == null) {
                    SSNG.msg(s, "&cEntreprise inconnue.");
                    return true;
                }
                final String nom = e.getString("nom");
                reseau.envoyer((Player) s, "RemoteOpenEnterpriseMainPacket", new ReseauNG.Ecrivain() {
                    public void ecrire(DataOutputStream out) throws Exception {
                        out.writeUTF(nom);
                    }
                });
                return true;
            }
            if (sc.equals("rejoindre") && a.length > 1 && s instanceof Player) {
                ConfigurationSection e = ent(a[1]);
                if (e == null || (!e.getBoolean("ouverte", true) && !admin)) {
                    SSNG.msg(s, "&cEntreprise inconnue ou fermee.");
                    return true;
                }
                if (entrepriseDe(s.getName(), e.getString("type")) != null) {
                    SSNG.msg(s, "&cTu fais deja partie d'une entreprise de ce type.");
                    return true;
                }
                List<String> m = new ArrayList<String>(e.getStringList("membres"));
                m.add(s.getName());
                e.set("membres", m);
                sauver();
                SSNG.msg(s, "&aTu as rejoint " + e.getString("nom") + ".");
                return true;
            }
            if (sc.equals("quitter") && a.length > 1) {
                ConfigurationSection e = ent(a[1]);
                if (e != null && retirerMembre(e, s.getName())) {
                    sauver();
                    SSNG.msg(s, "&aTu as quitte " + e.getString("nom") + ".");
                } else {
                    SSNG.msg(s, "&cTu n'es pas membre (le chef doit transmettre l'entreprise ou la supprimer).");
                }
                return true;
            }
            if (!admin) {
                aide(s, false);
                return true;
            }
            if (sc.equals("autotest")) {
                autotest(s);
                return true;
            }
            if (sc.equals("recharger")) {
                charger();
                SSNG.msg(s, "&aentreprises.yml recharge.");
                return true;
            }
            if (sc.equals("marche")) {
                return marche(s, a);
            }
            if (sc.equals("reglage") && a.length > 2) {
                String k = a[1].toLowerCase();
                if (k.equals("paiement")) {
                    conf.set("reglages.paiement", Boolean.valueOf(oui(a[2])));
                } else if (k.startsWith("creation")) {
                    conf.set("reglages.creation-par-les-joueurs", Boolean.valueOf(oui(a[2])));
                } else if (k.equals("prix-creation")) {
                    conf.set("reglages.prix-creation", Integer.valueOf(Integer.parseInt(a[2])));
                } else if (k.equals("types")) {
                    conf.set("reglages.types-autorises", java.util.Arrays.asList(a[2].toLowerCase().split(",")));
                } else if (k.equals("membres-max")) {
                    conf.set("reglages.membres-max", Integer.valueOf(Integer.parseInt(a[2])));
                } else {
                    aide(s, true);
                    return true;
                }
                sauver();
                SSNG.msg(s, "&aReglage " + k + " = " + a[2]);
                return true;
            }
            if (sc.equals("creer") && a.length > 2) {
                String type = a[2].toLowerCase();
                if (!java.util.Arrays.asList(TYPES).contains(type)) {
                    SSNG.msg(s, "&cType inconnu. Types : " + java.util.Arrays.toString(TYPES));
                    return true;
                }
                if (!nomValide(a[1]) || ent(a[1]) != null) {
                    SSNG.msg(s, "&cNom invalide ou deja pris (3 a 16 caracteres).");
                    return true;
                }
                creer(a[1], type, a.length > 3 ? a[3] : s.getName(), true);
                SSNG.msg(s, "&aEntreprise " + a[1] + " creee (" + type + ").");
                return true;
            }
            if (a.length < 2) {
                aide(s, true);
                return true;
            }
            ConfigurationSection e = ent(a[1]);
            if (e == null) {
                SSNG.msg(s, "&cEntreprise inconnue : " + a[1]);
                return true;
            }
            if (sc.equals("info")) {
                SSNG.msg(s, "&e" + e.getString("nom") + " &7(" + e.getString("type") + ") chef &f" + e.getString("chef")
                    + " &7membres &f" + e.getStringList("membres"));
                SSNG.msg(s, "&7Reserve : &f" + (e.getBoolean("infinie") ? "infinie" : (long) e.getDouble("reserve"))
                    + " &7| prix &f" + e.getInt("prix") + " &7| chiffre &f" + format(e.getDouble("chiffre")) + " $ &7| ouverte &f" + e.getBoolean("ouverte", true));
                ConfigurationSection rc = e.getConfigurationSection("reserves-cereales");
                if (rc != null) {
                    SSNG.msg(s, "&7Cereales : &f" + rc.getValues(false));
                }
                return true;
            }
            if (sc.equals("supprimer")) {
                conf.set("entreprises." + e.getName(), null);
                sauver();
                SSNG.msg(s, "&aEntreprise supprimee.");
                return true;
            }
            if (a.length < 3) {
                aide(s, true);
                return true;
            }
            if (sc.equals("chef")) {
                retirerMembre(e, a[2]);
                String ancien = e.getString("chef", "");
                e.set("chef", a[2]);
                if (!ancien.isEmpty() && !ancien.equalsIgnoreCase(a[2])) {
                    List<String> m = new ArrayList<String>(e.getStringList("membres"));
                    m.add(ancien);
                    e.set("membres", m);
                }
            } else if (sc.equals("membre") && a.length > 3) {
                if (a[2].equalsIgnoreCase("ajouter")) {
                    if (!membre(e, a[3])) {
                        List<String> m = new ArrayList<String>(e.getStringList("membres"));
                        m.add(a[3]);
                        e.set("membres", m);
                    }
                } else {
                    retirerMembre(e, a[3]);
                }
            } else if (sc.equals("reserve")) {
                if (a.length > 3 && e.getString("type").equals("farm")) {
                    String cer = a[2].toLowerCase();
                    if (!cereales.containsKey(cer)) {
                        SSNG.msg(s, "&cCereale inconnue. Cereales : " + cereales.keySet());
                        return true;
                    }
                    e.set("reserves-cereales." + cer, Double.valueOf(Double.parseDouble(a[3])));
                } else if (a[2].equalsIgnoreCase("infinie")) {
                    e.set("infinie", Boolean.TRUE);
                } else {
                    e.set("infinie", Boolean.FALSE);
                    e.set("reserve", Double.valueOf(Double.parseDouble(a[2])));
                }
            } else if (sc.equals("prix")) {
                e.set("prix", Integer.valueOf(Integer.parseInt(a[2])));
            } else if (sc.equals("ouverte")) {
                e.set("ouverte", Boolean.valueOf(oui(a[2])));
            } else if (sc.equals("description")) {
                StringBuilder b = new StringBuilder();
                for (int i = 2; i < a.length; i++) {
                    b.append(i > 2 ? " " : "").append(a[i]);
                }
                e.set("description", b.toString());
            } else if (sc.equals("type")) {
                e.set("type", a[2].toLowerCase());
            } else {
                aide(s, true);
                return true;
            }
            sauver();
            SSNG.msg(s, "&aEntreprise " + e.getString("nom") + " mise a jour.");
        } catch (NumberFormatException ex) {
            SSNG.msg(s, "&cNombre invalide.");
        }
        return true;
    }

    /** Test sans client des operations sur les machines de la vitrine. */
    private void autotest(CommandSender s) {
        World w = plugin.monde(MachinesNG.TypeMonde.TEST);
        for (int cx = (Machines.VITRINE_X >> 4) - 1; cx <= ((Machines.VITRINE_X + Machines.ESPACE * Machines.COLONNES) >> 4) + 1; cx++) {
            for (int cz = (Machines.VITRINE_Z >> 4) - 1; cz <= ((Machines.VITRINE_Z + Machines.ESPACE * 12) >> 4) + 1; cz++) {
                w.loadChunk(cx, cz);
            }
        }
        SSNG.msg(s, "&eEconomie Essentials : " + eco() + " ; cereales : " + cereales.size() + " ; entreprises : " + toutes().size());
        for (Object te : Ateliers.tuiles(w)) {
            String n = te.getClass().getSimpleName();
            try {
                if (n.equals("GCFluidTankBlockEntity")) {
                    float avant = getF(te, "getFuelStored");
                    setF(te, "storedFuel", avant + 100);
                    SSNG.msg(s, "&aCiterne : carburant " + avant + " -> " + getF(te, "getFuelStored") + " / " + getF(te, "getMaxFuelStored"));
                    setF(te, "storedFuel", avant);
                } else if (n.equals("GCEdoraTileEntityPlasmaAccumulator") || n.equals("GCElectricGeneratorBlockEntity")) {
                    float avant = getF(te, "getEnergyStored");
                    energie(te, avant + 5000);
                    SSNG.msg(s, "&a" + n + " : energie " + avant + " -> " + getF(te, "getEnergyStored") + " / " + getF(te, "getMaxEnergyStored"));
                    energie(te, avant);
                } else if (n.equals("GCSiloBlockEntity")) {
                    Class<?> c = te.getClass();
                    String k = cereales.keySet().iterator().next();
                    String typeAvant = (String) c.getField("cerealType").get(te);
                    int qAvant = c.getField("quantity").getInt(te);
                    if (typeAvant == null || typeAvant.isEmpty()) {
                        c.getMethod("setCerealType", int.class).invoke(te, Integer.valueOf(cereales.get(k)[0]));
                    }
                    int ajout = ((Number) c.getMethod("addQuantity", int.class).invoke(te, Integer.valueOf(10))).intValue();
                    String typeApres = (String) c.getField("cerealType").get(te);
                    int retire = ((Number) c.getMethod("withdrawQuantity", int.class).invoke(te, Integer.valueOf(10))).intValue();
                    if (c.getField("quantity").getInt(te) == 0) {
                        c.getField("cerealType").set(te, typeAvant == null ? "" : typeAvant);
                    }
                    SSNG.msg(s, "&aSilo : type '" + typeAvant + "' -> '" + typeApres + "' (cle " + k + "), +" + ajout + " -" + retire
                        + ", quantite " + qAvant + " -> " + c.getField("quantity").getInt(te) + " / " + c.getMethod("maxQuantity").invoke(te));
                }
            } catch (Throwable t) {
                SSNG.msg(s, "&c" + n + " : " + t);
            }
        }
        SSNG.msg(s, "&7Offres petrole : " + offres("petrol") + " ; electricite : " + offres("electric"));
        SSNG.msg(s, "&7Prix cereales : " + gson.toJson(prixCereales()));
    }

    private boolean marche(CommandSender s, String[] a) {
        if (a.length == 1) {
            SSNG.msg(s, "&eMarche des cereales &7(prix d'achat en $/tonne ; vente = (prix - " + conf.getInt("marche.marge-vente", 100)
                + ") - taxe " + (int) (conf.getDouble("marche.taxe-vente", 0.2) * 100) + "%, stock illimite : " + conf.getBoolean("marche.stock-illimite", true) + ")");
            List<String> l = new ArrayList<String>(prixCereales().keySet());
            Collections.sort(l, new Comparator<String>() {
                public int compare(String x, String y) {
                    return x.compareTo(y);
                }
            });
            for (String k : l) {
                SSNG.msg(s, "&6" + k + " &7(" + nomsCereales.get(k) + ") : &f" + prixCereales().get(k).intValue() + " $/T");
            }
            return true;
        }
        if (a.length < 3) {
            aide(s, true);
            return true;
        }
        String k = a[1].toLowerCase();
        if (k.equals("taxe")) {
            conf.set("marche.taxe-vente", Double.valueOf(Double.parseDouble(a[2])));
        } else if (k.equals("marge")) {
            conf.set("marche.marge-vente", Integer.valueOf(Integer.parseInt(a[2])));
        } else if (k.equals("stock-illimite")) {
            conf.set("marche.stock-illimite", Boolean.valueOf(oui(a[2])));
        } else if (cereales.containsKey(k) || conf.isSet("marche.cereales." + k)) {
            conf.set("marche.cereales." + k, Integer.valueOf(Integer.parseInt(a[2])));
        } else {
            SSNG.msg(s, "&cCereale inconnue. Cereales : " + cereales.keySet());
            return true;
        }
        sauver();
        SSNG.msg(s, "&aMarche mis a jour : " + k + " = " + a[2]);
        return true;
    }
}
