package fr.armersindustries.ssng.ponts.ponts;

/** Pont genere automatiquement : simule le plugin prive "NationsEvent" de NationsGlory. */
public class NationsEvent extends org.bukkit.plugin.java.JavaPlugin {

    @Override
    public void onEnable() {
        fr.armersindustries.ssng.ponts.Regles.pontActif("NationsEvent", 1);
    }

    public static Object savePlayerCollectEventPoint(java.lang.String a0, int a1, int a2, int a3, java.lang.String a4, java.lang.String a5) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsEvent", "savePlayerCollectEventPoint", "Boolean", new Object[] {a0, a1, a2, a3, a4, a5});
    }

}
