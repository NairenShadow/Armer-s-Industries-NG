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
