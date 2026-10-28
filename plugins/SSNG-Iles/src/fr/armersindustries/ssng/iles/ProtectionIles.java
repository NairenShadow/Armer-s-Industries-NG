package fr.armersindustries.ssng.iles;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.WeatherType;
import org.bukkit.block.Block;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryHolder;

import fr.armersindustries.ssng.core.SSNG;
import fr.armersindustries.ssng.core.ReseauNG;

/**
 * Regles appliquees sur les iles : permissions par role (createur / membre / visiteur / banni),
 * proprietes de l'ile (explosions, feu, eau, PvP, mobs...), heure et meteo par ile,
 * entree/sortie (message d'accueil, visites) et masque WorldEdit.
 */
public class ProtectionIles implements Listener {

    private final IlesNG plugin;
    private final GestionIles iles;
    private final PaquetsIles paquets;
    /** Ile actuelle de chaque joueur (id, ou -1). */
    private final Map<String, Integer> ileActuelle = new HashMap<String, Integer>();

    public ProtectionIles(IlesNG plugin, GestionIles iles, PaquetsIles paquets) {
        this.plugin = plugin;
        this.iles = iles;
        this.paquets = paquets;
        Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            public void run() {
                suivre();
            }
        }, 20L, 10L);
    }

    // ------------------------------------------------------------------ suivi entree / sortie

    private void suivre() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Ile i = iles.ileA(p.getLocation());
            int id = i == null ? -1 : i.id;
            Integer avant = ileActuelle.get(p.getName());
            if (avant == null || avant.intValue() != id) {
                ileActuelle.put(p.getName(), Integer.valueOf(id));
                if (i != null) {
                    entrer(p, i);
                } else {
                    sortir(p);
                }
            }
            if (i != null && iles.role(p, i).equals("banni")) {
                p.teleport(plugin.spawn(IlesNG.TypeMonde.BUILD));
                SSNG.msg(p, "&cTu es banni de cette ile.");
            }
            if (plugin.type(p.getWorld()) == IlesNG.TypeMonde.BUILD && p.getLocation().getY() < -10) {
                p.setFallDistance(0);
                p.teleport(i != null ? iles.spawn(i) : plugin.spawn(IlesNG.TypeMonde.BUILD));
            }
        }
    }

    private void entrer(Player p, Ile i) {
        String role = iles.role(p, i);
        if (role.equals("visiteur") && i.privee && !plugin.getConfig().getBoolean("iles.toutes-publiques", true)) {
            p.teleport(plugin.spawn(IlesNG.TypeMonde.BUILD));
            SSNG.msg(p, "&cCette ile est privee.");
            return;
        }
        if (role.equals("visiteur") && !PaquetsIles.contient(i.visiteurs, p.getName())) {
            i.visiteurs.add(p.getName());
            while (i.visiteurs.size() > 60) {
                i.visiteurs.remove(0);
            }
        }
        if (!i.estMembre(p.getName())) {
            i.visites++;
        }
        iles.sauver(i);
        if (!i.bienvenue.isEmpty()) {
            p.sendMessage(SSNG.c("&d[" + i.nom + "] &f" + i.bienvenue));
        } else {
            p.sendMessage(SSNG.c("&7Bienvenue sur &d" + i.nom + " &7(ile de " + i.createur + "). &8/i i pour le menu"));
        }
        ambiance(p, i);
        paquets.envoyerIleCourante(p, i);
        MasqueWorldEdit.appliquer(plugin, iles, p);
    }

    private void sortir(Player p) {
        p.resetPlayerTime();
        p.resetPlayerWeather();
        paquets.envoyerIleCourante(p, null);
    }

    private void ambiance(Player p, Ile i) {
        if ("day".equals(i.heure)) {
            p.setPlayerTime(6000L, false);
        } else if ("night".equals(i.heure)) {
            p.setPlayerTime(18000L, false);
        } else {
            p.resetPlayerTime();
        }
        if ("sun".equals(i.meteo)) {
            p.setPlayerWeather(WeatherType.CLEAR);
        } else if ("rain".equals(i.meteo) || "storm".equals(i.meteo)) {
            p.setPlayerWeather(WeatherType.DOWNFALL);
        } else {
            p.resetPlayerWeather();
        }
    }

    /** Reapplique heure/meteo a tous les joueurs presents sur l'ile. */
    public void appliquerAmbiance(Ile i) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Ile x = iles.ileA(p.getLocation());
            if (x != null && x.id == i.id) {
                ambiance(p, i);
            }
        }
    }

    @EventHandler
    public void quitter(PlayerQuitEvent e) {
        ileActuelle.remove(e.getPlayer().getName());
    }

    // ------------------------------------------------------------------ outils

    private boolean dansBuild(Location l) {
        return l != null && plugin.type(l.getWorld()) == IlesNG.TypeMonde.BUILD;
    }

    /** Le joueur a-t-il cette permission a cet endroit du monde Build ? (hors iles : admins seulement) */
    private boolean autorise(Player p, Location l, String perm) {
        if (SSNG.admin(p)) {
            return true;
        }
        Ile i = iles.ileA(l);
        return i != null && iles.permission(p, i, perm);
    }

    private void refus(Player p, String quoi) {
        SSNG.msg(p, "&cTu n'as pas la permission (" + quoi + ") sur cette ile.");
    }

    // ------------------------------------------------------------------ permissions des joueurs

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void poser(BlockPlaceEvent e) {
        if (dansBuild(e.getBlock().getLocation()) && !autorise(e.getPlayer(), e.getBlock().getLocation(), "build")) {
            e.setCancelled(true);
            refus(e.getPlayer(), "construire");
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void casser(BlockBreakEvent e) {
        if (dansBuild(e.getBlock().getLocation()) && !autorise(e.getPlayer(), e.getBlock().getLocation(), "build")) {
            e.setCancelled(true);
            refus(e.getPlayer(), "construire");
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void seauVider(PlayerBucketEmptyEvent e) {
        Location l = e.getBlockClicked().getRelative(e.getBlockFace()).getLocation();
        if (dansBuild(l) && !autorise(e.getPlayer(), l, "build")) {
            e.setCancelled(true);
            refus(e.getPlayer(), "construire");
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void seauRemplir(PlayerBucketFillEvent e) {
        Location l = e.getBlockClicked().getLocation();
        if (dansBuild(l) && !autorise(e.getPlayer(), l, "build")) {
            e.setCancelled(true);
            refus(e.getPlayer(), "construire");
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void interagir(PlayerInteractEvent e) {
        Block b = e.getClickedBlock();
        Player p = e.getPlayer();
        if (b == null || !dansBuild(b.getLocation())) {
            return;
        }
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getItem() != null
                && (e.getItem().getType() == Material.FLINT_AND_STEEL || e.getItem().getType() == Material.FIREBALL)
                && !autorise(p, b.getLocation(), "lighter")) {
            e.setCancelled(true);
            refus(p, "briquet");
            return;
        }
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK || e.getAction() == Action.PHYSICAL) {
            boolean conteneur = b.getState() instanceof InventoryHolder;
            String perm = conteneur ? "chest_access" : "interact";
            if (!autorise(p, b.getLocation(), perm)) {
                e.setCancelled(true);
                if (e.getAction() != Action.PHYSICAL) {
                    refus(p, conteneur ? "coffres" : "interactions");
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void ouvrir(InventoryOpenEvent e) {
        if (!(e.getPlayer() instanceof Player) || !(e.getInventory().getHolder() instanceof org.bukkit.block.BlockState)) {
            return;
        }
        Location l = ((org.bukkit.block.BlockState) e.getInventory().getHolder()).getLocation();
        if (dansBuild(l) && !autorise((Player) e.getPlayer(), l, "chest_access")) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void entite(PlayerInteractEntityEvent e) {
        Location l = e.getRightClicked().getLocation();
        if (dansBuild(l) && !(e.getRightClicked() instanceof Player) && !autorise(e.getPlayer(), l, "bike")) {
            e.setCancelled(true);
            refus(e.getPlayer(), "montures / mobs");
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void jeter(PlayerDropItemEvent e) {
        Location l = e.getPlayer().getLocation();
        Ile i = iles.ileA(l);
        if (i != null && (!i.drapeau("item-drop") || !autorise(e.getPlayer(), l, "item_drop"))) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void ramasser(PlayerPickupItemEvent e) {
        Location l = e.getItem().getLocation();
        Ile i = iles.ileA(l);
        if (i != null && (!i.drapeau("item-pickup") || !autorise(e.getPlayer(), l, "item_pickup"))) {
            e.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ proprietes de l'ile

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void explosion(EntityExplodeEvent e) {
        if (!dansBuild(e.getLocation())) {
            return;
        }
        Entity source = e.getEntity();
        String drapeau = source instanceof TNTPrimed ? "tnt" : source instanceof Creeper ? "creeper-explosion" : "other-explosion";
        Iterator<Block> it = e.blockList().iterator();
        while (it.hasNext()) {
            Ile i = iles.ileA(it.next().getLocation());
            if (i == null || !i.drapeau(drapeau)) {
                it.remove();
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void feu(BlockIgniteEvent e) {
        if (!dansBuild(e.getBlock().getLocation()) || e.getCause() == BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL) {
            return;
        }
        Ile i = iles.ileA(e.getBlock().getLocation());
        if (i == null || !i.drapeau("fire-spread")) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void brule(BlockBurnEvent e) {
        Ile i = iles.ileA(e.getBlock().getLocation());
        if (dansBuild(e.getBlock().getLocation()) && (i == null || !i.drapeau("fire-spread"))) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void propagation(BlockSpreadEvent e) {
        if (e.getNewState().getType() == Material.FIRE) {
            Ile i = iles.ileA(e.getBlock().getLocation());
            if (dansBuild(e.getBlock().getLocation()) && (i == null || !i.drapeau("fire-spread"))) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void ecoulement(BlockFromToEvent e) {
        if (!dansBuild(e.getBlock().getLocation())) {
            return;
        }
        Ile vers = iles.ileA(e.getToBlock().getLocation());
        if (vers == null) {
            e.setCancelled(true);
            return;
        }
        Material m = e.getBlock().getType();
        boolean eau = m == Material.WATER || m == Material.STATIONARY_WATER;
        boolean lave = m == Material.LAVA || m == Material.STATIONARY_LAVA;
        if (eau && !vers.drapeau("water-flow") || lave && !vers.drapeau("lava-flow")) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void fonte(BlockFadeEvent e) {
        Material m = e.getBlock().getType();
        if ((m == Material.SNOW || m == Material.ICE) && dansBuild(e.getBlock().getLocation())) {
            Ile i = iles.ileA(e.getBlock().getLocation());
            if (i == null || !i.drapeau("snow-melt")) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void apparition(CreatureSpawnEvent e) {
        if (!dansBuild(e.getLocation())) {
            return;
        }
        Ile i = iles.ileA(e.getLocation());
        boolean naturel = e.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL
                || e.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CHUNK_GEN;
        if (i == null) {
            if (naturel) {
                e.setCancelled(true);
            }
            return;
        }
        if (naturel && e.getEntity() instanceof Monster && !i.drapeau("mob_hostile")
                || naturel && e.getEntity() instanceof Animals && !i.drapeau("mob_passive")) {
            e.setCancelled(true);
        }
    }

    /** Degats sur les iles. Passe apres les autres protections pour le re-autoriser si l'ile le permet. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void degats(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player) || !dansBuild(e.getEntity().getLocation())) {
            return;
        }
        Ile i = iles.ileA(e.getEntity().getLocation());
        if (i == null) {
            return;
        }
        boolean autorise;
        if (e instanceof EntityDamageByEntityEvent) {
            Entity d = ((EntityDamageByEntityEvent) e).getDamager();
            if (d instanceof Projectile && ((Projectile) d).getShooter() != null) {
                d = (Entity) ((Projectile) d).getShooter();
            }
            autorise = d instanceof Player ? i.drapeau("pvp") : i.drapeau("mob-damage");
        } else if (e.getCause() == EntityDamageEvent.DamageCause.FALL) {
            autorise = i.drapeau("fall-damage");
        } else if (e.getCause() == EntityDamageEvent.DamageCause.VOID) {
            autorise = false;
        } else {
            autorise = i.drapeau("mob-damage") || i.drapeau("pvp");
        }
        e.setCancelled(!autorise);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void faim(FoodLevelChangeEvent e) {
        Ile i = iles.ileA(e.getEntity().getLocation());
        if (i != null) {
            e.setCancelled(i.drapeau("auto-feed"));
            if (i.drapeau("auto-feed")) {
                if (e.getEntity() instanceof Player) {
                    ((Player) e.getEntity()).setFoodLevel(20);
                }
            }
        }
    }

    /** Equipement garde a la mort (drapeau "Drop stuff mort" desactive), rendu a la reapparition. */
    private final Map<String, org.bukkit.inventory.ItemStack[][]> stuffGarde = new HashMap<String, org.bukkit.inventory.ItemStack[][]>();

    @EventHandler(ignoreCancelled = true)
    public void mort(PlayerDeathEvent e) {
        Ile i = iles.ileA(e.getEntity().getLocation());
        if (i != null && !i.drapeau("items-death")) {
            e.setKeepLevel(true);
            e.setDroppedExp(0);
            e.getDrops().clear();
            stuffGarde.put(e.getEntity().getName(), new org.bukkit.inventory.ItemStack[][] {
                e.getEntity().getInventory().getContents().clone(), e.getEntity().getInventory().getArmorContents().clone()});
        }
    }

    @EventHandler
    public void reapparition(org.bukkit.event.player.PlayerRespawnEvent e) {
        final Player p = e.getPlayer();
        final org.bukkit.inventory.ItemStack[][] s = stuffGarde.remove(p.getName());
        if (s != null) {
            Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
                public void run() {
                    if (p.isOnline()) {
                        p.getInventory().setContents(s[0]);
                        p.getInventory().setArmorContents(s[1]);
                        p.updateInventory();
                    }
                }
            }, 2L);
        }
    }

    // ------------------------------------------------------------------ WorldEdit

    @EventHandler(priority = EventPriority.LOWEST)
    public void commande(PlayerCommandPreprocessEvent e) {
        String m = e.getMessage().toLowerCase();
        Player p = e.getPlayer();
        if (m.startsWith("//") || m.startsWith("/schem") || m.startsWith("/br") || m.startsWith("/brush") || m.startsWith("/sphere")
                || m.startsWith("/cyl") || m.startsWith("/pyramid") || m.startsWith("/forest") || m.startsWith("/snow")
                || m.startsWith("/thaw") || m.startsWith("/drain") || m.startsWith("/fixwater") || m.startsWith("/fixlava")
                || m.startsWith("/replacenear") || m.startsWith("/remove") || m.startsWith("/butcher") || m.startsWith("/ex")
                || m.startsWith("/fill") || m.startsWith("/green") || m.startsWith("/tool") || m.startsWith("/sp ")
                || m.startsWith("/awe")) {
            if ((m.startsWith("//gmask") || m.startsWith("/gmask")) && !SSNG.admin(p)) {
                e.setCancelled(true);
                SSNG.msg(p, "&c//gmask est reserve aux admins (le masque des iles est automatique).");
                return;
            }
            String refus = MasqueWorldEdit.refus(plugin, iles, p);
            if (refus != null) {
                e.setCancelled(true);
                SSNG.msg(p, "&c" + refus);
                return;
            }
            MasqueWorldEdit.appliquer(plugin, iles, p);
        }
    }
}
