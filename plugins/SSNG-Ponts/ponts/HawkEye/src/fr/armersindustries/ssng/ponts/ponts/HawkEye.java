package fr.armersindustries.ssng.ponts.ponts;

/** Pont genere automatiquement : simule le plugin prive "HawkEye" de NationsGlory. */
public class HawkEye extends org.bukkit.plugin.java.JavaPlugin {

    @Override
    public void onEnable() {
        fr.armersindustries.ssng.ponts.Regles.pontActif("HawkEye", 3);
    }

    public static Object logInvDiff(int a0, int a1, int a2, java.util.HashMap a3, java.lang.String a4) {
        return fr.armersindustries.ssng.ponts.Regles.appel("HawkEye", "logInvDiff", "Void", new Object[] {a0, a1, a2, a3, a4});
    }

    public static Object logTransactionAsCustom(int a0, int a1, int a2, java.lang.String a3, java.lang.String a4, java.lang.String a5, java.lang.String a6) {
        return fr.armersindustries.ssng.ponts.Regles.appel("HawkEye", "logTransactionAsCustom", "Void", new Object[] {a0, a1, a2, a3, a4, a5, a6});
    }

    public static Object parseBlockInventory(int a0, int a1, int a2) {
        return fr.armersindustries.ssng.ponts.Regles.appel("HawkEye", "parseBlockInventory", "Map", new Object[] {a0, a1, a2});
    }

}
