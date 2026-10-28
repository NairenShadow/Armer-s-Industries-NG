package fr.armersindustries.ssng.ponts.ponts;

/** Pont genere automatiquement : simule le plugin prive "NationsJob" de NationsGlory. */
public class NationsJob extends org.bukkit.plugin.java.JavaPlugin {

    @Override
    public void onEnable() {
        fr.armersindustries.ssng.ponts.Regles.pontActif("NationsJob", 11);
    }

    public static Object addPlayerSkill(java.lang.String a0, java.lang.String a1, int a2) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsJob", "addPlayerSkill", "Void", new Object[] {a0, a1, a2});
    }

    public static Object addTraderPoint(java.lang.String a0, int a1) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsJob", "addTraderPoint", "Void", new Object[] {a0, a1});
    }

    public static Object canPlayerHaveDoubleLoot(java.lang.String a0, java.lang.String a1) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsJob", "canPlayerHaveDoubleLoot", "Boolean", new Object[] {a0, a1});
    }

    public static Object canPlayerUseEnchant(java.lang.String a0, int a1) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsJob", "canPlayerUseEnchant", "Boolean", new Object[] {a0, a1});
    }

    public static Object getPlayerSkillLevel(java.lang.String a0, java.lang.String a1) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsJob", "getPlayerSkillLevel", "Integer", new Object[] {a0, a1});
    }

    public static Object getPlayerSkillsInfos(java.lang.String a0) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsJob", "getPlayerSkillsInfos", "Map", new Object[] {a0});
    }

    public static Object getSkillNumericEffect(java.lang.String a0, java.lang.String a1, double a2) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsJob", "getSkillNumericEffect", "Null", new Object[] {a0, a1, a2});
    }

    public static Object getTopPlayersSkills() {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsJob", "getTopPlayersSkills", "Map", new Object[] {});
    }

    public static Object grantSkillXpFromSourceAllSkills(java.lang.String a0, java.lang.String a1, java.lang.String a2, int a3) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsJob", "grantSkillXpFromSourceAllSkills", "Void", new Object[] {a0, a1, a2, a3});
    }

    public static Object storeEnergy(java.lang.String a0, int a1) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsJob", "storeEnergy", "Void", new Object[] {a0, a1});
    }

    public static Object withdrawEnergy(java.lang.String a0, int a1, int a2, int a3) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsJob", "withdrawEnergy", "Float", new Object[] {a0, a1, a2, a3});
    }

}
