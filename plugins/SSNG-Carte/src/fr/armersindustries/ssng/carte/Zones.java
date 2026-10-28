package fr.armersindustries.ssng.carte;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import fr.armersindustries.ssng.core.SSNG;

/**
 * Zones par chunk, a la maniere des claims de faction : un admin "claim" des
 * chunks pour une zone, puis regle ses flags (PvP, construction, explosions...).
 * Hors zone, ce sont les regles normales du monde qui s'appliquent.
 */
public class Zones implements Listener, CommandExecutor {

    private static final String TITRE_GUI = ChatColor.DARK_GRAY + "Zone : ";
    private static final List<String> COMMANDES_TP = Arrays.asList(
            "tp", "tphere", "tpa", "tpahere", "tpaccept", "tpall", "tppos", "home", "homes", "spawn", "warp", "warps",
            "back", "hub", "lobby", "build", "test", "menu", "mondes", "pvp", "arene", "arena", "top", "jump", "i", "is", "ile");

    private final Carte plugin;
    private final File fichier;
    private final Map<String, Zone> zones = new LinkedHashMap<String, Zone>();
    /** "monde;cx;cz" -> zone */
    private final Map<String, Zone> chunks = new HashMap<String, Zone>();
    private final Map<String, ItemStack[][]> inventairesGardes = new HashMap<String, ItemStack[][]>();
    private final Map<String, String> derniereZone = new HashMap<String, String>();
    /** Droits de construction par chunk (carte NG) : "monde;cx;cz" -> [tous|Builder|Testeur|Moderateur..., joueur1, ...]. */
    private final Map<String, List<String>> accesChunks = new HashMap<String, List<String>>();

    public Zones(Carte plugin) {
        this.plugin = plugin;
        this.fichier = new File(plugin.getDataFolder(), "zones.yml");
        charger();
    }

    // ------------------------------------------------------------------ stockage

    public void charger() {
        zones.clear();
        chunks.clear();
        YamlConfiguration y = (fichier.exists() ? YamlConfiguration.loadConfiguration(fichier) : new YamlConfiguration());
        ConfigurationSection s = y.getConfigurationSection("zones");
        if (s == null) {
            return;
        }
        for (String id : s.getKeys(false)) {
            ConfigurationSection z = s.getConfigurationSection(id);
            Zone zone = new Zone(id);
            zone.nom = z.getString("nom", id);
            zone.couleur = z.getString("couleur", "&e");
            zone.spawn = z.getString("spawn", null);
            zone.reapparition = z.getString("reapparition", null);
            zone.volume = z.getString("volume", null);
            ConfigurationSection f = z.getConfigurationSection("flags");
            if (f != null) {
                for (String k : f.getKeys(false)) {
                    zone.regler(k, f.getBoolean(k));
                }
            }
            zones.put(id, zone);
            for (String c : z.getStringList("chunks")) {
                chunks.put(c, zone);
            }
        }
        accesChunks.clear();
        ConfigurationSection ac = y.getConfigurationSection("acces-chunks");
        if (ac != null) {
            for (String k : ac.getKeys(false)) {
                accesChunks.put(k.replace(',', ';'), new ArrayList<String>(ac.getStringList(k)));
            }
        }
        plugin.getLogger().info(zones.size() + " zone(s), " + chunks.size() + " chunk(s) claim(s), " + accesChunks.size() + " chunk(s) avec droits.");
    }

    public void sauver() {
        YamlConfiguration y = new YamlConfiguration();
        y.options().header("Zones par chunk (commande /zone). Mieux vaut les modifier en jeu.");
        for (Zone z : zones.values()) {
            String b = "zones." + z.id + ".";
            y.set(b + "nom", z.nom);
            y.set(b + "couleur", z.couleur);
            y.set(b + "spawn", z.spawn);
            y.set(b + "reapparition", z.reapparition);
            if (z.volume != null) {
                y.set(b + "volume", z.volume);
            }
            for (Map.Entry<String, Boolean> e : z.flags.entrySet()) {
                y.set(b + "flags." + e.getKey(), e.getValue());
            }
            List<String> l = new ArrayList<String>();
            for (Map.Entry<String, Zone> e : chunks.entrySet()) {
                if (e.getValue() == z) {
                    l.add(e.getKey());
                }
            }
            java.util.Collections.sort(l);
            y.set(b + "chunks", l);
        }
        for (Map.Entry<String, List<String>> e : accesChunks.entrySet()) {
            y.set("acces-chunks." + e.getKey().replace(';', ','), e.getValue());
        }
        try {
            y.save(fichier);
        } catch (Exception e) {
            plugin.getLogger().warning("Sauvegarde des zones impossible : " + e);
        }
    }

    // ------------------------------------------------------------------ acces

    private static String cle(World w, int cx, int cz) {
        return w.getName() + ";" + cx + ";" + cz;
    }

    public Zone zone(String id) {
        return id == null ? null : zones.get(id.toLowerCase());
    }

    public java.util.Collection<Zone> toutes() {
        return zones.values();
    }

    public Zone zoneA(Location l) {
        if (l == null || l.getWorld() == null) {
            return null;
        }
        for (Zone z : zones.values()) {
            if (z.volume != null && z.dansVolume(l)) {
                return z;
            }
        }
        return chunks.get(cle(l.getWorld(), l.getBlockX() >> 4, l.getBlockZ() >> 4));
    }

    /** Valeur du flag a cet endroit, ou "horsZone" s'il n'y a pas de zone. */
    public boolean flag(Location l, String f, boolean horsZone) {
        Zone z = zoneA(l);
        return z == null ? horsZone : z.flag(f);
    }

    public Zone creer(String id) {
        id = id.toLowerCase();
        Zone z = zones.get(id);
        if (z == null) {
            z = new Zone(id);
            zones.put(id, z);
        }
        return z;
    }

    public void supprimer(Zone z) {
        zones.remove(z.id);
        Iterator<Map.Entry<String, Zone>> it = chunks.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue() == z) {
                it.remove();
            }
        }
    }

    /** Claim des chunks qui couvrent le rectangle (coordonnees de blocs). */
    public int claimRectangle(Zone z, World w, int x1, int z1, int x2, int z2) {
        int n = 0;
        for (int cx = Math.min(x1, x2) >> 4; cx <= Math.max(x1, x2) >> 4; cx++) {
            for (int cz = Math.min(z1, z2) >> 4; cz <= Math.max(z1, z2) >> 4; cz++) {
                chunks.put(cle(w, cx, cz), z);
                n++;
            }
        }
        return n;
    }

    private int claim(Zone z, Location l, int rayon) {
        int n = 0;
        int ccx = l.getBlockX() >> 4;
        int ccz = l.getBlockZ() >> 4;
        for (int cx = ccx - rayon; cx <= ccx + rayon; cx++) {
            for (int cz = ccz - rayon; cz <= ccz + rayon; cz++) {
                String k = cle(l.getWorld(), cx, cz);
                if (z == null) {
                    if (chunks.remove(k) != null) {
                        n++;
                    }
                } else {
                    chunks.put(k, z);
                    n++;
                }
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ chunks (carte NG)

    public static String cleChunk(String monde, int cx, int cz) {
        return monde + ";" + cx + ";" + cz;
    }

    public Zone zoneChunk(String monde, int cx, int cz) {
        return chunks.get(cleChunk(monde, cx, cz));
    }

    public void claimChunk(Zone z, String monde, int cx, int cz) {
        chunks.put(cleChunk(monde, cx, cz), z);
    }

    public Zone unclaimChunk(String monde, int cx, int cz) {
        return chunks.remove(cleChunk(monde, cx, cz));
    }

    /** Chunks claims d'un monde : cle "cx;cz" -> zone. */
    public Map<String, Zone> chunksDuMonde(String monde) {
        Map<String, Zone> m = new HashMap<String, Zone>();
        String p = monde + ";";
        for (Map.Entry<String, Zone> e : chunks.entrySet()) {
            if (e.getKey().startsWith(p)) {
                m.put(e.getKey().substring(p.length()), e.getValue());
            }
        }
        return m;
    }

    public List<String> accesChunk(String monde, int cx, int cz) {
        return accesChunks.get(cleChunk(monde, cx, cz));
    }

    public void reglerAccesChunk(String monde, int cx, int cz, List<String> acces) {
        if (acces == null || acces.isEmpty()) {
            accesChunks.remove(cleChunk(monde, cx, cz));
        } else {
            accesChunks.put(cleChunk(monde, cx, cz), new ArrayList<String>(acces));
        }
    }

    /** Peut-on construire ici ? Droits du chunk (carte NG) d'abord, puis la regle "construire" de la zone. */
    public boolean peutConstruire(Player p, Location l) {
        if (admin(p)) {
            return true;
        }
        List<String> acces = accesChunks.get(cleChunk(l.getWorld().getName(), l.getBlockX() >> 4, l.getBlockZ() >> 4));
        if (acces != null) {
            for (String a : acces) {
                if (a.equalsIgnoreCase("tous") || a.equalsIgnoreCase(p.getName())) {
                    return true;
                }
                if (a.startsWith("grade:") && plugin.grades().auMoins(p, a.substring(6))) {
                    return true;
                }
            }
            return false;
        }
        return flag(l, "construire", true);
    }

    public int nbChunks(Zone z) {
        int n = 0;
        for (Zone x : chunks.values()) {
            if (x == z) {
                n++;
            }
        }
        return n;
    }

    private boolean admin(Player p) {
        return SSNG.admin(p);
    }

    private boolean occupe(Player p) {
        return plugin.occupe(p);
    }

    // ------------------------------------------------------------------ regles

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void casser(BlockBreakEvent e) {
        if (!peutConstruire(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true);
            SSNG.msg(e.getPlayer(), "&cZone protegee.");
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void poser(BlockPlaceEvent e) {
        if (!peutConstruire(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true);
            SSNG.msg(e.getPlayer(), "&cZone protegee.");
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void seau(PlayerBucketEmptyEvent e) {
        if (!peutConstruire(e.getPlayer(), e.getBlockClicked().getRelative(e.getBlockFace()).getLocation())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void seauRemplir(PlayerBucketFillEvent e) {
        if (!peutConstruire(e.getPlayer(), e.getBlockClicked().getLocation())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void explosion(EntityExplodeEvent e) {
        Iterator<Block> it = e.blockList().iterator();
        while (it.hasNext()) {
            if (!flag(it.next().getLocation(), "explosions", true)) {
                it.remove();
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void allumer(BlockIgniteEvent e) {
        if (e.getCause() == BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL && e.getPlayer() != null && admin(e.getPlayer())) {
            return;
        }
        if (!flag(e.getBlock().getLocation(), "feu", true)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void bruler(BlockBurnEvent e) {
        if (!flag(e.getBlock().getLocation(), "feu", true)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void propager(BlockSpreadEvent e) {
        if (e.getNewState().getType() == Material.FIRE && !flag(e.getBlock().getLocation(), "feu", true)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void degats(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player)) {
            return;
        }
        Player p = (Player) e.getEntity();
        Zone z = zoneA(p.getLocation());
        if (z == null || z.flag("degats")) {
            return;
        }
        e.setCancelled(true);
        if (e.getCause() == EntityDamageEvent.DamageCause.VOID && z.spawn() != null) {
            p.setFallDistance(0);
            p.teleport(z.spawn());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void pvp(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player)) {
            return;
        }
        Entity source = e.getDamager();
        if (source instanceof Projectile && ((Projectile) source).getShooter() instanceof Player) {
            source = (Entity) ((Projectile) source).getShooter();
        }
        if (!(source instanceof Player) || source == e.getEntity()) {
            return;
        }
        if (!flag(source.getLocation(), "pvp", true) || !flag(e.getEntity().getLocation(), "pvp", true)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void faim(FoodLevelChangeEvent e) {
        if (e.getFoodLevel() < ((Player) e.getEntity()).getFoodLevel() && !flag(e.getEntity().getLocation(), "faim", true)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void jeter(PlayerDropItemEvent e) {
        if (!admin(e.getPlayer()) && !flag(e.getPlayer().getLocation(), "jeter", true)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void ramasser(PlayerPickupItemEvent e) {
        if (!flag(e.getPlayer().getLocation(), "ramasser", true)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void monstres(CreatureSpawnEvent e) {
        CreatureSpawnEvent.SpawnReason r = e.getSpawnReason();
        if ((r == CreatureSpawnEvent.SpawnReason.NATURAL || r == CreatureSpawnEvent.SpawnReason.SPAWNER
                || r == CreatureSpawnEvent.SpawnReason.CHUNK_GEN || r == CreatureSpawnEvent.SpawnReason.JOCKEY)
                && !flag(e.getLocation(), "monstres", true)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void bouger(PlayerMoveEvent e) {
        Location de = e.getFrom();
        Location vers = e.getTo();
        if (vers == null || (de.getBlockX() >> 4) == (vers.getBlockX() >> 4) && (de.getBlockZ() >> 4) == (vers.getBlockZ() >> 4)
                && de.getWorld() == vers.getWorld()) {
            return;
        }
        changerZone(e.getPlayer(), de, vers, e);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void teleporter(PlayerTeleportEvent e) {
        changerZone(e.getPlayer(), e.getFrom(), e.getTo(), null);
    }

    private void changerZone(Player p, Location de, Location vers, PlayerMoveEvent mouvement) {
        Zone z = zoneA(vers);
        if (z != null && !z.flag("entree") && !admin(p) && zoneA(de) != z) {
            if (mouvement != null) {
                mouvement.setTo(de);
            }
            SSNG.msg(p, "&cLa zone " + z.couleur + z.nom + " &cest fermee.");
            return;
        }
        String avant = derniereZone.get(p.getName());
        String id = z == null ? "" : z.id;
        if (id.equals(avant == null ? "" : avant)) {
            return;
        }
        derniereZone.put(p.getName(), id);
        if (z != null) {
            SSNG.msg(p, "&7Zone : " + z.couleur + z.nom + (z.flag("pvp") ? " &c(PvP)" : " &a(sans PvP)"));
        }
    }

    @EventHandler
    public void quitter(PlayerQuitEvent e) {
        derniereZone.remove(e.getPlayer().getName());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void mort(PlayerDeathEvent e) {
        Player p = e.getEntity();
        if (occupe(p)) {
            return;
        }
        Zone z = zoneA(p.getLocation());
        if (z != null && z.flag("garder-inventaire")) {
            inventairesGardes.put(p.getName(), new ItemStack[][] {p.getInventory().getContents().clone(), p.getInventory().getArmorContents().clone()});
            e.getDrops().clear();
            e.setKeepLevel(true);
            e.setDroppedExp(0);
        }
        if (z != null) {
            derniereZone.put(p.getName() + "#mort", z.id);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void reapparition(PlayerRespawnEvent e) {
        final Player p = e.getPlayer();
        String morte = derniereZone.remove(p.getName() + "#mort");
        final ItemStack[][] garde = inventairesGardes.remove(p.getName());
        if (garde != null) {
            Bukkit.getScheduler().runTask(plugin, new Runnable() {
                public void run() {
                    p.getInventory().setContents(garde[0]);
                    p.getInventory().setArmorContents(garde[1]);
                    p.updateInventory();
                }
            });
        }
        if (occupe(p)) {
            return;
        }
        Zone z = zone(morte);
        if (z == null) {
            return;
        }
        Zone cible = z.reapparition != null && !z.reapparition.isEmpty() ? zone(z.reapparition) : null;
        Location l = cible != null ? cible.spawn() : null;
        if (l != null) {
            e.setRespawnLocation(l);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void commandesTp(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (admin(p)) {
            return;
        }
        String cmd = e.getMessage().substring(1).split("\\s+")[0].toLowerCase();
        if (cmd.contains(":")) {
            cmd = cmd.substring(cmd.indexOf(':') + 1);
        }
        if (COMMANDES_TP.contains(cmd) && !flag(p.getLocation(), "teleportation", true)) {
            e.setCancelled(true);
            SSNG.msg(p, "&cLes teleportations sont bloquees dans cette zone.");
        }
    }

    // ------------------------------------------------------------------ interface des flags

    @SuppressWarnings("deprecation")
    public void ouvrirFlags(Player p, Zone z) {
        Inventory inv = Bukkit.createInventory(null, 27, TITRE_GUI + z.id);
        int i = 0;
        for (Map.Entry<String, String[]> f : Zone.FLAGS.entrySet()) {
            boolean v = z.flag(f.getKey());
            ItemStack it = new ItemStack(35, 1, (short) (v ? 5 : 14));
            ItemMeta m = it.getItemMeta();
            m.setDisplayName((v ? ChatColor.GREEN : ChatColor.RED) + f.getKey() + " : " + (v ? "OUI" : "NON"));
            m.setLore(Arrays.asList(ChatColor.GRAY + f.getValue()[1], ChatColor.YELLOW + "Clic : changer"));
            it.setItemMeta(m);
            inv.setItem(i++, it);
        }
        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta m = info.getItemMeta();
        m.setDisplayName(SSNG.c(z.couleur + z.nom));
        m.setLore(Arrays.asList(ChatColor.GRAY + "" + nbChunks(z) + " chunk(s)",
                ChatColor.GRAY + "Reapparition : " + (z.reapparition == null ? "normale" : z.reapparition),
                ChatColor.GRAY + "Spawn : " + (z.spawn == null ? "aucun" : "oui")));
        info.setItemMeta(m);
        inv.setItem(26, info);
        p.openInventory(inv);
    }

    @EventHandler(ignoreCancelled = true)
    public void clicFlags(InventoryClickEvent e) {
        String titre = e.getInventory().getTitle();
        if (titre == null || !titre.startsWith(TITRE_GUI)) {
            return;
        }
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player)) {
            return;
        }
        final Player p = (Player) e.getWhoClicked();
        final Zone z = zone(titre.substring(TITRE_GUI.length()));
        int slot = e.getRawSlot();
        if (z == null || !admin(p) || slot < 0 || slot >= Zone.FLAGS.size()) {
            return;
        }
        String f = new ArrayList<String>(Zone.FLAGS.keySet()).get(slot);
        z.regler(f, !z.flag(f));
        sauver();
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            public void run() {
                ouvrirFlags(p, z);
            }
        });
    }

    // ------------------------------------------------------------------ /zone

    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (!SSNG.admin(s)) {
            SSNG.msg(s, "&cReserve aux admins.");
            return true;
        }
        if (a.length == 0) {
            aide(s);
            return true;
        }
        String sous = a[0].toLowerCase();
        Player p = s instanceof Player ? (Player) s : null;
        if (sous.equals("liste")) {
            SSNG.msg(s, "&6Zones :");
            for (Zone z : zones.values()) {
                SSNG.msg(s, "&7- " + z.couleur + z.id + " &8(" + z.nom + ", " + nbChunks(z) + " chunks" + (z.flag("pvp") ? ", PvP" : "") + ")");
            }
            return true;
        }
        if (sous.equals("reload")) {
            charger();
            SSNG.msg(s, "&azones.yml recharge.");
            return true;
        }
        if (p == null && Arrays.asList("info", "carte", "unclaim", "claim", "flags", "spawn", "tp").contains(sous)) {
            SSNG.msg(s, "&cCommande a utiliser en jeu.");
            return true;
        }
        Zone ici = p == null ? null : zoneA(p.getLocation());
        if (sous.equals("choisir") && p != null) {
            Zone z = a.length > 1 ? zone(a[1]) : ici;
            if (z == null) {
                SSNG.msg(p, "&c/zone choisir <id> &7: zone a modifier depuis la carte NG (onglet Gestion du territoire).");
            } else {
                plugin.carte().choisir(p, z);
                SSNG.msg(p, "&aCarte NG : tu modifies maintenant la zone " + z.couleur + z.nom + "&a (selection de chunks, ajout, retrait, droits).");
            }
            return true;
        }
        if (sous.equals("info")) {
            Chunk ch = p.getLocation().getChunk();
            SSNG.msg(p, "&7Chunk " + ch.getX() + ", " + ch.getZ() + " : " + (ici == null ? "&2aucune zone (regles du monde)" : ici.couleur + ici.id));
            if (ici != null) {
                StringBuilder sb = new StringBuilder();
                for (String f : Zone.FLAGS.keySet()) {
                    sb.append(ici.flag(f) ? "&a" : "&c").append(f).append(" ");
                }
                SSNG.msg(p, sb.toString());
            }
            return true;
        }
        if (sous.equals("carte")) {
            carte(p);
            return true;
        }
        if (sous.equals("creer") && a.length >= 2) {
            Zone z = creer(a[1]);
            if (a.length >= 3) {
                z.nom = join(a, 2);
            }
            sauver();
            SSNG.msg(s, "&aZone " + z.id + " creee. &7Ajoute des chunks : &f/zone claim " + z.id + " [rayon]");
            return true;
        }
        if (sous.equals("unclaim")) {
            int r = a.length >= 2 ? entier(a[1], 0) : 0;
            int n = claim(null, p.getLocation(), Math.min(10, r));
            sauver();
            SSNG.msg(p, "&a" + n + " chunk(s) liberes.");
            return true;
        }
        Zone z = a.length >= 2 ? zone(a[1]) : null;
        if (a.length >= 2 && z == null && !sous.equals("creer")) {
            SSNG.msg(s, "&cZone inconnue : " + a[1] + " &7(/zone liste)");
            return true;
        }
        if (sous.equals("claim") && z != null) {
            int r = a.length >= 3 ? entier(a[2], 0) : 0;
            int n = claim(z, p.getLocation(), Math.min(10, r));
            sauver();
            SSNG.msg(p, "&a" + n + " chunk(s) ajoutes a " + z.id + ".");
            return true;
        }
        if (sous.equals("supprimer") && z != null) {
            supprimer(z);
            sauver();
            SSNG.msg(s, "&aZone " + z.id + " supprimee.");
            return true;
        }
        if (sous.equals("flags") && z != null) {
            ouvrirFlags(p, z);
            return true;
        }
        if (sous.equals("flag") && z != null && a.length >= 4) {
            if (!Zone.FLAGS.containsKey(a[2].toLowerCase())) {
                SSNG.msg(s, "&cFlag inconnu. &7Flags : " + Zone.FLAGS.keySet());
                return true;
            }
            boolean v = a[3].equalsIgnoreCase("on") || a[3].equalsIgnoreCase("oui") || a[3].equalsIgnoreCase("true");
            z.regler(a[2].toLowerCase(), v);
            sauver();
            SSNG.msg(s, "&a" + z.id + " : " + a[2].toLowerCase() + " = " + (v ? "oui" : "non"));
            return true;
        }
        if (sous.equals("spawn") && z != null) {
            z.spawn = Zone.ecrire(p.getLocation());
            sauver();
            SSNG.msg(p, "&aSpawn de " + z.id + " place ici.");
            return true;
        }
        if (sous.equals("tp") && z != null) {
            if (z.spawn() == null) {
                SSNG.msg(p, "&cCette zone n'a pas de spawn (/zone spawn " + z.id + ").");
            } else {
                p.teleport(z.spawn());
            }
            return true;
        }
        if (sous.equals("reapparition") && z != null && a.length >= 3) {
            String cible = a[2].toLowerCase();
            if (cible.equals("aucune") || cible.equals("normale")) {
                z.reapparition = null;
            } else if (zone(cible) == null) {
                SSNG.msg(s, "&cZone inconnue : " + cible);
                return true;
            } else {
                z.reapparition = cible;
            }
            sauver();
            SSNG.msg(s, "&aReapparition de " + z.id + " : " + (z.reapparition == null ? "normale" : z.reapparition));
            return true;
        }
        if (sous.equals("nom") && z != null && a.length >= 3) {
            z.nom = join(a, 2);
            sauver();
            SSNG.msg(s, "&aNom de " + z.id + " : " + z.nom);
            return true;
        }
        if (sous.equals("couleur") && z != null && a.length >= 3) {
            z.couleur = a[2];
            sauver();
            SSNG.msg(s, "&aCouleur de " + z.id + " : " + z.couleur + z.nom);
            return true;
        }
        aide(s);
        return true;
    }

    private void aide(CommandSender s) {
        SSNG.msg(s, "&6/zone liste &7| &6info &7| &6carte");
        SSNG.msg(s, "&6/zone creer <id> [nom] &7| &6supprimer <id>");
        SSNG.msg(s, "&6/zone claim <id> [rayon] &7| &6unclaim [rayon] &7(rayon en chunks autour de toi)");
        SSNG.msg(s, "&6/zone flags <id> &7(interface) | &6flag <id> <flag> <on|off>");
        SSNG.msg(s, "&6/zone spawn <id> &7| &6tp <id> &7| &6reapparition <id> <zone|aucune>");
        SSNG.msg(s, "&6/zone nom <id> <nom> &7| &6couleur <id> <&code> &7| &6reload");
        SSNG.msg(s, "&6/zone choisir <id> &7: zone geree depuis la carte NG (chunks et droits par chunk)");
        SSNG.msg(s, "&7Flags : " + Zone.FLAGS.keySet());
    }

    public void carte(Player p) {
        int ccx = p.getLocation().getBlockX() >> 4;
        int ccz = p.getLocation().getBlockZ() >> 4;
        Map<Zone, Character> lettres = new LinkedHashMap<Zone, Character>();
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        String[] couleurs = {"&c", "&9", "&a", "&e", "&d", "&b", "&6", "&5", "&3", "&2"};
        SSNG.msg(p, "&6Carte des zones &7(nord en haut, &f+&7 = toi, &8-&7 = libre)");
        for (int dz = -5; dz <= 5; dz++) {
            StringBuilder sb = new StringBuilder();
            for (int dx = -12; dx <= 12; dx++) {
                Zone z = chunks.get(cle(p.getWorld(), ccx + dx, ccz + dz));
                if (dx == 0 && dz == 0) {
                    sb.append("&f+");
                } else if (z == null) {
                    sb.append("&8-");
                } else {
                    if (!lettres.containsKey(z)) {
                        lettres.put(z, alphabet.charAt(lettres.size() % alphabet.length()));
                    }
                    int i = new ArrayList<Zone>(lettres.keySet()).indexOf(z);
                    sb.append(couleurs[i % couleurs.length]).append(lettres.get(z));
                }
            }
            p.sendMessage(SSNG.c(sb.toString()));
        }
        int i = 0;
        for (Map.Entry<Zone, Character> e : lettres.entrySet()) {
            p.sendMessage(SSNG.c(couleurs[i++ % couleurs.length] + e.getValue() + " &7= " + e.getKey().id));
        }
    }

    private static String join(String[] a, int debut) {
        StringBuilder sb = new StringBuilder();
        for (int i = debut; i < a.length; i++) {
            sb.append(i > debut ? " " : "").append(a[i]);
        }
        return sb.toString();
    }

    private static int entier(String s, int d) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return d;
        }
    }
}
