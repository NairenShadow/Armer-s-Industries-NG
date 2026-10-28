package fr.armersindustries.ssng.ponts.ponts;

/** Pont genere automatiquement : simule le plugin prive "Factions" de NationsGlory. */
public class Factions extends org.bukkit.plugin.java.JavaPlugin {

    @Override
    public void onEnable() {
        fr.armersindustries.ssng.ponts.Regles.pontActif("Factions", 6);
    }

    public static Object get(java.lang.Object a0) {
        return fr.armersindustries.ssng.ponts.Regles.appel("Factions", "get", "Null", new Object[] {a0});
    }

    public static Object get(java.lang.Object a0, double a1, long a2) {
        return fr.armersindustries.ssng.ponts.Regles.appel("Factions", "get", "Void", new Object[] {a0, a1, a2});
    }

    public static Object getFactionFromPlayer(java.lang.String a0) {
        return fr.armersindustries.ssng.ponts.Regles.appel("Factions", "getFactionFromPlayer", "String", new Object[] {a0});
    }

    public static Object getFlagFromName(java.lang.String a0) {
        return fr.armersindustries.ssng.ponts.Regles.appel("Factions", "getFlagFromName", "String", new Object[] {a0});
    }

    public static Object onChunkCopyComplete(java.lang.String a0, java.lang.String a1) {
        return fr.armersindustries.ssng.ponts.Regles.appel("Factions", "onChunkCopyComplete", "Void", new Object[] {a0, a1});
    }

    public static Object onChunkCopyComplete(java.lang.String a0, java.lang.String a1, java.lang.String a2) {
        return fr.armersindustries.ssng.ponts.Regles.appel("Factions", "onChunkCopyComplete", "Void", new Object[] {a0, a1, a2});
    }

}
