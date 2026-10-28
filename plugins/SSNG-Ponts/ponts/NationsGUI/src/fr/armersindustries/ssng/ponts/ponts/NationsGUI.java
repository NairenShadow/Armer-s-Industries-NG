package fr.armersindustries.ssng.ponts.ponts;

/** Pont genere automatiquement : simule le plugin prive "NationsGUI" de NationsGlory. */
public class NationsGUI extends org.bukkit.plugin.java.JavaPlugin {

    @Override
    public void onEnable() {
        fr.armersindustries.ssng.ponts.Regles.pontActif("NationsGUI", 93);
    }

    public static Object addCountryFactionLogs(java.lang.String a0, java.util.List a1) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "addCountryFactionLogs", "Void", new Object[] {a0, a1});
    }

    public static Object addEventPoint(java.lang.String a0, int a1, int a2, int a3, java.lang.String a4) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "addEventPoint", "Null", new Object[] {a0, a1, a2, a3, a4});
    }

    public static Object addHalloweenPoints(java.lang.String a0, java.lang.String a1, java.lang.Integer a2) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "addHalloweenPoints", "Void", new Object[] {a0, a1, a2});
    }

    public static Object addMissingTranslation(java.lang.String a0) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "addMissingTranslation", "Void", new Object[] {a0});
    }

    public static Object addMoney(java.lang.String a0, int a1) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "addMoney", "Void", new Object[] {a0, a1});
    }

    public static Object addSkinToPlayer(java.lang.String a0, java.lang.String a1) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "addSkinToPlayer", "Void", new Object[] {a0, a1});
    }

    public static Object applyFagTicketValidity(java.lang.String a0) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "applyFagTicketValidity", "Void", new Object[] {a0});
    }

    public static Object applySurcharge(java.lang.String a0, java.lang.String a1, int a2) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "applySurcharge", "Void", new Object[] {a0, a1, a2});
    }

    public static Object applyToxicityTax(java.lang.String a0, int a1) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "applyToxicityTax", "Integer", new Object[] {a0, a1});
    }

    public static Object arePlayersFriendly(java.lang.String a0, java.lang.String a1) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "arePlayersFriendly", "Boolean", new Object[] {a0, a1});
    }

    public static Object arePlayersFriendlyByName(java.lang.String a0, java.lang.String a1) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "arePlayersFriendlyByName", "Boolean", new Object[] {a0, a1});
    }

    public static Object calculateSurcharge(java.lang.String a0, java.lang.String a1, int a2) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "calculateSurcharge", "Double", new Object[] {a0, a1, a2});
    }

    public static Object canPlayerCombatPlayer(java.lang.String a0, java.lang.String a1) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "canPlayerCombatPlayer", "Boolean", new Object[] {a0, a1});
    }

    public static Object canPlayerContractContagious(java.lang.String a0) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "canPlayerContractContagious", "Boolean", new Object[] {a0});
    }

    public static Object canPlayerInteractBlock(java.lang.String a0, int a1, int a2, int a3) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "canPlayerInteractBlock", "Boolean", new Object[] {a0, a1, a2, a3});
    }

    public static Object canPlayerModifyBlock(java.lang.String a0, int a1, int a2, int a3, java.lang.String a4) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "canPlayerModifyBlock", "Boolean", new Object[] {a0, a1, a2, a3, a4});
    }

    public static Object canPlayerOpenLootbox(java.lang.String a0, int a1, int a2, int a3, java.lang.String a4) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "canPlayerOpenLootbox", "Boolean", new Object[] {a0, a1, a2, a3, a4});
    }

    public static Object canPlayerOpenPostbox(java.lang.String a0, java.lang.String a1) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "canPlayerOpenPostbox", "Boolean", new Object[] {a0, a1});
    }

    public static Object canPlayerPlaceBlock(java.lang.String a0, int a1, int a2, int a3, java.lang.String a4) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "canPlayerPlaceBlock", "Boolean", new Object[] {a0, a1, a2, a3, a4});
    }

    public static Object canPlayerPlaceTag(java.lang.String a0, int a1, int a2, int a3, java.lang.String a4) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "canPlayerPlaceTag", "Boolean", new Object[] {a0, a1, a2, a3, a4});
    }

    public static Object canPlayerUseSpecialAttack(java.lang.String a0, int a1, int a2, int a3, java.lang.String a4) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "canPlayerUseSpecialAttack", "Boolean", new Object[] {a0, a1, a2, a3, a4});
    }

    public static Object checkCanOpenMarket(java.lang.String a0, boolean a1) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "checkCanOpenMarket", "String", new Object[] {a0, a1});
    }

    public static Object checkCanOpenMarketByType(java.lang.String a0, java.lang.String a1) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "checkCanOpenMarketByType", "String", new Object[] {a0, a1});
    }

    public static Object checkInCombatOrAssault(java.lang.String a0) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "checkInCombatOrAssault", "String", new Object[] {a0});
    }

    public static Object checkPermission(java.lang.String a0, java.lang.String a1) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "checkPermission", "Boolean", new Object[] {a0, a1});
    }

    public static Object dealBukkitDamage(java.lang.String a0, double a1) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "dealBukkitDamage", "Boolean", new Object[] {a0, a1});
    }

    public static Object decideCenter(java.lang.String a0, int a1, int a2) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "decideCenter", "Boolean", new Object[] {a0, a1, a2});
    }

    public static Object depositPlayer(java.lang.String a0, double a1) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "depositPlayer", "Void", new Object[] {a0, a1});
    }

    public static Object dispatchConsoleCommand(java.lang.String a0) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "dispatchConsoleCommand", "Void", new Object[] {a0});
    }

    public static Object economyAddPendingValue(java.lang.String a0, int a1) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "economyAddPendingValue", "Void", new Object[] {a0, a1});
    }

    public static Object executeNpcCommand(java.lang.String a0, java.lang.String a1) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "executeNpcCommand", "Boolean", new Object[] {a0, a1});
    }

    public static Object executePlayerCmd(java.lang.String a0, java.lang.String a1) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "executePlayerCmd", "Void", new Object[] {a0, a1});
    }

    public static Object findChestsForPlayer(java.lang.String a0, java.lang.String a1, int a2) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "findChestsForPlayer", "Void", new Object[] {a0, a1, a2});
    }

    public static Object getActiveSkinOfPlayer(java.lang.String a0, java.lang.String a1) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "getActiveSkinOfPlayer", "String", new Object[] {a0, a1});
    }

    public static Object getEventPoints(java.lang.String a0) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "getEventPoints", "Null", new Object[] {a0});
    }

    public static Object getFactionHomeInWorld(java.lang.String a0, java.lang.String a1) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "getFactionHomeInWorld", "String", new Object[] {a0, a1});
    }

    public static Object getFactionIdAtCoordonates(java.lang.String a0, int a1, int a2, int a3) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "getFactionIdAtCoordonates", "String", new Object[] {a0, a1, a2, a3});
    }

    public static Object getFactionNameFromId(java.lang.String a0) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "getFactionNameFromId", "String", new Object[] {a0});
    }

    public static Object getModuleConstructionInfo(java.lang.String a0, int a1, int a2, int a3, int a4) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "getModuleConstructionInfo", "Tab", new Object[] {a0, a1, a2, a3, a4});
    }

    public static Object getMoney(java.lang.String a0) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "getMoney", "Double", new Object[] {a0});
    }

    public static Object getNoelFactoryRegionBounds(java.lang.String a0) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "getNoelFactoryRegionBounds", "Null", new Object[] {a0});
    }

    public static Object getPlayerRenderDistance(java.lang.String a0) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "getPlayerRenderDistance", "Integer", new Object[] {a0});
    }

    public static Object getServerType() {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "getServerType", "String", new Object[] {});
    }

    public static Object getServerType(java.lang.String a0) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "getServerType", "Integer", new Object[] {a0});
    }

    public static Object getToxicityTax(java.lang.String a0) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "getToxicityTax", "Double", new Object[] {a0});
    }

    public static Object getTradeSurchargeInfo(java.lang.String a0, java.lang.String a1) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "getTradeSurchargeInfo", "Tab", new Object[] {a0, a1});
    }

    public static Object getTranslationLang() {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "getTranslationLang", "String", new Object[] {});
    }

    public static Object getTranslations() {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "getTranslations", "Map", new Object[] {});
    }

    public static Object hasEmbargo(java.lang.String a0, java.lang.String a1) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "hasEmbargo", "Boolean", new Object[] {a0, a1});
    }

    public static Object hasMoney(java.lang.String a0, long a1) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "hasMoney", "Boolean", new Object[] {a0, a1});
    }

    public static Object hasNonGlobalWorldguardRegionAt(java.lang.String a0, int a1, int a2, int a3) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "hasNonGlobalWorldguardRegionAt", "Boolean", new Object[] {a0, a1, a2, a3});
    }

    public static Object hasPermissionPlayer(java.lang.String a0, java.lang.String a1) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "hasPermissionPlayer", "Boolean", new Object[] {a0, a1});
    }

    public static Object hasPlayerIslandPermission(java.lang.String a0, java.lang.String a1) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "hasPlayerIslandPermission", "Boolean", new Object[] {a0, a1});
    }

    public static Object hasPlayersInNoelFactoryRegion(java.lang.String a0) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "hasPlayersInNoelFactoryRegion", "Boolean", new Object[] {a0});
    }

    public static Object isCountryClaimAt(java.lang.String a0, int a1, int a2, int a3) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "isCountryClaimAt", "Boolean", new Object[] {a0, a1, a2, a3});
    }

    public static Object isEntryDenied(java.lang.String a0, double a1, double a2, double a3) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "isEntryDenied", "Boolean", new Object[] {a0, a1, a2, a3});
    }

    public static Object isExitDenied(java.lang.String a0, double a1, double a2, double a3) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "isExitDenied", "Boolean", new Object[] {a0, a1, a2, a3});
    }

    public static Object isInExtractionZone(java.lang.String a0, int a1, int a2, int a3) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "isInExtractionZone", "Boolean", new Object[] {a0, a1, a2, a3});
    }

    public static Object isMachineOnMagneticArea(java.lang.String a0, java.lang.Integer a1, java.lang.Integer a2, java.lang.Integer a3) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "isMachineOnMagneticArea", "Long", new Object[] {a0, a1, a2, a3});
    }

    public static Object isPlayerGod(java.lang.String a0) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "isPlayerGod", "Boolean", new Object[] {a0});
    }

    public static Object isPlayerImmuniseFromMissile(java.lang.String a0) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "isPlayerImmuniseFromMissile", "Boolean", new Object[] {a0});
    }

    public static Object isPlayerInAssault(java.lang.String a0, java.lang.Integer a1, java.lang.Integer a2) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "isPlayerInAssault", "Boolean", new Object[] {a0, a1, a2});
    }

    public static Object isPlayerInCombat(java.lang.String a0) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "isPlayerInCombat", "Boolean", new Object[] {a0});
    }

    public static Object isWildernessAt(java.lang.String a0, int a1, int a2, int a3) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "isWildernessAt", "Boolean", new Object[] {a0, a1, a2, a3});
    }

    public static Object logElectricToolBlockBreak(java.lang.String a0, int a1, int a2, java.lang.String a3, int a4, int a5, int a6) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "logElectricToolBlockBreak", "Void", new Object[] {a0, a1, a2, a3, a4, a5, a6});
    }

    public static Object logTombeExpired(java.lang.String a0, java.lang.String a1, java.lang.String a2, int a3, int a4, int a5, java.lang.Object[] a6) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "logTombeExpired", "Void", new Object[] {a0, a1, a2, a3, a4, a5, a6});
    }

    public static Object logTombeRecovery(java.lang.String a0, java.lang.String a1, java.lang.String a2, int a3, int a4, java.lang.String a5, int a6, int a7, int a8) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "logTombeRecovery", "Void", new Object[] {a0, a1, a2, a3, a4, a5, a6, a7, a8});
    }

    public static Object ngCoinsPriceFor(java.lang.String a0, int a1) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "ngCoinsPriceFor", "Integer", new Object[] {a0, a1});
    }

    public static Object openLootboxForPlayer(java.lang.String a0, java.lang.String a1, int a2, int a3, int a4, java.lang.String a5, boolean a6) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "openLootboxForPlayer", "Void", new Object[] {a0, a1, a2, a3, a4, a5, a6});
    }

    public static Object payNGCoins(java.lang.String a0, int a1, java.lang.String a2, java.lang.Runnable a3) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "payNGCoins", "Boolean", new Object[] {a0, a1, a2, a3});
    }

    public static Object playerCanShootLaseSpartan(java.lang.String a0) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "playerCanShootLaseSpartan", "Boolean", new Object[] {a0});
    }

    public static Object playerHasNGPrime(java.lang.String a0) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "playerHasNGPrime", "Boolean", new Object[] {a0});
    }

    public static Object playerHasPremiumPlus(java.lang.String a0) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "playerHasPremiumPlus", "Boolean", new Object[] {a0});
    }

    public static Object playerHasSkinSync(java.lang.String a0, java.lang.String a1) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "playerHasSkinSync", "Boolean", new Object[] {a0, a1});
    }

    public static Object refreshBadgesSite(java.lang.String a0, java.lang.String a1) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "refreshBadgesSite", "Void", new Object[] {a0, a1});
    }

    public static Object registerBlockExplosion(java.lang.String a0, java.lang.Integer a1, java.lang.Integer a2, java.lang.Integer a3, java.lang.Integer a4, java.lang.Integer a5) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "registerBlockExplosion", "Void", new Object[] {a0, a1, a2, a3, a4, a5});
    }

    public static Object registerFactionsInteractableBlocks(int[] a0, int[] a1, int[] a2) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "registerFactionsInteractableBlocks", "Void", new Object[] {a0, a1, a2});
    }

    public static Object removeAllEventPoints(java.lang.String a0) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "removeAllEventPoints", "Null", new Object[] {a0});
    }

    public static Object removeEventPoint(java.lang.String a0, int a1) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "removeEventPoint", "Boolean", new Object[] {a0, a1});
    }

    public static Object requestTombola(java.lang.String a0) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "requestTombola", "Void", new Object[] {a0});
    }

    public static Object sendErrorNotification(org.bukkit.entity.Player a0, java.lang.String a1, java.lang.String a2) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "sendErrorNotification", "Void", new Object[] {a0, a1, a2});
    }

    public static Object sendHawkEyeLog(java.lang.String a0, java.lang.String a1, java.lang.String a2, java.lang.String a3) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "sendHawkEyeLog", "Void", new Object[] {a0, a1, a2, a3});
    }

    public static Object sendHawkEyeTypedLog(java.lang.String a0, java.lang.String a1, java.lang.String a2, java.lang.String a3) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "sendHawkEyeTypedLog", "Void", new Object[] {a0, a1, a2, a3});
    }

    public static Object sendLuckyPotionBonusPacketToForgePlayer(java.lang.String a0) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "sendLuckyPotionBonusPacketToForgePlayer", "Null", new Object[] {a0});
    }

    public static Object sendValidNotification(org.bukkit.entity.Player a0, java.lang.String a1, java.lang.String a2) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "sendValidNotification", "Void", new Object[] {a0, a1, a2});
    }

    public static Object sendZephyrNotification(org.bukkit.entity.Player a0, java.lang.String a1, java.lang.String a2, java.lang.String a3, java.lang.String a4, long a5) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "sendZephyrNotification", "Void", new Object[] {a0, a1, a2, a3, a4, a5});
    }

    public static Object setMoney(java.lang.String a0, double a1) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "setMoney", "Void", new Object[] {a0, a1});
    }

    public static Object spawnOffPlayer(java.lang.String a0) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "spawnOffPlayer", "Void", new Object[] {a0});
    }

    public static Object tagPlayerInCombat(java.lang.String a0) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "tagPlayerInCombat", "Void", new Object[] {a0});
    }

    public static Object teleportPlayerToLocation(java.lang.String a0, java.lang.String a1, java.lang.Integer a2, java.lang.Integer a3, java.lang.Integer a4, java.lang.Float a5, java.lang.Float a6) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "teleportPlayerToLocation", "Void", new Object[] {a0, a1, a2, a3, a4, a5, a6});
    }

    public static Object teleportPlayersFromNoelFactory(java.lang.String a0) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "teleportPlayersFromNoelFactory", "Null", new Object[] {a0});
    }

    public static Object userCanShoot(java.lang.String a0) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "userCanShoot", "Boolean", new Object[] {a0});
    }

    public static Object withdrawMoney(java.lang.String a0, double a1) {
        return fr.armersindustries.ssng.ponts.Regles.appel("NationsGUI", "withdrawMoney", "Boolean", new Object[] {a0, a1});
    }

    // ---- Sacoches (pouch) : le service prive n'existe pas ici. Methodes statiques neutres
    // (le curseur du joueur est rendu tel quel, rien n'est perdu).
    public static Object[] buildPouchSyncData(String joueur) {
        return null;
    }

    public static Object pouchEquipClick(String joueur, int a, int b, int c, boolean d, boolean e, Object curseur) {
        return curseur;
    }

    public static Object pouchSlotClick(String joueur, int a, int b, int c, boolean d, Object curseur) {
        return curseur;
    }

    public static Object pouchConfigureSlot(String joueur, int a, int b, Object curseur) {
        return curseur;
    }

    public static void pouchDump(String joueur, int a) {
    }

    public static Object pouchEquipFromHand(String joueur) {
        return Boolean.FALSE;
    }

    public static void pouchStoreFromInventory(String joueur, int a) {
    }

    public static String exportPouchStore(String joueur) {
        return "";
    }

    public static Object importPouchStore(String joueur, String donnees) {
        return Boolean.TRUE;
    }

    public static void clearPouchStore(String joueur) {
    }

}
