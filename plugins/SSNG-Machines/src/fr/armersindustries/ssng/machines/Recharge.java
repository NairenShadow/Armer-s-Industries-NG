package fr.armersindustries.ssng.machines;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import fr.armersindustries.ssng.core.SSNG;

/**
 * /recharge : remplit l'objet en main (ou tout l'inventaire avec /recharge tout) :
 * objets electriques (batteries, outils, armes electriques), missiles (carburant),
 * bouteilles de gaz, jerrycans, bidons de carburant, et repare les objets uses.
 */
public class Recharge implements CommandExecutor {

    private final MachinesNG plugin;

    public Recharge(MachinesNG plugin) {
        this.plugin = plugin;
    }

    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (!(s instanceof Player)) {
            SSNG.msg(s, "&cCommande a utiliser en jeu.");
            return true;
        }
        Player p = (Player) s;
        boolean admin = SSNG.admin(p);
        if (!admin && !(p.hasPermission("ssng.test") && plugin.type(p.getWorld()) == MachinesNG.TypeMonde.TEST)) {
            SSNG.msg(p, "&c/recharge est reserve au monde Test (testeurs) et aux admins.");
            return true;
        }
        boolean tout = a.length > 0 && (a[0].equalsIgnoreCase("tout") || a[0].equalsIgnoreCase("all"));
        try {
            Object joueur = p.getClass().getMethod("getHandle").invoke(p);
            Object inv = champ(joueur.getClass(), "field_71071_by").get(joueur);
            int n = 0;
            StringBuilder quoi = new StringBuilder();
            if (tout) {
                Object[] principal = (Object[]) champ(inv.getClass(), "field_70462_a").get(inv);
                Object[] armure = (Object[]) champ(inv.getClass(), "field_70460_b").get(inv);
                for (Object[] tab : new Object[][] {principal, armure}) {
                    for (Object st : tab) {
                        String r = remplir(st);
                        if (r != null) {
                            n++;
                        }
                    }
                }
                SSNG.msg(p, n > 0 ? "&a" + n + " objet(s) recharge(s) ou repare(s)." : "&7Rien a recharger dans ton inventaire.");
            } else {
                Object st = inv.getClass().getMethod("func_70448_g").invoke(inv);
                String r = remplir(st);
                if (st == null) {
                    SSNG.msg(p, "&cTu n'as rien en main.");
                } else if (r == null) {
                    SSNG.msg(p, "&7Cet objet ne se recharge pas (ou est deja plein).");
                } else {
                    quoi.append(r);
                    SSNG.msg(p, "&aRecharge : &f" + quoi);
                }
            }
            p.updateInventory();
        } catch (Throwable t) {
            SSNG.msg(p, "&cRecharge impossible : " + t);
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Recharge", t);
        }
        return true;
    }

    /** Remplit / repare une pile ; renvoie ce qui a ete fait, ou null. */
    private String remplir(Object st) throws Exception {
        if (st == null) {
            return null;
        }
        Class<?> cStack = st.getClass();
        Object item = cStack.getMethod("func_77973_b").invoke(st);
        if (item == null) {
            return null;
        }
        StringBuilder fait = new StringBuilder();
        Class<?> ci = item.getClass();
        // 1) objets electriques (IItemElectric)
        Method max = methode(ci, "getMaxElectricityStored", cStack);
        Method set = methode(ci, "setElectricity", cStack, float.class);
        if (max != null && set != null) {
            float m = ((Number) max.invoke(item, st)).floatValue();
            set.invoke(item, st, Float.valueOf(m));
            fait.append("energie ");
        }
        // 2) bouteilles de gaz
        Method gaz = methode(ci, "setGas", cStack, float.class);
        if (gaz != null) {
            float m = 100f;
            try {
                m = ci.getField("MAX_GAS_STORED").getFloat(null);
            } catch (Exception e) {
                // 100 par defaut
            }
            gaz.invoke(item, st, Float.valueOf(m));
            fait.append("gaz ");
        }
        // 3) missiles (carburant)
        Method maxFuel = methode(ci, "getMaxFuelQuantity", cStack);
        if (maxFuel != null) {
            Object nbt = nbt(st);
            float m = ((Number) maxFuel.invoke(null, st)).floatValue();
            nbt.getClass().getMethod("func_74776_a", String.class, float.class).invoke(nbt, "missileFuel", Float.valueOf(m));
            fait.append("carburant ");
        }
        // 4) jerrycans
        if (ci.getName().endsWith("ItemJerrycan")) {
            Object nbt = nbt(st);
            int cap = 30;
            try {
                cap = ci.getField("CAPACITY").getInt(null);
            } catch (Exception e) {
                // 30 par defaut
            }
            boolean aType = ((Boolean) nbt.getClass().getMethod("func_74764_b", String.class).invoke(nbt, "fuelType")).booleanValue();
            if (!aType) {
                nbt.getClass().getMethod("func_74778_a", String.class, String.class).invoke(nbt, "fuelType", "petrol");
            }
            nbt.getClass().getMethod("func_74768_a", String.class, int.class).invoke(nbt, "fuel", Integer.valueOf(cap));
            fait.append("jerrycan ");
        }
        // 5) bidons de carburant / petrole (Galacticraft : plein = degat 1)
        String nom = ci.getSimpleName().toLowerCase();
        boolean usable = ((Boolean) cStack.getMethod("func_77984_f").invoke(st)).booleanValue();
        int degat = ((Integer) cStack.getMethod("func_77960_j").invoke(st)).intValue();
        if (nom.contains("canister")) {
            if (degat != 1) {
                cStack.getMethod("func_77964_b", int.class).invoke(st, Integer.valueOf(1));
                fait.append("bidon ");
            }
        } else if (usable && degat > 0 && max == null && maxFuel == null && gaz == null) {
            // 6) reparation des objets uses (outils, armes, armures)
            cStack.getMethod("func_77964_b", int.class).invoke(st, Integer.valueOf(0));
            fait.append("repare ");
        }
        return fait.length() == 0 ? null : fait.toString().trim();
    }

    private static Object nbt(Object st) throws Exception {
        Field f = champ(st.getClass(), "field_77990_d");
        Object t = f.get(st);
        if (t == null) {
            ClassLoader cl = Bukkit.getServer().getClass().getClassLoader();
            Class<?> c = Class.forName("net.minecraft.nbt.NBTTagCompound", true, cl);
            t = c.newInstance();
            st.getClass().getMethod("func_77982_d", c).invoke(st, t);
        }
        return t;
    }

    private static Method methode(Class<?> c, String nom, Class<?>... params) {
        try {
            return c.getMethod(nom, params);
        } catch (Exception e) {
            return null;
        }
    }

    private static Field champ(Class<?> c, String nom) throws NoSuchFieldException {
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            try {
                Field f = k.getDeclaredField(nom);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException e) {
                // classe parente
            }
        }
        throw new NoSuchFieldException(nom);
    }
}
