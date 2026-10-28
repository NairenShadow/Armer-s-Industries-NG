package fr.armersindustries.ssng.ponts.ponts;

/** Pont genere automatiquement : simule le plugin prive "NationsBattle" de NationsGlory. */
public class NationsBattle extends org.bukkit.plugin.java.JavaPlugin {

    @Override
    public void onEnable() {
        fr.armersindustries.ssng.ponts.Regles.pontActif("NationsBattle", 1);
    }

    public static Object getAssaultAtPosition(int a0, int a1) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsBattle", "getAssaultAtPosition", "Null", new Object[] {a0, a1});
    }

}
