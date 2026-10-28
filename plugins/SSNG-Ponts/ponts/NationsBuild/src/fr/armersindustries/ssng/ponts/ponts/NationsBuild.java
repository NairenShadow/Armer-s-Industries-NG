package fr.armersindustries.ssng.ponts.ponts;

/** Pont genere automatiquement : simule le plugin prive "NationsBuild" de NationsGlory. */
public class NationsBuild extends org.bukkit.plugin.java.JavaPlugin {

    @Override
    public void onEnable() {
        fr.armersindustries.ssng.ponts.Regles.pontActif("NationsBuild", 9);
    }

    public static Object canPlayerShootOnPlayer(java.lang.String a0, java.lang.String a1) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsBuild", "canPlayerShootOnPlayer", "Boolean", new Object[] {a0, a1});
    }

    public static Object captureZone(java.lang.String a0, java.util.ArrayList a1) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsBuild", "captureZone", "Void", new Object[] {a0, a1});
    }

    public static Object changePlateTeam(java.lang.String a0, int a1, int a2, int a3, java.lang.String a4, boolean a5) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsBuild", "changePlateTeam", "String", new Object[] {a0, a1, a2, a3, a4, a5});
    }

    public static Object checkPermission(java.lang.String a0, java.lang.String a1, java.lang.String a2) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsBuild", "checkPermission", "Boolean", new Object[] {a0, a1, a2});
    }

    public static Object footGetScores(java.lang.String a0) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsBuild", "footGetScores", "Map", new Object[] {a0});
    }

    public static Object footGoal(java.lang.String a0, int a1, int a2, int a3, java.util.List a4, java.lang.String a5) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsBuild", "footGoal", "Void", new Object[] {a0, a1, a2, a3, a4, a5});
    }

    public static Object isMissileAuthorized(java.lang.String a0, java.lang.String a1, int a2, int a3, int a4, int a5) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsBuild", "isMissileAuthorized", "Boolean", new Object[] {a0, a1, a2, a3, a4, a5});
    }

    public static Object playerJoinTeam(java.lang.String a0, java.lang.String a1, java.lang.String a2) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsBuild", "playerJoinTeam", "Void", new Object[] {a0, a1, a2});
    }

    public static Object removePlateTeam(java.lang.String a0, int a1, int a2, int a3, java.lang.String a4) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsBuild", "removePlateTeam", "String", new Object[] {a0, a1, a2, a3, a4});
    }

}
