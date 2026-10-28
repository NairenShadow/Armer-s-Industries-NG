package fr.armersindustries.ssng.iles;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.LocalPlayer;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.Vector;
import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldedit.masks.Mask;

import fr.armersindustries.ssng.core.SSNG;
import fr.armersindustries.ssng.core.ReseauNG;

/**
 * Masque WorldEdit applique a chaque joueur : une modification n'est permise
 * que sur les blocs ou le joueur a le droit d'utiliser WorldEdit.
 * - monde Build : seulement sur ses propres iles (proprietaire) et avec le grade Builder ou plus ;
 * - monde Test  : s'il a shadow.test ;
 * - autres mondes : admins seulement.
 * Les admins (shadow.admin) n'ont pas de masque.
 */
public class MasqueWorldEdit implements Mask {

    private final IlesNG plugin;
    private final GestionIles iles;
    private final String joueur;

    public MasqueWorldEdit(IlesNG plugin, GestionIles iles, String joueur) {
        this.plugin = plugin;
        this.iles = iles;
        this.joueur = joueur;
    }

    public void prepare(LocalSession session, LocalPlayer player, Vector target) {
    }

    public boolean matches(EditSession editSession, Vector pos) {
        Player p = Bukkit.getPlayerExact(joueur);
        if (p == null) {
            return false;
        }
        World w = Bukkit.getWorld(editSession.getWorld().getName());
        if (w == null) {
            return false;
        }
        IlesNG.TypeMonde t = plugin.type(w);
        if (t == IlesNG.TypeMonde.BUILD) {
            Ile i = iles.ileA(new Location(w, pos.getBlockX(), pos.getBlockY(), pos.getBlockZ()));
            return i != null && i.estCreateur(p.getName()) && gradeOk(plugin, p);
        }
        if (t == IlesNG.TypeMonde.TEST) {
            return p.hasPermission("ssng.test");
        }
        return false;
    }

    /** Grade minimum pour WorldEdit sur son ile (iles.worldedit-grade, Builder par defaut). */
    public static boolean gradeOk(IlesNG plugin, Player p) {
        return plugin.grades().auMoins(p, plugin.getConfig().getString("iles.worldedit-grade", "Builder"));
    }

    /** Le joueur peut-il utiliser WorldEdit la ou il est ? null si oui, sinon la raison. */
    public static String refus(IlesNG plugin, GestionIles iles, Player p) {
        if (SSNG.admin(p)) {
            return null;
        }
        IlesNG.TypeMonde t = plugin.type(p.getWorld());
        if (t == IlesNG.TypeMonde.TEST) {
            return p.hasPermission("ssng.test") ? null : "WorldEdit est reserve aux testeurs dans ce monde.";
        }
        if (t == IlesNG.TypeMonde.BUILD) {
            if (!gradeOk(plugin, p)) {
                return "WorldEdit est reserve au grade " + plugin.getConfig().getString("iles.worldedit-grade", "Builder") + " (sur ton ile).";
            }
            for (Ile i : iles.ilesDe(p.getName())) {
                if (i.estCreateur(p.getName())) {
                    return null;
                }
            }
            return "WorldEdit ne marche que sur une ile dont tu es le proprietaire.";
        }
        return "WorldEdit est reserve aux admins dans ce monde.";
    }

    /** Pose (ou retire) le masque sur la session WorldEdit du joueur. */
    public static void appliquer(IlesNG plugin, GestionIles iles, Player p) {
        org.bukkit.plugin.Plugin we = Bukkit.getPluginManager().getPlugin("WorldEdit");
        if (!(we instanceof WorldEditPlugin)) {
            return;
        }
        LocalSession s = ((WorldEditPlugin) we).getSession(p);
        if (s == null) {
            return;
        }
        if (SSNG.admin(p)) {
            if (s.getMask() instanceof MasqueWorldEdit) {
                s.setMask(null);
            }
        } else if (!(s.getMask() instanceof MasqueWorldEdit)) {
            s.setMask(new MasqueWorldEdit(plugin, iles, p.getName()));
        }
    }
}
