package fr.armersindustries.ssng.ponts.ponts;

/** Pont genere automatiquement : simule le plugin prive "NationsUtils" de NationsGlory. */
public class NationsUtils extends org.bukkit.plugin.java.JavaPlugin {

    @Override
    public void onEnable() {
        fr.armersindustries.ssng.ponts.Regles.pontActif("NationsUtils", 27);
    }

    public static Object addBattlePassPoints(java.lang.String a0, java.lang.String a1) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsUtils", "addBattlePassPoints", "Null", new Object[] {a0, a1});
    }

    public static Object addBattlePassPointsForMultipleActions(java.lang.String a0, java.lang.String a1, int a2) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsUtils", "addBattlePassPointsForMultipleActions", "Null", new Object[] {a0, a1, a2});
    }

    public static Object addBlockBreakResearch(java.lang.String a0, int a1, int a2, java.lang.String a3) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsUtils", "addBlockBreakResearch", "Void", new Object[] {a0, a1, a2, a3});
    }

    public static Object addFactionResearchValue(java.lang.String a0, java.lang.String a1, java.lang.Double a2) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsUtils", "addFactionResearchValue", "Void", new Object[] {a0, a1, a2});
    }

    public static Object addItemFamilyResearch(java.lang.String a0, java.lang.String a1, int a2, int a3, double a4) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsUtils", "addItemFamilyResearch", "Void", new Object[] {a0, a1, a2, a3, a4});
    }

    public static Object canCropTick(java.lang.String a0, java.lang.String a1, int a2, int a3) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsUtils", "canCropTick", "Boolean", new Object[] {a0, a1, a2, a3});
    }

    public static Object canPlayerAccessMachineStorage(java.lang.String a0, int a1, int a2, int a3) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsUtils", "canPlayerAccessMachineStorage", "Boolean", new Object[] {a0, a1, a2, a3});
    }

    public static Object canPlayerBypassT3RDForReplique(java.lang.String a0, java.lang.String a1, int a2, int a3, int a4) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsUtils", "canPlayerBypassT3RDForReplique", "Boolean", new Object[] {a0, a1, a2, a3, a4});
    }

    public static Object canPlayerInteractBlock(java.lang.String a0, int a1, int a2, int a3) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsUtils", "canPlayerInteractBlock", "Boolean", new Object[] {a0, a1, a2, a3});
    }

    public static Object canPlayerInteractVehicle(java.lang.String a0, java.lang.String a1, double a2, double a3, double a4) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsUtils", "canPlayerInteractVehicle", "Boolean", new Object[] {a0, a1, a2, a3, a4});
    }

    public static Object canPlayerOpenMachine(java.lang.String a0, int a1, int a2, int a3) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsUtils", "canPlayerOpenMachine", "Boolean", new Object[] {a0, a1, a2, a3});
    }

    public static Object canPlayerPlaceLiquid(java.lang.String a0, int a1, int a2, int a3, boolean a4) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsUtils", "canPlayerPlaceLiquid", "Boolean", new Object[] {a0, a1, a2, a3, a4});
    }

    public static Object canPlayerShootOnPlayer(java.lang.String a0, java.lang.String a1) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsUtils", "canPlayerShootOnPlayer", "Boolean", new Object[] {a0, a1});
    }

    public static Object canPlayerUseVehicle(java.lang.String a0, java.lang.String a1, boolean a2, boolean a3) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsUtils", "canPlayerUseVehicle", "Boolean", new Object[] {a0, a1, a2, a3});
    }

    public static Object currentValue() {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsUtils", "currentValue", "Double", new Object[] {});
    }

    public static Object decreaseCorruption(java.lang.String a0, java.lang.String a1) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsUtils", "decreaseCorruption", "Null", new Object[] {a0, a1});
    }

    public static Object getCorruptionInfo() {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsUtils", "getCorruptionInfo", "Null", new Object[] {});
    }

    public static Object getCountryLastBuiltSurface(java.lang.String a0) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsUtils", "getCountryLastBuiltSurface", "Double", new Object[] {a0});
    }

    public static Object getCountryResearchLevel(java.lang.String a0, java.lang.String a1) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsUtils", "getCountryResearchLevel", "Integer", new Object[] {a0, a1});
    }

    public static Object getExpToGive(java.lang.String a0, int a1) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsUtils", "getExpToGive", "Integer", new Object[] {a0, a1});
    }

    public static Object getPlayerRegionAuthorization(java.lang.String a0, org.bukkit.Location a1) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsUtils", "getPlayerRegionAuthorization", "String", new Object[] {a0, a1});
    }

    public static Object playerGhostTransaction(java.lang.String a0, int a1) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsUtils", "playerGhostTransaction", "Void", new Object[] {a0, a1});
    }

    public static Object processVoriackDeath() {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsUtils", "processVoriackDeath", "Void", new Object[] {});
    }

    public static Object removePlayerBadge(java.lang.String a0, java.lang.String a1) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsUtils", "removePlayerBadge", "Void", new Object[] {a0, a1});
    }

    public static Object repairVehicle(java.lang.String a0, int a1) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsUtils", "repairVehicle", "Integer", new Object[] {a0, a1});
    }

    public static Object withdrawGasInCoords(java.lang.String a0, int a1, int a2, int a3) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsUtils", "withdrawGasInCoords", "Float", new Object[] {a0, a1, a2, a3});
    }

    public static Object withdrawPetrolInCoords(java.lang.String a0, int a1, int a2, int a3) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsUtils", "withdrawPetrolInCoords", "Float", new Object[] {a0, a1, a2, a3});
    }

}
