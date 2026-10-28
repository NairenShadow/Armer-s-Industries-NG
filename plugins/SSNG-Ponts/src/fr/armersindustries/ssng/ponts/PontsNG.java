package fr.armersindustries.ssng.ponts;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * PontsNG : remplace les plugins prives du serveur officiel NationsGlory
 * (NationsGUI, NationsUtils, ServerBridge, NationsBuild...) dont les mods ont besoin.
 * Les "ponts" (un petit plugin par nom) delegent tous a {@link Regles}.
 */
public class PontsNG extends JavaPlugin {

    @Override
    public void onLoad() {
        Regles.init(this);
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Regles.init(this);
        getLogger().info("PontsNG actif : missiles autorises dans " + getConfig().getStringList("mondes-missiles")
                + ", combat dans " + getConfig().getStringList("mondes-combat") + ".");
    }

    boolean journal() {
        return getConfig().getBoolean("journal.actif", true);
    }

    boolean journalTout() {
        return getConfig().getBoolean("journal.tout", false);
    }

    long journalMax() {
        return getConfig().getLong("journal.premiers-appels", 3);
    }

    /** Valeur forcee dans config.yml : reponses.NationsGUI.canPlayerInteractBlock: true */
    Object reponseForcee(String pont, String methode) {
        ConfigurationSection s = getConfig().getConfigurationSection("reponses." + pont);
        return s == null ? null : s.get(methode);
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (a.length > 0 && a[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            s.sendMessage("§aNGCompat : configuration rechargee.");
            return true;
        }
        if (a.length > 0 && a[0].equalsIgnoreCase("journal")) {
            boolean v = !journal();
            getConfig().set("journal.actif", v);
            saveConfig();
            s.sendMessage("§aNGCompat : journal des appels " + (v ? "active" : "desactive") + ".");
            return true;
        }
        // statistiques des appels
        List<Map.Entry<String, AtomicLong>> l = new ArrayList<Map.Entry<String, AtomicLong>>(Regles.compteurs().entrySet());
        java.util.Collections.sort(l, new java.util.Comparator<Map.Entry<String, AtomicLong>>() {
            public int compare(Map.Entry<String, AtomicLong> x, Map.Entry<String, AtomicLong> y) {
                return Long.valueOf(y.getValue().get()).compareTo(x.getValue().get());
            }
        });
        s.sendMessage("§6NGCompat : " + l.size() + " fonctions appelees par les mods depuis le demarrage :");
        for (int i = 0; i < l.size() && i < 25; i++) {
            s.sendMessage("§7 " + l.get(i).getValue().get() + "x §f" + l.get(i).getKey());
        }
        s.sendMessage("§7/ponts journal | reload");
        return true;
    }
}
