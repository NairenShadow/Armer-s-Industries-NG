package fr.armersindustries.ssng.ponts.ponts;

/** Pont genere automatiquement : simule le plugin prive "NationsAlliance" de NationsGlory. */
public class NationsAlliance extends org.bukkit.plugin.java.JavaPlugin {

    @Override
    public void onEnable() {
        fr.armersindustries.ssng.ponts.Regles.pontActif("NationsAlliance", 5);
    }

    public static Object addAlliancePrestige(java.lang.String a0, java.lang.String a1) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsAlliance", "addAlliancePrestige", "Void", new Object[] {a0, a1});
    }

    public static Object getEnergy() {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsAlliance", "getEnergy", "Null", new Object[] {});
    }

    public static Object getInstance() {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsAlliance", "getInstance", "List", new Object[] {});
    }

    public static Object getInstance(double a0, double a1, double a2) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsAlliance", "getInstance", "Null", new Object[] {a0, a1, a2});
    }

    public static Object getInstance(java.util.UUID a0) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsAlliance", "getInstance", "Null", new Object[] {a0});
    }

}
