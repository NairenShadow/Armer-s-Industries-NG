package fr.armersindustries.ssng.ponts.ponts;

/** Pont genere automatiquement : simule le plugin prive "NationsToxicity" de NationsGlory. */
public class NationsToxicity extends org.bukkit.plugin.java.JavaPlugin {

    @Override
    public void onEnable() {
        fr.armersindustries.ssng.ponts.Regles.pontActif("NationsToxicity", 3);
    }

    public static Object getMalusValue(java.lang.String a0, java.lang.String a1, double a2) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsToxicity", "getMalusValue", "Null", new Object[] {a0, a1, a2});
    }

    public static Object getToxicityTierNumber(java.lang.String a0) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsToxicity", "getToxicityTierNumber", "Null", new Object[] {a0});
    }

    public static Object hasMalus(java.lang.String a0, java.lang.String a1) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsToxicity", "hasMalus", "Boolean", new Object[] {a0, a1});
    }

}
