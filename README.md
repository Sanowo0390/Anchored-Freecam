# Anchored Freecam

Paper/Purpur **26.2** 向けの、サバイバルサーバー用・クライアントMod不要の制限付きFreecamプラグインです。

## 特徴

- `/freecam` / `/fc` でON/OFF
- 開始地点にプレイヤーの見た目・装備をコピーした **Mannequin本体** を残す
- 実Playerは他人から隠され、カメラ役として飛行
- 本体から設定したマス数以上は離れられない
- Spectatorを使わないため、カメラ側にも通常のブロック衝突判定が残る
- Freecam中のブロック破壊・設置・攻撃・インタラクト・アイテム操作を禁止
- Mobが移動中のカメラPlayerをターゲットにした場合、可能ならアンカー側の本体へターゲットを移す
- 本体がダメージを受けると、デフォルトではFreecamを即終了して本人を本体位置へ戻し、そのダメージを本人へ転送
- 終了時に飛行・無敵・衝突・不可視などの状態を復元

## 「本体を残す」の実装

Bukkit/Paperの通常APIでは、1つのPlayer Entityを開始地点に残したまま同じPlayerを別位置のカメラとして動かすことはできません。

Anchored FreecamではPaper 26.2の **Mannequin** を本体代理として使います。プレイヤーのスキンと装備を複製し、攻撃可能なLivingEntityとして開始地点に残します。実際のPlayer Entityはカメラとして動き、他プレイヤーからは非表示になります。

本体への攻撃を本人へ転送するため、サバイバルでFreecamを完全な安全地帯として使いにくい設計です。

## Geyser / Bedrock

クライアントModは不要です。Geyserが対応するJava 26.2環境を想定しています。

Bedrock側の入力・飛行挙動はGeyserを経由するため、アンチチートを併用している場合はFreecam中の飛行を除外する設定が必要になる場合があります。

## config.yml

```yaml
# 本体から離れられる最大距離（マス）
max-distance-blocks: 5.0

# 開始地点にMannequin本体を残す
leave-body-at-anchor: true

# 本体が攻撃されたらFreecamを終了してダメージを本人へ転送
exit-on-body-damage: true

# 移動中の実Player（カメラ）を他プレイヤーから隠す
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
build/libs/AnchoredFreecam-1.1.0.jar
```
