package fr.armersindustries.ssng.ponts.ponts;

/** Pont genere automatiquement : simule le plugin prive "ServerBridge" de NationsGlory. */
public class ServerBridge extends org.bukkit.plugin.java.JavaPlugin {

    @Override
    public void onEnable() {
        fr.armersindustries.ssng.ponts.Regles.pontActif("ServerBridge", 7);
    }

    public static Object getRemainingMissilePointsForPlayer(java.lang.String a0) {
        return fr.armersindustries.ssng.ponts.Regles.appel("ServerBridge", "getRemainingMissilePointsForPlayer", "Integer", new Object[] {a0});
    }

    public static Object informMissileDestruction(java.lang.String a0, java.lang.String a1, int a2, int a3, int a4, int a5, boolean a6) {
        return fr.armersindustries.ssng.ponts.Regles.appel("ServerBridge", "informMissileDestruction", "Void", new Object[] {a0, a1, a2, a3, a4, a5, a6});
    }

    public static Object missileCanExplodeAt(java.lang.String a0, java.lang.String a1, int a2, int a3, int a4, int a5, boolean a6, int a7) {
        return fr.armersindustries.ssng.ponts.Regles.appel("ServerBridge", "missileCanExplodeAt", "Boolean", new Object[] {a0, a1, a2, a3, a4, a5, a6, a7});
    }

    public static Object missileCanLaunchAt(java.lang.String a0, java.lang.String a1, int a2, int a3, int a4, int a5, boolean a6, int a7) {
        return fr.armersindustries.ssng.ponts.Regles.appel("ServerBridge", "missileCanLaunchAt", "Boolean", new Object[] {a0, a1, a2, a3, a4, a5, a6, a7});
    }

    public static Object missileIsAuthorizedArmorAbility(java.lang.String a0, int a1, int a2, int a3, int a4) {
        return fr.armersindustries.ssng.ponts.Regles.appel("ServerBridge", "missileIsAuthorizedArmorAbility", "Boolean", new Object[] {a0, a1, a2, a3, a4});
    }

    public static Object tagPlayerUserShoot(java.lang.String a0) {
        return fr.armersindustries.ssng.ponts.Regles.appel("ServerBridge", "tagPlayerUserShoot", "Void", new Object[] {a0});
    }

    public static Object turretCanDestroyMissile(java.lang.String a0, java.lang.String a1, int a2, int a3, int a4, java.lang.String a5) {
        return fr.armersindustries.ssng.ponts.Regles.appel("ServerBridge", "turretCanDestroyMissile", "Boolean", new Object[] {a0, a1, a2, a3, a4, a5});
    }

}
