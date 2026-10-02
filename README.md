# Anchored Freecam

Paper/Purpur **26.2** 向けの、サバイバルサーバー用・クライアントMod不要の制限付きFreecamプラグインです。

## 1.1.3 の主な修正

- LuckPerms向けに権限ノードを分離
- `anchoredfreecam.use` はデフォルト無効
- `anchoredfreecam.range` を追加
- `anchoredfreecam.admin` で全権限をまとめて付与可能
- `/freecam range` で現在の範囲を確認
- `/freecam range <マス>` で範囲を変更
- 変更値は `config.yml` に保存
- 範囲を縮小した際、すでに範囲外のFreecamプレイヤーも即座に範囲内へ戻す

## 基本仕様

- `/freecam` / `/fc` でON/OFF
- 開始地点にプレイヤーのスキン・装備をコピーした **Mannequin本体** を残す
- 実Player Entityは不可視のカメラとして飛行
- 他プレイヤーからカメラPlayerを非表示
- 本体から設定距離以上は離れられない
- 範囲を越えた移動や同一ワールド内の範囲外Teleportはキャンセルし、最後の正常位置へ戻す
- Spectatorを使わないためブロック衝突判定を維持
- Freecam中の攻撃・破壊・設置・インタラクト・アイテム操作を禁止
- 本体へのダメージは、デフォルトではFreecam終了後に本人へ転送

## 敵対Mobについて

MannequinはLivingEntityですがPlayerではありません。そのため、Vanillaの「近くのPlayerを探して敵対する」AIだけではMannequinを自動的にターゲットにしません。

Anchored Freecam 1.1.2では、Freecam本体の周囲を定期的に確認し、通常の敵対MonsterについてはMannequin本体をターゲットとして維持します。

この処理は設定できます。

```yaml
force-hostile-mob-aggro: true
mob-aggro-radius-blocks: 32.0
```

他のプレイヤーなど、すでに別の正当なターゲットを持っているMobからターゲットを奪うことはしません。

## config.yml

```yaml
# 本体から離れられる最大距離（マス）
max-distance-blocks: 5.0

# 開始地点にMannequin本体を残す
leave-body-at-anchor: true

# 本体が攻撃されたらFreecamを終了してダメージを本人へ転送
exit-on-body-damage: true

# カメラPlayerを他プレイヤーから隠す
hide-camera-player-from-others: true

# 通常の敵対Monsterを本体へ敵対させ続ける
force-hostile-mob-aggro: true

# 敵対維持の検索範囲
mob-aggro-radius-blocks: 32.0

# カメラ側へのダメージを無効化
protect-camera-player: true

# カメラとEntityの衝突を無効化
# ブロック衝突は残る
disable-camera-entity-collision: true

show-boundary-message: true
```

## コマンド

- `/freecam`
- `/freecam on`
- `/freecam off`
- `/freecam status`
- `/freecam range`
- `/freecam range <マス>`
- `/freecam reload`

`/freecam range <マス>` は 0.1〜256 マスの範囲で指定できます。

## 権限

- `anchoredfreecam.use` — Freecam使用権限
- `anchoredfreecam.range` — 範囲確認・変更権限
- `anchoredfreecam.reload` — 設定再読み込み権限
- `anchoredfreecam.admin` — 上記すべて

LuckPerms例:

```text
/lp group member permission set anchoredfreecam.use true
/lp group admin permission set anchoredfreecam.admin true
```

## 対応環境

- Minecraft Java: **26.2**
- Paper/Purpur: **26.2**
- Java: **25**
- クライアントMod: 不要

## ビルド

```bash
gradle build
```

生成物:

```text
build/libs/AnchoredFreecam-1.1.3.jar
```
