<div align="center">

<img src="assets/logo.png" alt="SkyHand" width="140">

# SHDoubleJump

*Double saut configurable par monde et par région WorldGuard.*

<img src="https://img.shields.io/badge/Paper-26.3-f2a900?style=for-the-badge" alt="Paper 26.3"> <img src="https://img.shields.io/badge/Java-25-e76f00?style=for-the-badge" alt="Java 25"> <img src="https://img.shields.io/badge/SkyHand-Opale%20%C2%B7%20Lobby-3aa655?style=for-the-badge" alt="SkyHand Opale · Lobby">

</div>

---

## Présentation

Double saut configurable par monde et par région WorldGuard.

Utilisé sur : **Opale · Lobby**.

## Dépendances

- Optionnelles : `WorldGuard`, `SHTempFly`, `SHParkour`

## Commandes

| Commande | Alias | Description | Permission |
|---|---|---|---|
| `/shdoublejump reload` | — | Relit `config.yml` et `messages.yml` et réévalue les joueurs connectés. En cas d'erreur YAML, l'ancienne configuration reste en place et le fichier fautif est indiqué. | `shdoublejump.admin.reload` |
| `/shdoublejump info [joueur]` | — | Explique le double saut d'un joueur : monde, régions WorldGuard, sauts permis, bypass, vol géré par un autre plugin. | `shdoublejump.admin.info` |

La console peut utiliser les deux ; `info` demande alors un joueur.

## Permissions

| Permission | Description | Par défaut |
|---|---|---|
| `shdoublejump.admin` | Toutes les sous-commandes de `/shdoublejump`. | `op` |
| `shdoublejump.admin.reload` | `/shdoublejump reload`. | `op` |
| `shdoublejump.admin.info` | `/shdoublejump info`. | `op` |
| `shdoublejump.bypass` | Permet de ne pas être affecté par le double saut (bypass). | `op` |
| `shdoublejump.infinite` | Permet un nombre infini de sauts. | `op` |
| `shdoublejump.<n>` | Palier : `n` sauts en l'air (entier, le plus grand compte). Exemple : `shdoublejump.2`. Sans palier, un seul saut en l'air. | personne |

## Configuration

Fichiers par défaut, avec des valeurs d'exemple ; `messages.yml` est créé au premier démarrage s'il manque. Cliquez sur un fichier pour l'ouvrir :

<pre>
plugins/SHDoubleJump/
├── <a href="src/main/resources/config.yml">config.yml</a>
└── <a href="src/main/resources/messages.yml">messages.yml</a>
</pre>

## Compilation

Java 25 et Maven :

```bash
mvn clean package
```

Le jar est généré dans `target/`.

---

<div align="center">

<sub>Développé par <b>Cry_Legende</b> pour le serveur <b>SkyHand</b> · Tous droits réservés</sub>

</div>
