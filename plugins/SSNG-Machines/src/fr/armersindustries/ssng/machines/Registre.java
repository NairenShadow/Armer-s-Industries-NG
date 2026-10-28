package fr.armersindustries.ssng.machines;

import java.io.File;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.World;

/**
 * Lecture du registre des blocs (Forge) : tous les blocs qui ont une tile entity
 * (machines, coffres...). Sert a la vitrine des machines et a /machines liste.
 */
public final class Registre {

    private Registre() {
    }

    /** Un bloc a tile entity : id, meta, nom affiche, classe de la tile entity, mod. */
    public static class Entree {
        public int id;
        public int meta;
        public String nom;
        public String nomTechnique;
        public String classeTuile;
        public String classeBloc;
    }

    public static List<Entree> blocsATuile(World w) throws Exception {
        ClassLoader cl = Bukkit.getServer().getClass().getClassLoader();
        Class<?> cBloc = Class.forName("net.minecraft.block.Block", true, cl);
        Class<?> cMonde = Class.forName("net.minecraft.world.World", true, cl);
        Class<?> cStack = Class.forName("net.minecraft.item.ItemStack", true, cl);
        Field fListe = cBloc.getField("field_71973_m");
        Method mHas = cBloc.getMethod("hasTileEntity", int.class);
        Method mCreer = cBloc.getMethod("createTileEntity", cMonde, int.class);
        Method mNomTech = cBloc.getMethod("func_71917_a");
        java.lang.reflect.Constructor<?> nouveauStack = cStack.getConstructor(cBloc, int.class, int.class);
        Method mNomStack = cStack.getMethod("func_82833_r");
        Object monde = w.getClass().getMethod("getHandle").invoke(w);
        Object[] blocs = (Object[]) fListe.get(null);
        List<Entree> l = new ArrayList<Entree>();
        for (int id = 0; id < blocs.length; id++) {
            Object b = blocs[id];
            if (b == null) {
                continue;
            }
            String vu = null;
            for (int meta = 0; meta < 16; meta++) {
                boolean a;
                try {
                    a = ((Boolean) mHas.invoke(b, meta)).booleanValue();
                } catch (Throwable t) {
                    continue;
                }
                if (!a) {
                    continue;
                }
                Entree e = new Entree();
                e.id = id;
                e.meta = meta;
                e.classeBloc = b.getClass().getName();
                try {
                    Object te = mCreer.invoke(b, monde, meta);
                    e.classeTuile = te == null ? "-" : te.getClass().getName();
                } catch (Throwable t) {
                    e.classeTuile = "?";
                }
                try {
                    e.nomTechnique = String.valueOf(mNomTech.invoke(b));
                } catch (Throwable t) {
                    e.nomTechnique = "?";
                }
                try {
                    e.nom = String.valueOf(mNomStack.invoke(nouveauStack.newInstance(b, 1, meta)));
                } catch (Throwable t) {
                    e.nom = e.nomTechnique;
                }
                // meme tile entity et meme nom pour toutes les metas : une seule entree
                String cle = e.classeTuile + "|" + e.nom;
                if (cle.equals(vu)) {
                    continue;
                }
                vu = cle;
                l.add(e);
            }
        }
        return l;
    }

    public static int exporter(World w, File f) throws Exception {
        List<Entree> l = blocsATuile(w);
        PrintWriter out = new PrintWriter(f, "UTF-8");
        try {
            out.println("id:meta\tnom\tbloc\ttile entity\tnom technique");
            for (Entree e : l) {
                out.println(e.id + ":" + e.meta + "\t" + e.nom + "\t" + e.classeBloc + "\t" + e.classeTuile + "\t" + e.nomTechnique);
            }
        } finally {
            out.close();
        }
        return l.size();
    }
}
