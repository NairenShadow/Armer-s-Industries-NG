package fr.armersindustries.ssng.ponts.ponts;

/** Pont genere automatiquement : simule le plugin prive "NationsArcade2" de NationsGlory. */
public class NationsArcade2 extends org.bukkit.plugin.java.JavaPlugin {

    @Override
    public void onEnable() {
        fr.armersindustries.ssng.ponts.Regles.pontActif("NationsArcade2", 1);
    }

    public static Object canPlayerShootOnPlayer(java.lang.String a0, java.lang.String a1) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsArcade2", "canPlayerShootOnPlayer", "Boolean", new Object[] {a0, a1});
    }

}
