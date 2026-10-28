package fr.armersindustries.ssng.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

/**
 * Inventaires au format NBT de Minecraft : les objets NationsGlory gardent toutes leurs donnees
 * (armes, munitions, objets moddes), ce que la serialisation Bukkit ne garantit pas.
 * Sert aux kits (assaut, arenes...). Plus : vider un joueur, le soigner.
 */
public final class Inventaire {

    private Inventaire() {
    }

    private static boolean pret;
    private static Class<?> cCompound;
    private static Class<?> cListe;
    private static Method mSetTag;
    private static Method mGetListe;
    private static Method mEcrire;
    private static Method mLire;
    private static Method mVersNbt;
    private static Method mDepuisNbt;
    private static Field fInventaire;

    private static Method methode(Class<?> c, Class<?> retour, boolean statique, Class<?>... params) {
        for (Method m : c.getMethods()) {
            if (Modifier.isStatic(m.getModifiers()) == statique && m.getReturnType() == retour
                    && java.util.Arrays.equals(m.getParameterTypes(), params)) {
                return m;
            }
        }
        throw new IllegalStateException("methode introuvable dans " + c.getName());
    }

    private static synchronized void preparer(Player p) throws Exception {
        if (pret) {
            return;
        }
        Object handle = p.getClass().getMethod("getHandle").invoke(p);
        for (Class<?> c = handle.getClass(); c != null && fInventaire == null; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.getType().getName().endsWith("InventoryPlayer")) {
                    fInventaire = f;
                }
            }
        }
        fInventaire.setAccessible(true);
        ClassLoader cl = handle.getClass().getClassLoader();
        cCompound = Class.forName("net.minecraft.nbt.NBTTagCompound", true, cl);
        cListe = Class.forName("net.minecraft.nbt.NBTTagList", true, cl);
        Class<?> cBase = Class.forName("net.minecraft.nbt.NBTBase", true, cl);
        Class<?> cOutils = Class.forName("net.minecraft.nbt.CompressedStreamTools", true, cl);
        mSetTag = methode(cCompound, void.class, false, String.class, cBase);
        mGetListe = methode(cCompound, cListe, false, String.class);
        mEcrire = methode(cOutils, void.class, true, cCompound, OutputStream.class);
        mLire = methode(cOutils, cCompound, true, InputStream.class);
        mVersNbt = methode(fInventaire.getType(), cListe, false, cListe);
        mDepuisNbt = methode(fInventaire.getType(), void.class, false, cListe);
        pret = true;
    }

    /** Enregistre l'inventaire complet (armure comprise) du joueur dans ce fichier. */
    public static boolean sauver(Player p, File f) {
        try {
            preparer(p);
            Object handle = p.getClass().getMethod("getHandle").invoke(p);
            Object racine = cCompound.newInstance();
            mSetTag.invoke(racine, "Inventaire", mVersNbt.invoke(fInventaire.get(handle), cListe.newInstance()));
            f.getParentFile().mkdirs();
            OutputStream out = new FileOutputStream(f);
            try {
                mEcrire.invoke(null, racine, out);
            } finally {
                out.close();
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Remplace l'inventaire du joueur par celui du fichier. */
    public static boolean charger(Player p, File f) {
        if (!f.exists()) {
            return false;
        }
        try {
            preparer(p);
            Object handle = p.getClass().getMethod("getHandle").invoke(p);
            Object racine;
            InputStream in = new FileInputStream(f);
            try {
                racine = mLire.invoke(null, in);
            } finally {
                in.close();
            }
            mDepuisNbt.invoke(fInventaire.get(handle), mGetListe.invoke(racine, "Inventaire"));
            p.updateInventory();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Vide inventaire, armure et effets. */
    public static void vider(Player p) {
        p.getInventory().clear();
        p.getInventory().setArmorContents(new ItemStack[4]);
        for (PotionEffect e : p.getActivePotionEffects()) {
            p.removePotionEffect(e.getType());
        }
        p.setFireTicks(0);
        p.updateInventory();
    }

    /** Vie et faim au maximum (en 1.6.4 setHealth existe en int et en double : on choisit le double). */
    public static void soigner(Player p) {
        try {
            Method max = null;
            Method set = null;
            for (Method m : p.getClass().getMethods()) {
                if (m.getName().equals("getMaxHealth") && m.getParameterTypes().length == 0 && m.getReturnType() == double.class) {
                    max = m;
                }
                if (m.getName().equals("setHealth") && m.getParameterTypes().length == 1 && m.getParameterTypes()[0] == double.class) {
                    set = m;
                }
            }
            if (max != null && set != null) {
                set.invoke(p, max.invoke(p));
            }
        } catch (Throwable ignore) {
            // la vie n'est simplement pas remise au maximum
        }
        p.setFoodLevel(20);
    }
}
