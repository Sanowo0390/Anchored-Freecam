# Anchored Freecam

Paper/Purpur **26.2** 向けの、サバイバルサーバー用・クライアントMod不要の制限付きFreecamプラグインです。

## 特徴

- `/freecam` / `/fc` でON/OFF
- 開始地点にプレイヤーの見た目・装備をコピーした **Mannequin本体** を残す
- 実Playerは人間プレイヤーからだけ隠され、カメラ役として飛行
- Mob AIから実Playerを不可視にはしないため、敵対Mobは通常どおりターゲットを取得できる
- MobがカメラPlayerをターゲットにした場合、Mannequin本体へターゲットを差し替える
- 本体から設定したマス数以上は離れられない
- 境界を越えてもFreecamは解除せず、最後にいた範囲内の位置へ強制的に戻す
- 外部プラグインやアンチチートのTP補正でも、同一ワールドで範囲外へ飛ばされる場合はキャンセルして範囲内へ戻す
- Spectatorを使わないため、カメラ側にも通常のブロック衝突判定が残る
- Freecam中のブロック破壊・設置・攻撃・インタラクト・アイテム操作を禁止
- 本体がダメージを受けると、デフォルトではFreecamを即終了して本人を本体位置へ戻し、そのダメージを本人へ転送
- 終了時に飛行・無敵・衝突・不可視などの元状態を復元

## 「本体を残す」の実装

Bukkit/Paperの通常APIでは、1つのPlayer Entityを開始地点に残したまま同じPlayerを別位置のカメラとして動かすことはできません。

Anchored FreecamではPaper 26.2の **Mannequin** を本体代理として使います。プレイヤーのスキンと装備を複製し、攻撃可能なLivingEntityとして開始地点に残します。実際のPlayer Entityはカメラとして動き、人間プレイヤーのクライアントからは `hidePlayer()` で非表示になります。

重要なのは、実Playerに `setInvisible(true)` を使っていないことです。Mob AIからまで不可視にしてしまうと敵対判定が消えるため、Mobからは実Playerを認識できる状態を維持し、ターゲットイベントでMannequin本体へ誘導します。

## 距離制限

`max-distance-blocks` は本体からの3D距離です。

たとえば5マス設定の場合、6マス目へ進もうとしてもFreecamは解除されません。移動をキャンセルし、直前の範囲内位置へサーバー側から強制的に戻します。

同一ワールド内の外部Teleportについても、5マスを超える移動はキャンセルして範囲内へ戻します。別ワールドへのTeleportでは本体との同期を維持できないため、Freecamを終了してTeleportを許可します。

## Geyser / Bedrock

クライアントModは不要です。Geyserが対応するJava 26.2環境を想定しています。

Bedrock側の入力・飛行挙動はGeyserを経由するため、アンチチートを併用している場合はFreecam中の飛行を除外する設定が必要になる場合があります。

## config.yml

```yaml
# 本体から離れられる最大距離（マス）
# 超えた場合はFreecam解除ではなく、最後の範囲内位置へ戻す
max-distance-blocks: 5.0

# 開始地点にMannequin本体を残す
leave-body-at-anchor: true

# 本体が攻撃されたらFreecamを終了してダメージを本人へ転送
exit-on-body-damage: true

# 移動中の実Player（カメラ）を人間プレイヤーから隠す
hide-camera-player-from-others: true

# カメラ側で受けたダメージを無効化する
protect-camera-player: true

# カメラとEntityの衝突を無効化する
# ブロックとの衝突は残る
disable-camera-entity-collision: true

show-boundary-message: true
```

たとえば10マスにしたい場合:

```yaml
max-distance-blocks: 10.0
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
build/libs/AnchoredFreecam-1.1.1.jar
```
