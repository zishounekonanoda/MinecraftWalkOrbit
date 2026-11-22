# Walk Orbit

Apex Legends 風の「軌道」を、エリトラではなく**歩行**で表示する Paper プラグインです（[MinecraftElytraOrbit](https://github.com/zishounekonanoda/MinecraftElytraOrbit) の歩行版）。

対応: Paper 1.21.11 / Java 21

---

## 日本語

### 見た目
- **移動中**: おしりの左右から、パーティクルが縦の帯になって出ます。歩いた道筋に沿って途切れずにつながり、下に行くほど外側へ扇形に広がります。体の向きに合わせて少し後ろに出て、しゃがむと位置が下がります。
- **立ち止まっている間**: しばらく止まっていると、パーティクルがプレイヤーの周りを回りながら上から下へ滴り落ちます。下まで落ちると上に戻って繰り返します。

エリトラ滑空中、クリエイティブ飛行中、乗り物に乗っているとき、泳いでいるとき、スペクテイター、透明化中は表示しません。

### 使い方
- `/walkorbit`（または `/worbit`）でメニューを開き、アイコンをクリックすると適用されます。
- メニュー下段の「歩行時の表示方式」（コンパレーター）で、歩いているときの出し方をプレイヤーごとに選べます（左右 / 背中・太め / 背中・控えめ / 足元 / サーバーの既定）。
- 選んだ軌道はプレイヤー自身に保存されるので、再ログインしても残ります。ElytraOrbit のタグとは干渉しません。

| コマンド | 権限 | 内容 |
|---|---|---|
| `/walkorbit` / `/walkorbit menu` | `walkorbit.menu`（全員） | メニューを開く |
| `/walkorbit set <プレイヤー> <グループ>` | `walkorbit.admin`（OP） | 軌道を付与する |
| `/walkorbit clear <プレイヤー>` | `walkorbit.admin` | 軌道を外す |
| `/walkorbit reload` | `walkorbit.admin` | config と言語ファイルを再読み込みする |

### 設定（config.yml）
- `walk_shape`: 移動中の形
  - `style` で表示方式を選べます。軌道ごとに `walk_shape: { style: FEET }` のように変えることもできます。
    - `SIDES`: おしりの左右に、下に向かって扇形に広がる帯（既定）
    - `BACK`: 背中の真後ろに太めの帯
    - `BACK_SUBTLE`: 背中の真後ろに細く控えめな帯
    - `FEET`: 足元の低い位置に太めの帯
  - 高さ・幅・点の数などは style ごとに初期値があり、書いたキーだけ上書きされます（`line_top`、`line_bottom`、`side_offset`、`back_offset`、`width`、`points`、`spread`、`flare`、`step` など）
- `idle_shape`: 停止中の形（半径、開始と終了の高さ、本数、回転速度、落下速度、密度など）
- `groups.<名前>`: 軌道ごとの設定
  - `COLOR`: GUI での名前の色（`RED` や `"&c"`）
  - `MATERIAL`: GUI のアイコン
  - `walk` / `idle`: パーティクルのリスト
  - `walk_shape` / `idle_shape`: このグループだけ形を変えたいときに書く上書き設定

パーティクル1つあたりのキー: `particle`、`count`（1）、`offset`（0）、`speed`（0）、`chance`（1.0。0〜1 の出現確率）。

追加データが必要なパーティクルは、次のキーで指定します（古い `redstone:` の書き方も使えます）。

| パーティクル | キー |
|---|---|
| `DUST` | `color`、`size` |
| `DUST_COLOR_TRANSITION` | `color`（開始色）、`to_color`（終了色）、`size` |
| `ENTITY_EFFECT` / `TINTED_LEAVES` / `FLASH` | `color` |
| `EFFECT` / `INSTANT_EFFECT` | `color`、`value`（強さ） |
| `FALLING_DUST` / `BLOCK` / `BLOCK_CRUMBLE` / `DUST_PILLAR` | `block`（ブロック名） |
| `ITEM` | `item`（アイテム名） |
| `SCULK_CHARGE` / `DRAGON_BREATH` | `value`（数値） |
| `TRAIL` | `color`、`duration`（表示する tick 数）、`fall`（その間に下へ垂れる距離） |
| `NOTE` | `note`（`random` で毎回ちがう音程・色、0〜24 で固定） |

`TRAIL` は、1つの点が `duration` の間、下へ流れながら残るパーティクルです。同梱の軌道では、色付きの DUST と一緒に出しています。

### パーティクルを長く残す（どのパーティクルでも使えます）
パーティクルが表示される時間は、種類ごとにゲーム側で決まっていて、サーバーからは変えられません。そこで、次のキーを付けると、プラグインが同じ場所に出し直し続けて、長く残っているように見せます。

| キー | 内容 |
|---|---|
| `linger` | 何 tick の間残して見せるか |
| `refresh` | 何 tick ごとに出し直すか（そのパーティクルが自然に消えるまでの時間くらい） |
| `fall` | 残っている間に下へずらす距離（どのパーティクルでも「垂れる」動きになる） |

出し直すぶん、サーバーから送るパケットは増えます。

### 同梱の軌道（24種）
- ランク系: PREDATOR、MASTER、DIAMOND、GOLD
- 属性系: INFERNO（業火）、FROST（氷結）、SOUL（魂）、ELECTRIC（電撃）、ENDER（エンド）、SCULK（スカルク）、SHADOW（影）、GLOW（グロウ）
- 自然系: CHERRY（桜）、AUTUMN（紅葉）、FOREST（森）、DESERT（砂塵）、OCEAN（海）
- ファン系: RAINBOW（虹）、LOVE（ハート）、MUSIC（音符）、POTION（ポーション）、TOTEM（不死のトーテム）、OMINOUS（不吉）、EMERALD（エメラルド）

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
