package fr.armersindustries.ssng.ponts.ponts;

/** Pont genere automatiquement : simule le plugin prive "NationsBox" de NationsGlory. */
public class NationsBox extends org.bukkit.plugin.java.JavaPlugin {

    @Override
    public void onEnable() {
        fr.armersindustries.ssng.ponts.Regles.pontActif("NationsBox", 1);
    }

    public static Object getLootFromLootboxName(java.lang.String a0, java.lang.String a1, java.lang.String a2) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsBox", "getLootFromLootboxName", "Json", new Object[] {a0, a1, a2});
    }

}
