#!/usr/bin/env python3
"""Genere les plugins 'ponts' (NationsGUI, NationsUtils, ServerBridge...) a partir de bridge.json.

Chaque pont est un vrai plugin Bukkit qui porte le nom d'un plugin prive de NationsGlory
et declare exactement les methodes que les mods appellent par reflexion. Chaque methode
delegue a fr.armersindustries.ssng.ponts.Regles.appel(...) qui decide de la reponse.
"""
import json, os, re, sys

ICI = os.path.dirname(os.path.abspath(__file__))
data = json.load(open(sys.argv[1]))

# Plugins a ne pas simuler (vrais plugins installes) et appels mal attribues par le scan
IGNORER_PLUGINS = {'WorldGuard', 'Essentials'}
IGNORER_METHODES = {'getPacketId', 'getPacketSize', 'levelFor', 'setGoal', 'ownBackpack',
                    'func_73284_a', 'func_73288_a', 'NationsGUI', 'NGCollection', 'NationsSuccess'}

# Type de retour attendu pour les appels dont le scan n'a pas trouve le cast
OBJ_BOOL = {'saveRagdollItemGrip', 'savePlayerCollectEventPoint', 'canPlayerModifyBlock', 'canPlayerOpenPostbox',
            'dealBukkitDamage', 'decideCenter', 'executeNpcCommand', 'hasNonGlobalWorldguardRegionAt',
            'hasPlayersInNoelFactoryRegion', 'isCountryClaimAt', 'isInExtractionZone', 'payNGCoins',
            'removeEventPoint', 'withdrawMoney', 'canPlayerUseEnchant', 'hasMalus'}
OBJ_NULL = {'get', 'getInstance', 'getAssaultAtPosition', 'getCorruptionInfo', 'sendLuckyPotionBonusPacketToForgePlayer',
            'getEnergy', 'getTextureIndex', 'addEventPoint', 'removeAllEventPoints', 'teleportPlayersFromNoelFactory',
            'getToxicityTierNumber', 'addBattlePassPoints', 'addBattlePassPointsForMultipleActions', 'decreaseCorruption',
            'getSkillNumericEffect', 'getMalusValue', 'getEventPoints', 'getNoelFactoryRegionBounds'}

JAVA = {'int': 'int', 'boolean': 'boolean', 'double': 'double', 'long': 'long', 'float': 'float',
        'short': 'short', 'byte': 'byte', 'char': 'char'}


def jtype(t):
    t = t.strip('"')
    if t.startswith('[L') and t.endswith(';'):
        return t[2:-1] + '[]'
    if t == '[I':
        return 'int[]'
    if t == '[Z':
        return 'boolean[]'
    return JAVA.get(t, t.replace('$', '.'))


def genre(o):
    """Type de retour 'logique' : Boolean, Integer, Double, Float, Long, String, Map, List, Json, Tab, Null, Void."""
    r = [x for x in o['rets'] if x not in ('?', 'Object')]
    r = r[0] if r else ('Object' if 'Object' in o['rets'] else '?')
    m = o['method']
    if r == 'Object' or r == '?':
        if m in OBJ_BOOL:
            return 'Boolean'
        return 'Null'
    return {'java.lang.Boolean': 'Boolean', 'java.lang.Integer': 'Integer', 'java.lang.Double': 'Double',
            'java.lang.Float': 'Float', 'java.lang.Long': 'Long', 'java.lang.String': 'String',
            'java.util.HashMap': 'Map', 'java.util.Map': 'Map', 'java.util.List': 'List',
            'com.google.gson.JsonArray': 'Json', 'void': 'Void'}.get(r, 'Tab' if r.startswith('"[') else 'Null')


ponts = {}
for o in data:
    p = o['plugin']
    if p.startswith('?') or p in IGNORER_PLUGINS or o['method'] in IGNORER_METHODES:
        continue
    cle = (o['method'], tuple(o['params']))
    ponts.setdefault(p, {})
    if cle not in ponts[p]:
        ponts[p][cle] = genre(o)

base = os.path.join(ICI, 'ponts')
os.makedirs(base, exist_ok=True)
resume = []
for p, methodes in sorted(ponts.items()):
    d = os.path.join(base, p, 'src', 'fr', 'armersindustries', 'ssng', 'ponts', 'ponts')
    os.makedirs(d, exist_ok=True)
    lignes = ['package fr.armersindustries.ssng.ponts.ponts;', '',
              '/** Pont genere automatiquement : simule le plugin prive "%s" de NationsGlory. */' % p,
              'public class %s extends org.bukkit.plugin.java.JavaPlugin {' % p, '',
              '    @Override', '    public void onEnable() {',
              '        fr.armersindustries.ssng.ponts.Regles.pontActif("%s", %d);' % (p, len(methodes)), '    }', '']
    for (m, params), g in sorted(methodes.items()):
        args = ', '.join('%s a%d' % (jtype(t), i) for i, t in enumerate(params))
        noms = ', '.join('a%d' % i for i in range(len(params)))
        lignes.append('    public static Object %s(%s) {' % (m, args))
        lignes.append('        return fr.armersindustries.ssng.ponts.Regles.appel("%s", "%s", "%s", new Object[] {%s});' % (p, m, g, noms))
        lignes.append('    }')
        lignes.append('')
        resume.append('%s.%s(%s) -> %s' % (p, m, ', '.join(jtype(t).split('.')[-1] for t in params), g))
    extra = os.path.join(ICI, 'ponts_extra', p + '.java')
    if os.path.exists(extra):
        lignes.append(open(extra).read().rstrip())
        lignes.append('')
    lignes.append('}')
    open(os.path.join(d, p + '.java'), 'w').write('\n'.join(lignes) + '\n')
    open(os.path.join(base, p, 'plugin.yml'), 'w').write(
        'name: %s\nversion: 1.0-compat\nmain: fr.armersindustries.ssng.ponts.ponts.%s\n'
        'description: Pont de compatibilite (PontsNG) remplacant le plugin prive NationsGlory "%s".\n'
        'depend: [PontsNG]\nload: STARTUP\n' % (p, p, p))
open(os.path.join(ICI, 'ponts', 'LISTE.txt'), 'w').write('\n'.join(resume) + '\n')
print(len(ponts), 'ponts,', len(resume), 'methodes')
