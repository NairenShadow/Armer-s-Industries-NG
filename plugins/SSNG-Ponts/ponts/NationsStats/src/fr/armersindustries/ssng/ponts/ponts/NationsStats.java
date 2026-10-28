package fr.armersindustries.ssng.ponts.ponts;

/** Pont genere automatiquement : simule le plugin prive "NationsStats" de NationsGlory. */
public class NationsStats extends org.bukkit.plugin.java.JavaPlugin {

    @Override
    public void onEnable() {
        fr.armersindustries.ssng.ponts.Regles.pontActif("NationsStats", 1);
    }

    public static Object getPlayerAchievements(java.lang.String a0) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsStats", "getPlayerAchievements", "List", new Object[] {a0});
    }

}
