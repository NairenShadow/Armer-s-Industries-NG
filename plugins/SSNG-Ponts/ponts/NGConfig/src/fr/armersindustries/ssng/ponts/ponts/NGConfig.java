package fr.armersindustries.ssng.ponts.ponts;

/** Pont genere automatiquement : simule le plugin prive "NGConfig" de NationsGlory. */
public class NGConfig extends org.bukkit.plugin.java.JavaPlugin {

    @Override
    public void onEnable() {
        fr.armersindustries.ssng.ponts.Regles.pontActif("NGConfig", 1);
    }

    public static Object saveRagdollItemGrip(java.lang.String a0, java.lang.String a1) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NGConfig", "saveRagdollItemGrip", "Boolean", new Object[] {a0, a1});
    }

}
