package fr.armersindustries.ssng.machines;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Lecture / ecriture des fichiers .yml en UTF-8, quel que soit le systeme
 * (sous Windows, Bukkit 1.6 utilise l'encodage Windows-1252 : "»" devenait "Â»").
 */
public final class Yaml {

    private static final Charset UTF8 = Charset.forName("UTF-8");

    private Yaml() {
    }

    public static YamlConfiguration charger(File f) {
        YamlConfiguration y = new YamlConfiguration();
        if (f == null || !f.exists()) {
            return y;
        }
        try {
            byte[] b = java.nio.file.Files.readAllBytes(f.toPath());
            String texte = new String(b, UTF8);
            if (texte.startsWith("﻿")) {
                texte = texte.substring(1);
            }
            y.loadFromString(texte);
        } catch (Exception e) {
            org.bukkit.Bukkit.getLogger().warning("[SSNG] Lecture de " + f.getName() + " impossible : " + e);
        }
        return y;
    }

    public static void sauver(YamlConfiguration y, File f) throws IOException {
        if (f.getParentFile() != null) {
            f.getParentFile().mkdirs();
        }
        java.nio.file.Files.write(f.toPath(), y.saveToString().getBytes(UTF8));
    }
}
