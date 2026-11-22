# Walk Orbit

Apex Legends 風の「軌道」を、エリトラではなく**歩行**で表示する Paper プラグインです（[MinecraftElytraOrbit](https://github.com/zishounekonanoda/MinecraftElytraOrbit) の歩行版）。

対応: Paper 1.21.11 / Java 21

---

## 日本語

### 見た目
- **移動中**: おしりの左右から、パーティクルが縦の帯になって出ます。歩いた道筋に沿って途切れずにつながり、横に少しばらつきます。体の向きに合わせて少し後ろに出て、しゃがむと位置が下がります。
- **立ち止まっている間**: しばらく止まっていると、パーティクルがプレイヤーの周りを回りながら上から下へ滴り落ちます。下まで落ちると上に戻って繰り返します。

エリトラ滑空中、クリエイティブ飛行中、乗り物に乗っているとき、泳いでいるとき、スペクテイター、透明化中は表示しません。

### 使い方
- `/walkorbit`（または `/worbit`）でメニューを開き、アイコンをクリックすると適用されます。
- 選んだ軌道はプレイヤー自身に保存されるので、再ログインしても残ります。ElytraOrbit のタグとは干渉しません。

| コマンド | 権限 | 内容 |
|---|---|---|
| `/walkorbit` / `/walkorbit menu` | `walkorbit.menu`（全員） | メニューを開く |
| `/walkorbit set <プレイヤー> <グループ>` | `walkorbit.admin`（OP） | 軌道を付与する |
| `/walkorbit clear <プレイヤー>` | `walkorbit.admin` | 軌道を外す |
| `/walkorbit reload` | `walkorbit.admin` | config と言語ファイルを再読み込みする |

### 設定（config.yml）
- `walk_shape`: 移動中の形（帯の上端と下端の高さ、左右の幅、後ろへのずれ、点の数、横のばらつき、歩いた道筋を埋める間隔など）
- `idle_shape`: 停止中の形（半径、開始と終了の高さ、本数、回転速度、落下速度、密度など）
- `groups.<名前>`: 軌道ごとの設定
  - `COLOR`: GUI での名前の色（`RED` や `"&c"`）
  - `MATERIAL`: GUI のアイコン
  - `walk` / `idle`: パーティクルのリスト
  - `walk_shape` / `idle_shape`: このグループだけ形を変えたいときに書く上書き設定

パーティクル1つあたりのキー: `particle`、`count`（1）、`offset`（0）、`speed`（0）、`chance`（1.0。0〜1 の出現確率）、`redstone`（`DUST` 用の `color` と `size`）。

パーティクル名は [Bukkit の Particle 一覧](https://jd.papermc.io/paper/1.21.11/org/bukkit/Particle.html) の名前を使います。1.20.5 より前の古い名前（`REDSTONE`、`FIREWORKS_SPARK` など）は自動で変換されます。`DRIPPING_LAVA` のような滴るパーティクルを `chance` を低くして混ぜると、「滴る」感じが強くなります。

---

## English

- **While moving**: vertical particle lines on the left and right of the hips, following the body direction (lower while sneaking).
- **While standing still**: particles circle the player and drip from top to bottom, looping.
- Hidden while gliding, flying, riding, swimming, in spectator mode or invisible.

Open the menu with `/walkorbit` (alias `/worbit`). Admin: `/walkorbit set <player> <group>`, `/walkorbit clear <player>`, `/walkorbit reload` (`walkorbit.admin`). The selection is stored on the player and persists across relogs.

See the comments in `config.yml` for every shape and particle option.

## Build

```
./mvnw package
```
