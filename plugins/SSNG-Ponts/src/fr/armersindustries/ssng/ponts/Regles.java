package fr.armersindustries.ssng.ponts;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Reponses donnees aux mods NationsGlory a la place des plugins prives du serveur officiel.
 *
 * Principe : sur un serveur prive, tout est permis (machines, pompes, marche, missiles...)
 * sauf ce qui est regle par monde dans config.yml (combat et missiles interdits au hub
 * et dans le monde Build par exemple). Chaque appel peut etre affiche dans la console
 * (journal: true) pour comprendre ce que les mods demandent.
 */
public final class Regles {

    private static PontsNG plugin;
    private static final Map<String, AtomicLong> COMPTEURS = new ConcurrentHashMap<String, AtomicLong>();

    private Regles() {
    }

    static void init(PontsNG p) {
        plugin = p;
    }

    public static void pontActif(String nom, int methodes) {
        if (plugin != null) {
            plugin.getLogger().info("Pont " + nom + " actif (" + methodes + " fonctions simulees).");
        }
    }

    public static Map<String, AtomicLong> compteurs() {
        return COMPTEURS;
    }

    /** Point d'entree unique de tous les ponts. */
    public static Object appel(String pont, String methode, String genre, Object[] args) {
        String cle = pont + "." + methode;
        AtomicLong c = COMPTEURS.get(cle);
        if (c == null) {
            COMPTEURS.putIfAbsent(cle, new AtomicLong());
            c = COMPTEURS.get(cle);
        }
        long n = c.incrementAndGet();
        Object r;
        try {
            r = decider(pont, methode, genre, args);
        } catch (Throwable t) {
            if (plugin != null) {
                plugin.getLogger().log(Level.WARNING, "Erreur dans la regle " + cle, t);
            }
            r = parDefaut(genre, methode);
        }
        if (plugin != null && plugin.journal() && (n <= plugin.journalMax() || plugin.journalTout())) {
            plugin.getLogger().info("[appel] " + cle + texte(args) + " -> " + r);
        }
        return r;
    }

    // ------------------------------------------------------------------ regles

    private static Object decider(String pont, String m, String genre, Object[] a) {
        // 1) valeur forcee dans config.yml (reponses.<Pont>.<methode>)
        if (plugin != null) {
            Object force = plugin.reponseForcee(pont, m);
            if (force != null) {
                return convertir(force, genre);
            }
        }

        // 1b) colis d'assaut (SSNG-Assaut) : sans "assaut" a leur position, le mod NG les supprime au bout de 30 s
        if (m.equals("getAssaultAtPosition") && a.length >= 2) {
            Object r = extension("colisValide", new Class<?>[] {int.class, int.class},
                    ((Number) a[0]).intValue(), ((Number) a[1]).intValue());
            return Boolean.TRUE.equals(r) ? "assaut-ssng" : null;
        }

        // 1c) gisements (SSNG-Ressources) : coefficient de petrole / gaz a cet endroit
        if ((m.equals("withdrawPetrolInCoords") || m.equals("withdrawGasInCoords")) && a.length >= 4) {
            Object r = extension("gisement", new Class<?>[] {String.class, String.class, int.class, int.class},
                    m.equals("withdrawPetrolInCoords") ? "petrole" : "gaz", str(a, 0),
                    ((Number) a[1]).intValue(), ((Number) a[3]).intValue());
            if (r instanceof Number) {
                return Float.valueOf(((Number) r).floatValue());
            }
        }

        // 2) missiles : autorises seulement dans les mondes listes
        if (m.equals("missileCanLaunchAt") || m.equals("missileCanExplodeAt")) {
            boolean ok = mondeAutorise(str(a, 0), "mondes-missiles");
            if (ok) {
                informerMissile(m.equals("missileCanLaunchAt"), a);
            }
            return Boolean.valueOf(ok);
        }
        if (m.equals("isMissileAuthorized")
                || m.equals("missileIsAuthorizedArmorAbility")) {
            return Boolean.valueOf(mondeAutorise(str(a, 0), "mondes-missiles"));
        }
        if (m.equals("turretCanDestroyMissile")) {
            return Boolean.TRUE;
        }
        if (m.equals("getRemainingMissilePointsForPlayer")) {
            return Integer.valueOf(plugin == null ? 9999 : plugin.getConfig().getInt("points-missile", 9999));
        }

        // 3) combat entre joueurs (armes NG, coups speciaux) : selon le monde
        if (m.equals("canPlayerShootOnPlayer") || m.equals("canPlayerCombatPlayer") || m.equals("userCanShoot")
                || m.equals("playerCanShootLaseSpartan") || m.equals("canPlayerUseSpecialAttack")) {
            return Boolean.valueOf(mondeAutorise(str(a, 0), "mondes-combat"));
        }
        if (m.equals("arePlayersFriendly") || m.equals("arePlayersFriendlyByName")) {
            return Boolean.valueOf(!mondeAutorise(str(a, 0), "mondes-combat"));
        }

        // 4) ressources des machines
        if (m.equals("withdrawPetrolInCoords")) {
            return Float.valueOf((float) cfgDouble("ressources.petrole", 1.0));
        }
        if (m.equals("withdrawGasInCoords")) {
            return Float.valueOf((float) cfgDouble("ressources.gaz", 1.0));
        }
        if (m.equals("withdrawEnergy")) {
            // energie stockee par le pays : on fournit ce qui est demande
            return Float.valueOf(a.length > 1 && a[1] instanceof Number ? ((Number) a[1]).floatValue() : 1f);
        }
        if (m.equals("isInExtractionZone")) {
            return Boolean.TRUE;
        }
        if (m.equals("isMachineOnMagneticArea")) {
            // date de la derniere tempete magnetique (IEM) dans la zone : 0 = jamais
            return Long.valueOf(0L);
        }
        if (m.equals("getCountryResearchLevel")) {
            // recherche militaire >= 13 : precision maximale des lanceurs de missiles
            if (a.length > 1 && "military".equalsIgnoreCase(str(a, 1))) {
                return Integer.valueOf(plugin == null ? 15 : plugin.getConfig().getInt("niveau-recherche-militaire", 15));
            }
            return Integer.valueOf(plugin == null ? 10 : plugin.getConfig().getInt("niveau-recherche", 10));
        }
        if (m.equals("getPlayerSkillLevel")) {
            // metiers : ingenieur >= 3 = tirs plus precis, >= 4 = degats des missiles renforces (comme sur NG)
            String metier = str(a, 1);
            int defaut = "engineer".equalsIgnoreCase(metier) ? 5 : 0;
            return Integer.valueOf(plugin == null ? defaut : plugin.getConfig().getInt("niveaux-metiers." + metier, defaut));
        }

        // 5) economie : argent illimite, tout est gratuit
        if (m.equals("getMoney")) {
            return Double.valueOf(cfgDouble("argent", 1000000000.0));
        }
        if (m.equals("hasMoney") || m.equals("withdrawMoney")) {
            return Boolean.TRUE;
        }
        if (m.equals("payNGCoins")) {
            if (a.length > 3 && a[3] instanceof Runnable) {
                ((Runnable) a[3]).run();
            }
            return Boolean.TRUE;
        }
        if (m.equals("ngCoinsPriceFor") || m.equals("applyToxicityTax")) {
            return m.equals("applyToxicityTax") ? Integer.valueOf(0) : Integer.valueOf(0);
        }
        if (m.equals("calculateSurcharge") || m.equals("getToxicityTax")) {
            return Double.valueOf(0);
        }
        if (m.equals("getExpToGive")) {
            return a.length > 1 && a[1] instanceof Number ? Integer.valueOf(((Number) a[1]).intValue()) : Integer.valueOf(0);
        }

        // 6) pays / Factions : pas de pays, tout le monde est en zone libre
        if (m.equals("isWildernessAt")) {
            return Boolean.TRUE;
        }
        if (m.equals("isCountryClaimAt") || m.equals("hasNonGlobalWorldguardRegionAt") || m.equals("hasEmbargo")
                || m.equals("isPlayerInCombat") || m.equals("isPlayerInAssault") || m.equals("isEntryDenied")
                || m.equals("isExitDenied") || m.equals("hasPlayersInNoelFactoryRegion") || m.equals("canPlayerContractContagious")
                || m.equals("isPlayerImmuniseFromMissile") || m.equals("canPlayerHaveDoubleLoot") || m.equals("playerHasSkinSync")
                || m.equals("isPlayerGod")) {
            return Boolean.FALSE;
        }
        if (m.equals("getFactionFromPlayer") || m.equals("getFactionNameFromId")) {
            return "Wilderness";
        }
        if (m.equals("getFactionIdAtCoordonates")) {
            return "0";
        }
        if (m.equals("checkInCombatOrAssault") || m.equals("checkCanOpenMarket") || m.equals("checkCanOpenMarketByType")) {
            return null; // null = rien a signaler
        }
        if (m.equals("getTranslationLang")) {
            return "fr";
        }
        if (m.equals("getServerType")) {
            return genre.equals("Integer") ? Integer.valueOf(0) : plugin == null ? "blue" : plugin.getConfig().getString("type-serveur", "blue");
        }
        if (m.equals("getPlayerRenderDistance")) {
            return Integer.valueOf(10);
        }
        if (m.equals("getSkillNumericEffect")) {
            return a.length > 2 ? a[2] : null;
        }

        // 7) commandes demandees par les mods : on les execute vraiment
        if (m.equals("dispatchConsoleCommand") && a.length > 0 && a[0] != null) {
            final String cmd = String.valueOf(a[0]);
            Bukkit.getScheduler().runTask(plugin, new Runnable() {
                public void run() {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                }
            });
            return null;
        }
        if (m.equals("executePlayerCmd") && a.length > 1) {
            final Player p = Bukkit.getPlayerExact(str(a, 0));
            final String cmd = str(a, 1);
            if (p != null && cmd != null) {
                Bukkit.getScheduler().runTask(plugin, new Runnable() {
                    public void run() {
                        p.performCommand(cmd.startsWith("/") ? cmd.substring(1) : cmd);
                    }
                });
            }
            return null;
        }

        // 8) par defaut selon le type attendu (permissions "can..." = oui)
        return parDefaut(genre, m);
    }

    static Object parDefaut(String genre, String m) {
        if (genre.equals("Boolean")) {
            String x = m.toLowerCase();
            boolean oui = x.startsWith("can") || x.startsWith("check") || x.startsWith("has") || x.startsWith("player")
                    || x.contains("authoriz") || x.startsWith("save") || x.startsWith("remove") || x.startsWith("decide")
                    || x.startsWith("execute") || x.startsWith("deal") || x.startsWith("pay") || x.startsWith("withdraw");
            return Boolean.valueOf(oui);
        }
        if (genre.equals("Integer")) {
            return Integer.valueOf(0);
        }
        if (genre.equals("Double")) {
            return Double.valueOf(0);
        }
        if (genre.equals("Float")) {
            return Float.valueOf(1f);
        }
        if (genre.equals("Long")) {
            return Long.valueOf(0L);
        }
        if (genre.equals("String")) {
            return "";
        }
        if (genre.equals("Map")) {
            return new HashMap<Object, Object>();
        }
        if (genre.equals("List")) {
            return new ArrayList<Object>();
        }
        if (genre.equals("Json")) {
            return new com.google.gson.JsonArray();
        }
        return null;
    }

    // ------------------------------------------------------------------ outils

    /** Le monde du joueur (ou le monde nomme) est-il dans la liste de config ? Liste vide = partout. */
    /** Plugins du pack qui peuvent repondre a la place des regles par defaut (par reflexion, sans dependance). */
    private static final String[] EXTENSIONS = {"SSNG-Assaut", "SSNG-Ressources", "SSNG-Carte", "ShadowMondes"};

    static Object extension(String methode, Class<?>[] types, Object... args) {
        for (String n : EXTENSIONS) {
            org.bukkit.plugin.Plugin p = Bukkit.getPluginManager().getPlugin(n);
            if (p == null || !p.isEnabled()) {
                continue;
            }
            try {
                Object r = p.getClass().getMethod(methode, types).invoke(p, args);
                if (r != null) {
                    return r;
                }
            } catch (NoSuchMethodException e) {
                // ce plugin ne gere pas cette question
            } catch (Throwable t) {
                if (plugin != null) {
                    plugin.getLogger().warning("Extension " + n + "." + methode + " : " + t);
                }
            }
        }
        return null;
    }

    private static boolean mondeAutorise(String joueurOuMonde, String liste) {
        if (plugin == null) {
            return true;
        }
        List<String> mondes = plugin.getConfig().getStringList(liste);
        if (mondes == null || mondes.isEmpty() || mondes.contains("*")) {
            return true;
        }
        World w = null;
        if (joueurOuMonde != null) {
            Player p = Bukkit.getPlayerExact(joueurOuMonde);
            w = p != null ? p.getWorld() : Bukkit.getWorld(joueurOuMonde);
        }
        if (w == null) {
            return plugin.getConfig().getBoolean("autoriser-si-monde-inconnu", false);
        }
        for (String s : mondes) {
            if (s.equalsIgnoreCase(w.getName())) {
                return true;
            }
        }
        return false;
    }

    private static double cfgDouble(String cle, double defaut) {
        return plugin == null ? defaut : plugin.getConfig().getDouble(cle, defaut);
    }

    /**
     * Messages au tireur : cible, distance et duree du vol au lancement, puis lieu de l'impact.
     * Arguments des ponts : (joueur, nomMissile, id, x, y, z, ...).
     */
    private static void informerMissile(boolean lancement, Object[] a) {
        if (plugin == null || a.length < 6 || !plugin.getConfig().getBoolean("messages-missiles", true)) {
            return;
        }
        final Player p = Bukkit.getPlayerExact(str(a, 0));
        if (p == null) {
            return;
        }
        String nom = str(a, 1);
        int x = entier(a[3]);
        int y = entier(a[4]);
        int z = entier(a[5]);
        String msg;
        if (lancement) {
            double dx = x - p.getLocation().getX();
            double dz = z - p.getLocation().getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            // ICBM : duree du vol = max(100, 2 x distance) ticks
            long secondes = Math.round(Math.max(100.0, 2.0 * dist) / 20.0);
            msg = "\u00a76[Missile] \u00a7e" + nom + " \u00a77lance vers \u00a7f" + x + " " + y + " " + z
                + " \u00a77(" + Math.round(dist) + " blocs, impact dans ~" + (secondes >= 60 ? secondes / 60 + " min " + secondes % 60 + " s" : secondes + " s") + ")";
        } else {
            msg = "\u00a7c[Missile] \u00a7e" + nom + " \u00a77a explose en \u00a7f" + x + " " + y + " " + z;
        }
        p.sendMessage(msg);
    }

    private static int entier(Object o) {
        if (o instanceof Number) {
            return ((Number) o).intValue();
        }
        try {
            return (int) Double.parseDouble(String.valueOf(o));
        } catch (Exception e) {
            return 0;
        }
    }

    private static String str(Object[] a, int i) {
        return a != null && a.length > i && a[i] != null ? String.valueOf(a[i]) : null;
    }

    private static Object convertir(Object v, String genre) {
        String s = String.valueOf(v);
        if (genre.equals("Boolean")) {
            return Boolean.valueOf(s);
        }
        if (genre.equals("Integer")) {
            return Integer.valueOf((int) Double.parseDouble(s));
        }
        if (genre.equals("Double")) {
            return Double.valueOf(s);
        }
        if (genre.equals("Float")) {
            return Float.valueOf(s);
        }
        if (genre.equals("Long")) {
            return s.equals("null") ? null : Long.valueOf(s);
        }
        if (genre.equals("String")) {
            return s.equals("null") ? null : s;
        }
        return s.equals("null") ? null : v;
    }

    private static String texte(Object[] a) {
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; a != null && i < a.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(a[i]);
        }
        return sb.append(')').toString();
    }
}
