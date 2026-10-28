package fr.armersindustries.ssng.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Pont reseau avec le mod NationsGUI (canal "nationsgui").
 *
 * Format d'un paquet : int (numero du paquet dans le registre de NationsGUI) + donnees.
 * - Reception : on s'abonne au canal Forge "nationsgui" (un IPacketHandler cree par Proxy),
 *   on lit le numero, on retrouve la classe du paquet et on transmet les donnees brutes.
 * - Envoi : on fabrique le Packet250 avec le PacketDispatcher de FML et on l'envoie au joueur.
 * Les numeros sont lus dans le registre du mod au demarrage : ils restent justes meme
 * apres une mise a jour de NationsGUI.
 */
public class ReseauNG {

    /** Recoit les paquets du client. */
    public interface Recepteur {
        void recu(Player joueur, String paquet, DataInputStream donnees) throws Exception;
    }

    public static final String CANAL = "nationsgui";

    private final Plugin plugin;
    private Map<Class<?>, Integer> numeros;
    private List<Class<?>> classes;
    private Method fabriquer;
    private Method envoyerAuJoueur;
    private Field champDonnees;
    private Field champCanal;
    private boolean pret;
    private final Map<String, Recepteur> autres = new java.util.concurrent.ConcurrentHashMap<String, Recepteur>();
    private final Map<String, Recepteur> prefixes = new java.util.concurrent.ConcurrentHashMap<String, Recepteur>();

    /** Recepteur pour tous les paquets dont le nom commence par ce prefixe (ex. "Island"). */
    public void ajouterPrefixe(String prefixe, Recepteur r) {
        prefixes.put(prefixe, r);
    }

    /** Ajoute un recepteur pour un paquet NationsGUI precis (nom simple de la classe). */
    public void ajouter(String nomPaquet, Recepteur r) {
        autres.put(nomPaquet, r);
    }

    public ReseauNG(Plugin plugin) {
        this.plugin = plugin;
    }

    public boolean pret() {
        return pret;
    }

    @SuppressWarnings("unchecked")
    public boolean demarrer(final Recepteur recepteur) {
        try {
            ClassLoader cl = Bukkit.getServer().getClass().getClassLoader();
            Class<?> registre = Class.forName("net.ilexiconn.nationsgui.forge.server.packet.PacketRegistry", true, cl);
            Object instance = registre.getField("INSTANCE").get(null);
            for (Field f : registre.getDeclaredFields()) {
                f.setAccessible(true);
                if (Map.class.isAssignableFrom(f.getType()) && numeros == null) {
                    numeros = (Map<Class<?>, Integer>) f.get(instance);
                } else if (List.class.isAssignableFrom(f.getType()) && classes == null) {
                    classes = (List<Class<?>>) f.get(instance);
                }
            }
            if (numeros == null || classes == null || classes.isEmpty()) {
                throw new IllegalStateException("registre des paquets NationsGUI introuvable");
            }

            Class<?> dispatcher = Class.forName("cpw.mods.fml.common.network.PacketDispatcher", true, cl);
            Class<?> paquetMc = Class.forName("net.minecraft.network.packet.Packet", true, cl);
            Class<?> joueurFml = Class.forName("cpw.mods.fml.common.network.Player", true, cl);
            fabriquer = dispatcher.getMethod("getPacket", String.class, byte[].class);
            envoyerAuJoueur = dispatcher.getMethod("sendPacketToPlayer", paquetMc, joueurFml);

            final Class<?> handlerItf = Class.forName("cpw.mods.fml.common.network.IPacketHandler", true, cl);
            Object handler = Proxy.newProxyInstance(handlerItf.getClassLoader(), new Class<?>[] {handlerItf}, new InvocationHandler() {
                public Object invoke(Object proxy, Method m, Object[] args) {
                    if (m.getName().equals("onPacketData") && args != null && args.length == 3) {
                        recevoir(recepteur, args[1], args[2]);
                        return null;
                    }
                    if (m.getName().equals("hashCode")) {
                        return Integer.valueOf(System.identityHashCode(proxy));
                    }
                    if (m.getName().equals("equals")) {
                        return Boolean.valueOf(proxy == args[0]);
                    }
                    if (m.getName().equals("toString")) {
                        return "SSNG-Core-NationsGUI";
                    }
                    return null;
                }
            });
            Class<?> registry = Class.forName("cpw.mods.fml.common.network.NetworkRegistry", true, cl);
            Object reg = registry.getMethod("instance").invoke(null);
            registry.getMethod("registerChannel", handlerItf, String.class).invoke(reg, handler, CANAL);
            pret = true;
            plugin.getLogger().info("Reseau NationsGUI branche (" + classes.size() + " paquets connus).");
            return true;
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Reseau NationsGUI indisponible : " + t, t);
            return false;
        }
    }

    private void recevoir(final Recepteur recepteur, Object paquet250, Object joueurMc) {
        try {
            if (champDonnees == null) {
                for (Class<?> c = paquet250.getClass(); c != null; c = c.getSuperclass()) {
                    for (Field f : c.getDeclaredFields()) {
                        if (f.getType() == byte[].class && champDonnees == null) {
                            f.setAccessible(true);
                            champDonnees = f;
                        } else if (f.getType() == String.class && champCanal == null) {
                            f.setAccessible(true);
                            champCanal = f;
                        }
                    }
                }
            }
            if (champCanal != null && !CANAL.equals(champCanal.get(paquet250))) {
                return;
            }
            byte[] brut = (byte[]) champDonnees.get(paquet250);
            if (brut == null || brut.length < 4) {
                return;
            }
            final DataInputStream in = new DataInputStream(new ByteArrayInputStream(brut));
            int num = in.readInt();
            if (num < 0 || num >= classes.size()) {
                return;
            }
            final String nom = classes.get(num).getSimpleName();
            Recepteur trouve = autres.get(nom);
            if (trouve == null) {
                for (Map.Entry<String, Recepteur> e : prefixes.entrySet()) {
                    if (nom.startsWith(e.getKey())) {
                        trouve = e.getValue();
                    }
                }
            }
            if (trouve == null) {
                trouve = recepteur;
            }
            if (trouve == null) {
                return;
            }
            final Recepteur cible = trouve;
            Method getBukkit = joueurMc.getClass().getMethod("getBukkitEntity");
            final Player p = (Player) getBukkit.invoke(joueurMc);
            Runnable r = new Runnable() {
                public void run() {
                    try {
                        cible.recu(p, nom, in);
                    } catch (Throwable t) {
                        plugin.getLogger().log(Level.WARNING, "Paquet " + nom + " de " + p.getName() + " : " + t, t);
                    }
                }
            };
            if (Bukkit.isPrimaryThread()) {
                r.run();
            } else {
                Bukkit.getScheduler().runTask(plugin, r);
            }
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Lecture d'un paquet NationsGUI impossible : " + t, t);
        }
    }

    /** Ecrit un paquet : prepare les donnees avec un DataOutputStream. */
    public interface Ecrivain {
        void ecrire(DataOutputStream out) throws Exception;
    }

    /** Mode test (/shadowmondes testpaquets) : les paquets sont relus par la classe NationsGUI au lieu d'etre envoyes. */
    public static volatile List<String> journalTest;

    public void envoyer(Player p, String nomPaquet, Ecrivain ecrivain) {
        if (journalTest != null && pret) {
            tester(nomPaquet, ecrivain);
            return;
        }
        if (!pret || p == null || !p.isOnline()) {
            return;
        }
        try {
            Integer num = null;
            for (Map.Entry<Class<?>, Integer> e : numeros.entrySet()) {
                if (e.getKey().getSimpleName().equals(nomPaquet)) {
                    num = e.getValue();
                    break;
                }
            }
            if (num == null) {
                plugin.getLogger().warning("Paquet NationsGUI inconnu : " + nomPaquet);
                return;
            }
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bout);
            out.writeInt(num.intValue());
            if (ecrivain != null) {
                ecrivain.ecrire(out);
            }
            out.flush();
            Object paquet = fabriquer.invoke(null, CANAL, bout.toByteArray());
            Object handle = p.getClass().getMethod("getHandle").invoke(p);
            envoyerAuJoueur.invoke(null, paquet, handle);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Envoi du paquet " + nomPaquet + " a " + p.getName() + " impossible : " + t, t);
        }
    }

    private Class<?> classe(String nomPaquet) {
        for (Class<?> c : numeros.keySet()) {
            if (c.getSimpleName().equals(nomPaquet)) {
                return c;
            }
        }
        return null;
    }

    /** Relit un paquet avec la classe NationsGUI (fromBytes) et note ses champs. */
    private void tester(String nomPaquet, Ecrivain ecrivain) {
        try {
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bout);
            if (ecrivain != null) {
                ecrivain.ecrire(out);
            }
            out.flush();
            Class<?> c = classe(nomPaquet);
            Object o = c.getConstructor().newInstance();
            ClassLoader cl = Bukkit.getServer().getClass().getClassLoader();
            Class<?> in = Class.forName("com.google.common.io.ByteArrayDataInput", true, cl);
            Object donnees = Class.forName("com.google.common.io.ByteStreams", true, cl).getMethod("newDataInput", byte[].class)
                    .invoke(null, bout.toByteArray());
            c.getMethod("fromBytes", in).invoke(o, donnees);
            StringBuilder sb = new StringBuilder(nomPaquet + " OK (" + bout.size() + " o) :");
            for (Field f : c.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                    continue;
                }
                f.setAccessible(true);
                Object v = f.get(o);
                String t = v instanceof int[] ? java.util.Arrays.toString((int[]) v) : v instanceof byte[] ? java.util.Arrays.toString((byte[]) v)
                        : v instanceof Object[] ? java.util.Arrays.toString((Object[]) v) : String.valueOf(v);
                sb.append(' ').append(f.getName()).append('=').append(t.length() > 160 ? t.substring(0, 160) + "..." : t);
            }
            journalTest.add(sb.toString());
        } catch (Throwable t) {
            journalTest.add(nomPaquet + " ERREUR : " + (t.getCause() != null ? t.getCause() : t));
        }
    }

    /** Simule un paquet envoye par le client (fabrique avec la classe NationsGUI et son toBytes). */
    public void simuler(Player p, Object paquetNG) throws Exception {
        ClassLoader cl = Bukkit.getServer().getClass().getClassLoader();
        Object sortie = Class.forName("com.google.common.io.ByteStreams", true, cl).getMethod("newDataOutput").invoke(null);
        Class<?> outItf = Class.forName("com.google.common.io.ByteArrayDataOutput", true, cl);
        paquetNG.getClass().getMethod("toBytes", outItf).invoke(paquetNG, sortie);
        byte[] b = (byte[]) outItf.getMethod("toByteArray").invoke(sortie);
        String nom = paquetNG.getClass().getSimpleName();
        Recepteur r = autres.get(nom);
        if (r == null) {
            journalTest.add("simulation " + nom + " : aucun recepteur");
            return;
        }
        r.recu(p, nom, new DataInputStream(new ByteArrayInputStream(b)));
        journalTest.add("simulation " + nom + " (" + b.length + " o) traitee");
    }

    /** Le paquet NationsGUI existe-t-il dans ce client / serveur ? */
    public boolean connu(String nomPaquet) {
        if (numeros == null) {
            return false;
        }
        for (Class<?> c : numeros.keySet()) {
            if (c.getSimpleName().equals(nomPaquet)) {
                return true;
            }
        }
        return false;
    }

    /** Envoie un paquet Minecraft deja construit (ex. Packet201PlayerInfo). */
    public void envoyerBrut(Player p, Object paquetMc) {
        if (!pret || p == null || !p.isOnline() || paquetMc == null) {
            return;
        }
        try {
            Object handle = p.getClass().getMethod("getHandle").invoke(p);
            envoyerAuJoueur.invoke(null, paquetMc, handle);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Envoi d'un paquet a " + p.getName() + " impossible : " + t, t);
        }
    }
}
