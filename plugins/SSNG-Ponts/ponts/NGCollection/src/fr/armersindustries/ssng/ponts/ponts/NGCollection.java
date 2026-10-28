package fr.armersindustries.ssng.ponts.ponts;

/** Pont genere automatiquement : simule le plugin prive "NGCollection" de NationsGlory. */
public class NGCollection extends org.bukkit.plugin.java.JavaPlugin {

    @Override
    public void onEnable() {
        fr.armersindustries.ssng.ponts.Regles.pontActif("NGCollection", 1);
    }

    public static Object getTextureIndex() {
        return fr.armersindustries.ssng.ponts.Regles.appel("NGCollection", "getTextureIndex", "Null", new Object[] {});
    }

}
