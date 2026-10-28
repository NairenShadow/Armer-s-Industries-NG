package fr.armersindustries.ssng.iles;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Instantanes de zone : blocs (ID etendus des mods compris), donnees et tile entities
 * (coffres, machines NG...) en NBT. Seules les sections de 16x16x16 non vides sont
 * stockees, donc une grande ile presque vide prend peu de place.
 *
 * Sauvegarde et restauration sont etalees sur plusieurs ticks ; la restauration ne
 * reecrit que les blocs qui ont change et produit un rapport des degats.
 */
public class Instantanes {

    private static final int VERSION = 1;

    /** Resultat d'une operation. */
    public interface Fin {
        void fin(boolean ok, String message, Rapport rapport);
    }

    /** Rapport de differences entre la sauvegarde et le terrain actuel. */
    public static class Rapport {
        public long blocsVerifies;
        public long blocsChanges;
        public long blocsDetruits;
        public long blocsAjoutes;
        public final Map<Integer, Long> detruitsParType = new HashMap<Integer, Long>();

        public String resume() {
            StringBuilder sb = new StringBuilder();
            sb.append(blocsChanges).append(" blocs differents (").append(blocsDetruits).append(" detruits, ")
                    .append(blocsAjoutes).append(" ajoutes)");
            List<Map.Entry<Integer, Long>> l = new ArrayList<Map.Entry<Integer, Long>>(detruitsParType.entrySet());
            java.util.Collections.sort(l, new java.util.Comparator<Map.Entry<Integer, Long>>() {
                public int compare(Map.Entry<Integer, Long> a, Map.Entry<Integer, Long> b) {
                    return b.getValue().compareTo(a.getValue());
                }
            });
            if (!l.isEmpty()) {
                sb.append(" - plus touches : ");
                for (int i = 0; i < Math.min(5, l.size()); i++) {
                    Material m = Material.getMaterial(l.get(i).getKey().intValue());
                    sb.append(i > 0 ? ", " : "").append(m != null ? m.name().toLowerCase() : "id " + l.get(i).getKey())
                            .append(" x").append(l.get(i).getValue());
                }
            }
            return sb.toString();
        }
    }

    /** Une colonne de chunk sauvegardee. */
    static class Colonne {
        int cx;
        int cz;
        int masque;
        short[][] ids = new short[16][];
        byte[][] meta = new byte[16][];
    }

    /** Contenu d'un fichier. */
    static class Contenu {
        String monde;
        int x1;
        int y1;
        int z1;
        int x2;
        int y2;
        int z2;
        long date;
        String auteur;
        List<Colonne> colonnes = new ArrayList<Colonne>();
        List<byte[]> tuiles = new ArrayList<byte[]>();
    }

    // ------------------------------------------------------------------ reflexion (tile entities)

    private static boolean pret;
    private static Method mEcrireNbt; // TileEntity.writeToNBT(NBTTagCompound)
    private static Method mLireNbt; // TileEntity.readFromNBT(NBTTagCompound)
    private static Method mCreer; // TileEntity.createAndLoadEntity(NBTTagCompound)
    private static Method mGetTuile; // World.getBlockTileEntity(int,int,int)
    private static Method mSetTuile; // World.setBlockTileEntity(int,int,int,TileEntity)
    private static Method mMaj; // World.markBlockForUpdate(int,int,int)
    private static Method mCompresser; // CompressedStreamTools.compress(NBTTagCompound) -> byte[]
    private static Method mDecompresser; // CompressedStreamTools.decompress(byte[]) -> NBTTagCompound
    private static Method mGetInt; // NBTTagCompound.getInteger(String)
    private static Class<?> cCompound;
    private static Field fCarteTuiles; // Chunk.chunkTileEntityMap

    private static synchronized void preparer() throws Exception {
        if (pret) {
            return;
        }
        ClassLoader cl = Bukkit.getServer().getClass().getClassLoader();
        cCompound = Class.forName("net.minecraft.nbt.NBTTagCompound", true, cl);
        Class<?> cTuile = Class.forName("net.minecraft.tileentity.TileEntity", true, cl);
        Class<?> cMonde = Class.forName("net.minecraft.world.World", true, cl);
        Class<?> cOutils = Class.forName("net.minecraft.nbt.CompressedStreamTools", true, cl);
        Class<?> cChunk = Class.forName("net.minecraft.world.chunk.Chunk", true, cl);
        mEcrireNbt = cTuile.getMethod("func_70310_b", cCompound);
        mLireNbt = cTuile.getMethod("func_70307_a", cCompound);
        mCreer = cTuile.getMethod("func_70317_c", cCompound);
        mGetTuile = cMonde.getMethod("func_72796_p", int.class, int.class, int.class);
        mSetTuile = cMonde.getMethod("func_72837_a", int.class, int.class, int.class, cTuile);
        mMaj = cMonde.getMethod("func_72845_h", int.class, int.class, int.class);
        mCompresser = cOutils.getMethod("func_74798_a", cCompound);
        mDecompresser = cOutils.getMethod("func_74792_a", byte[].class);
        mGetInt = cCompound.getMethod("func_74762_e", String.class);
        fCarteTuiles = cChunk.getDeclaredField("field_76648_i");
        fCarteTuiles.setAccessible(true);
        pret = true;
    }

    private static Object handle(Object craft) throws Exception {
        return craft.getClass().getMethod("getHandle").invoke(craft);
    }

    // ------------------------------------------------------------------ sauvegarde

    /**
     * Sauvegarde le pave [x1..x2, y1..y2, z1..z2] du monde dans le fichier.
     * chunksParTick : vitesse (2 a 8).
     */
    public static void sauver(final Plugin plugin, final World w, int ax1, int ay1, int az1, int ax2, int ay2, int az2,
            final File fichier, final String auteur, final int chunksParTick, final Fin fin) {
        try {
            preparer();
        } catch (Throwable t) {
            fin.fin(false, "Sauvegarde indisponible sur ce serveur : " + t, null);
            return;
        }
        final Contenu c = new Contenu();
        c.monde = w.getName();
        c.x1 = Math.min(ax1, ax2);
        c.x2 = Math.max(ax1, ax2);
        c.y1 = Math.max(0, Math.min(ay1, ay2));
        c.y2 = Math.min(w.getMaxHeight() - 1, Math.max(ay1, ay2));
        c.z1 = Math.min(az1, az2);
        c.z2 = Math.max(az1, az2);
        c.date = System.currentTimeMillis();
        c.auteur = auteur;
        final List<int[]> chunks = new ArrayList<int[]>();
        for (int cx = c.x1 >> 4; cx <= c.x2 >> 4; cx++) {
            for (int cz = c.z1 >> 4; cz <= c.z2 >> 4; cz++) {
                chunks.add(new int[] {cx, cz});
            }
        }
        final int sy1 = c.y1 >> 4;
        final int sy2 = c.y2 >> 4;
        new BukkitRunnable() {
            int i = 0;

            public void run() {
                try {
                    for (int k = 0; k < chunksParTick && i < chunks.size(); k++, i++) {
                        int cx = chunks.get(i)[0];
                        int cz = chunks.get(i)[1];
                        Chunk ch = w.getChunkAt(cx, cz);
                        ChunkSnapshot s = ch.getChunkSnapshot();
                        Colonne col = new Colonne();
                        col.cx = cx;
                        col.cz = cz;
                        for (int sy = sy1; sy <= sy2; sy++) {
                            if (s.isSectionEmpty(sy)) {
                                continue;
                            }
                            col.masque |= 1 << sy;
                            short[] ids = new short[4096];
                            byte[] meta = new byte[4096];
                            for (int y = 0; y < 16; y++) {
                                for (int z = 0; z < 16; z++) {
                                    for (int x = 0; x < 16; x++) {
                                        int idx = (y << 8) | (z << 4) | x;
                                        ids[idx] = (short) s.getBlockTypeId(x, (sy << 4) + y, z);
                                        meta[idx] = (byte) s.getBlockData(x, (sy << 4) + y, z);
                                    }
                                }
                            }
                            col.ids[sy] = ids;
                            col.meta[sy] = meta;
                        }
                        c.colonnes.add(col);
                        // tile entities de la zone
                        Map<?, ?> carte = (Map<?, ?>) fCarteTuiles.get(handle(ch));
                        for (Object te : new ArrayList<Object>(carte.values())) {
                            Object nbt = cCompound.newInstance();
                            mEcrireNbt.invoke(te, nbt);
                            int x = ((Integer) mGetInt.invoke(nbt, "x")).intValue();
                            int y = ((Integer) mGetInt.invoke(nbt, "y")).intValue();
                            int z = ((Integer) mGetInt.invoke(nbt, "z")).intValue();
                            if (x >= c.x1 && x <= c.x2 && y >= c.y1 && y <= c.y2 && z >= c.z1 && z <= c.z2) {
                                c.tuiles.add((byte[]) mCompresser.invoke(null, nbt));
                            }
                        }
                    }
                } catch (Throwable t) {
                    cancel();
                    fin.fin(false, "Erreur pendant la sauvegarde : " + t, null);
                    return;
                }
                if (i >= chunks.size()) {
                    cancel();
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
                        public void run() {
                            final boolean ok = ecrire(c, fichier);
                            Bukkit.getScheduler().runTask(plugin, new Runnable() {
                                public void run() {
                                    fin.fin(ok, ok ? chunks.size() + " chunks et " + c.tuiles.size() + " blocs speciaux sauvegardes ("
                                            + (fichier.length() / 1024) + " Ko)." : "Ecriture du fichier impossible.", null);
                                }
                            });
                        }
                    });
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private static boolean ecrire(Contenu c, File f) {
        try {
            f.getParentFile().mkdirs();
            File tmp = new File(f.getPath() + ".tmp");
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(tmp)), 65536));
            try {
                out.writeInt(VERSION);
                out.writeUTF(c.monde);
                out.writeInt(c.x1);
                out.writeInt(c.y1);
                out.writeInt(c.z1);
                out.writeInt(c.x2);
                out.writeInt(c.y2);
                out.writeInt(c.z2);
                out.writeLong(c.date);
                out.writeUTF(c.auteur == null ? "" : c.auteur);
                out.writeInt(c.colonnes.size());
                for (Colonne col : c.colonnes) {
                    out.writeInt(col.cx);
                    out.writeInt(col.cz);
                    out.writeInt(col.masque);
                    for (int sy = 0; sy < 16; sy++) {
                        if ((col.masque & (1 << sy)) == 0) {
                            continue;
                        }
                        for (short id : col.ids[sy]) {
                            out.writeShort(id);
                        }
                        out.write(col.meta[sy]);
                    }
                }
                out.writeInt(c.tuiles.size());
                for (byte[] b : c.tuiles) {
                    out.writeInt(b.length);
                    out.write(b);
                }
            } finally {
                out.close();
            }
            if (f.exists() && !f.delete()) {
                return false;
            }
            return tmp.renameTo(f);
        } catch (Exception e) {
            Bukkit.getLogger().warning("[SSNG] Ecriture de " + f + " : " + e);
            return false;
        }
    }

    static Contenu lire(File f) throws Exception {
        DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(new FileInputStream(f)), 65536));
        try {
            Contenu c = new Contenu();
            int v = in.readInt();
            if (v != VERSION) {
                throw new IllegalStateException("version de fichier " + v);
            }
            c.monde = in.readUTF();
            c.x1 = in.readInt();
            c.y1 = in.readInt();
            c.z1 = in.readInt();
            c.x2 = in.readInt();
            c.y2 = in.readInt();
            c.z2 = in.readInt();
            c.date = in.readLong();
            c.auteur = in.readUTF();
            int n = in.readInt();
            for (int i = 0; i < n; i++) {
                Colonne col = new Colonne();
                col.cx = in.readInt();
                col.cz = in.readInt();
                col.masque = in.readInt();
                for (int sy = 0; sy < 16; sy++) {
                    if ((col.masque & (1 << sy)) == 0) {
                        continue;
                    }
                    short[] ids = new short[4096];
                    for (int k = 0; k < 4096; k++) {
                        ids[k] = in.readShort();
                    }
                    byte[] meta = new byte[4096];
                    in.readFully(meta);
                    col.ids[sy] = ids;
                    col.meta[sy] = meta;
                }
                c.colonnes.add(col);
            }
            int nt = in.readInt();
            for (int i = 0; i < nt; i++) {
                byte[] b = new byte[in.readInt()];
                in.readFully(b);
                c.tuiles.add(b);
            }
            return c;
        } finally {
            in.close();
        }
    }

    /** Infos rapides d'un fichier : {monde, x1, y1, z1, x2, y2, z2, date, auteur}. */
    public static Object[] infos(File f) {
        try {
            DataInputStream in = new DataInputStream(new GZIPInputStream(new FileInputStream(f)));
            try {
                in.readInt();
                return new Object[] {in.readUTF(), Integer.valueOf(in.readInt()), Integer.valueOf(in.readInt()), Integer.valueOf(in.readInt()),
                        Integer.valueOf(in.readInt()), Integer.valueOf(in.readInt()), Integer.valueOf(in.readInt()), Long.valueOf(in.readLong()), in.readUTF()};
            } finally {
                in.close();
            }
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ restauration

    /**
     * Restaure le fichier (ou, si simuler, calcule seulement le rapport des differences).
     */
    public static void restaurer(final Plugin plugin, final File fichier, final boolean simuler, final int chunksParTick, final Fin fin) {
        try {
            preparer();
        } catch (Throwable t) {
            fin.fin(false, "Restauration indisponible sur ce serveur : " + t, null);
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            public void run() {
                final Contenu c;
                try {
                    c = lire(fichier);
                } catch (final Exception e) {
                    Bukkit.getScheduler().runTask(plugin, new Runnable() {
                        public void run() {
                            fin.fin(false, "Fichier illisible : " + e, null);
                        }
                    });
                    return;
                }
                Bukkit.getScheduler().runTask(plugin, new Runnable() {
                    public void run() {
                        appliquer(plugin, c, simuler, chunksParTick, fin);
                    }
                });
            }
        });
    }

    @SuppressWarnings("deprecation")
    private static void appliquer(final Plugin plugin, final Contenu c, final boolean simuler, final int chunksParTick, final Fin fin) {
        final World w = Bukkit.getWorld(c.monde);
        if (w == null) {
            fin.fin(false, "Monde " + c.monde + " introuvable.", null);
            return;
        }
        final Rapport r = new Rapport();
        final int sy1 = c.y1 >> 4;
        final int sy2 = c.y2 >> 4;
        new BukkitRunnable() {
            int i = 0;

            public void run() {
                try {
                    for (int k = 0; k < chunksParTick && i < c.colonnes.size(); k++, i++) {
                        Colonne col = c.colonnes.get(i);
                        Chunk ch = w.getChunkAt(col.cx, col.cz);
                        ChunkSnapshot actuel = ch.getChunkSnapshot();
                        boolean modifie = false;
                        for (int sy = sy1; sy <= sy2; sy++) {
                            boolean sauvee = (col.masque & (1 << sy)) != 0;
                            if (!sauvee && actuel.isSectionEmpty(sy)) {
                                continue; // vide avant et apres
                            }
                            short[] ids = col.ids[sy];
                            byte[] meta = col.meta[sy];
                            for (int y = 0; y < 16; y++) {
                                int by = (sy << 4) + y;
                                if (by < c.y1 || by > c.y2) {
                                    continue;
                                }
                                for (int z = 0; z < 16; z++) {
                                    int bz = (col.cz << 4) + z;
                                    if (bz < c.z1 || bz > c.z2) {
                                        continue;
                                    }
                                    for (int x = 0; x < 16; x++) {
                                        int bx = (col.cx << 4) + x;
                                        if (bx < c.x1 || bx > c.x2) {
                                            continue;
                                        }
                                        int idx = (y << 8) | (z << 4) | x;
                                        int voulu = sauvee ? ids[idx] & 0xFFFF : 0;
                                        int vouluMeta = sauvee ? meta[idx] : 0;
                                        int ici = actuel.getBlockTypeId(x, by, z);
                                        int iciMeta = actuel.getBlockData(x, by, z);
                                        r.blocsVerifies++;
                                        if (ici == voulu && iciMeta == vouluMeta) {
                                            continue;
                                        }
                                        r.blocsChanges++;
                                        if (voulu != 0 && ici == 0) {
                                            r.blocsDetruits++;
                                            Long n = r.detruitsParType.get(Integer.valueOf(voulu));
                                            r.detruitsParType.put(Integer.valueOf(voulu), Long.valueOf(n == null ? 1 : n.longValue() + 1));
                                        } else if (voulu == 0) {
                                            r.blocsAjoutes++;
                                        }
                                        if (!simuler) {
                                            w.getBlockAt(bx, by, bz).setTypeIdAndData(voulu, (byte) vouluMeta, false);
                                            modifie = true;
                                        }
                                    }
                                }
                            }
                        }
                        if (modifie) {
                            w.refreshChunk(col.cx, col.cz);
                        }
                    }
                } catch (Throwable t) {
                    cancel();
                    fin.fin(false, "Erreur pendant la restauration : " + t, r);
                    return;
                }
                if (i < c.colonnes.size()) {
                    return;
                }
                cancel();
                if (simuler) {
                    fin.fin(true, r.resume(), r);
                    return;
                }
                int tuiles = 0;
                try {
                    Object monde = handle(w);
                    for (byte[] b : c.tuiles) {
                        Object nbt = mDecompresser.invoke(null, b);
                        int x = ((Integer) mGetInt.invoke(nbt, "x")).intValue();
                        int y = ((Integer) mGetInt.invoke(nbt, "y")).intValue();
                        int z = ((Integer) mGetInt.invoke(nbt, "z")).intValue();
                        Object te = mGetTuile.invoke(monde, x, y, z);
                        if (te != null) {
                            mLireNbt.invoke(te, nbt);
                        } else {
                            Object nouveau = mCreer.invoke(null, nbt);
                            if (nouveau != null) {
                                mSetTuile.invoke(monde, x, y, z, nouveau);
                            }
                        }
                        mMaj.invoke(monde, x, y, z);
                        tuiles++;
                    }
                } catch (Throwable t) {
                    Bukkit.getLogger().warning("[SSNG] Restauration des blocs speciaux : " + t);
                }
                // objets au sol, projectiles et blocs qui tombent dans la zone
                int retires = 0;
                for (Entity e : w.getEntities()) {
                    if (e instanceof Player || e instanceof org.bukkit.entity.Hanging || e instanceof org.bukkit.entity.LivingEntity) {
                        continue;
                    }
                    org.bukkit.Location l = e.getLocation();
                    if (l.getX() >= c.x1 && l.getX() <= c.x2 + 1 && l.getZ() >= c.z1 && l.getZ() <= c.z2 + 1
                            && l.getY() >= c.y1 - 2 && l.getY() <= c.y2 + 2
                            && (e instanceof org.bukkit.entity.Item || e instanceof org.bukkit.entity.FallingBlock
                                    || e instanceof org.bukkit.entity.TNTPrimed || e instanceof org.bukkit.entity.Projectile
                                    || e instanceof org.bukkit.entity.ExperienceOrb)) {
                        e.remove();
                        retires++;
                    }
                }
                fin.fin(true, r.resume() + ". Restaure : " + r.blocsChanges + " blocs, " + tuiles + " blocs speciaux"
                        + (retires > 0 ? ", " + retires + " objets au sol retires" : "") + ".", r);
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /** Fichiers .dat d'un dossier, tries par nom. */
    public static Collection<File> fichiers(File dossier) {
        List<File> l = new ArrayList<File>();
        File[] fs = dossier.listFiles();
        if (fs != null) {
            for (File f : fs) {
                if (f.getName().endsWith(".dat")) {
                    l.add(f);
                }
            }
        }
        java.util.Collections.sort(l);
        return l;
    }
}
