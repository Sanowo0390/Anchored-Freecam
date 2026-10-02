# Anchored Freecam

Paper/Purpur **26.2** 向けの、サバイバルサーバー用・クライアントMod不要の制限付きFreecamプラグインです。

## 1.1.2 の主な修正

- Freecam中の実Player（カメラ）を再び不可視化
- 他プレイヤーからは `hidePlayer()` でも隠す
- MannequinはVanilla AIの通常のPlayerターゲット候補ではないため、敵対Mobのターゲットをサーバー側で定期的に本体へ維持
- ゾンビ・スケルトン・クリーパーなどの通常の敵対Monsterは、Freecam中も本体へ敵対する
- Enderman、通常Piglin、Zombified Piglin、通常Spider、Wardenなど条件付きで敵対するMobは、Freecam開始だけを理由に強制敵対させない
- すでに実Playerを狙っていたMobは条件付きMobでも本体へターゲットを移す
- 距離制限を超えた場合はFreecam解除ではなく、最後の正常な範囲内位置へ戻す

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
- `/freecam reload`

## 権限

- `anchoredfreecam.use` — デフォルトで全員
- `anchoredfreecam.reload` — デフォルトでOP

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
build/libs/AnchoredFreecam-1.1.2.jar
```
