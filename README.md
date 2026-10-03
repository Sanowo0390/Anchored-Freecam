# Anchored Freecam

Paper/Purpur **26.2** 向けの、サバイバルサーバー用・クライアントMod不要の制限付きFreecamプラグインです。

## 1.1.4 の主な変更

- デフォルト範囲を **20マス** に変更
- `config.yml` に `language: ja` を追加
- 日本語 `ja` / 英語 `en` に対応
- コマンド、エラー、状態表示、ActionBarなどのプレイヤー向けメッセージを言語設定に連動
- `/freecam language` で現在の言語を確認
- `/freecam language <ja|en>` でその場で言語を変更可能
- `anchoredfreecam.language` 権限を追加
- `anchoredfreecam.admin` に言語変更権限も含む

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
- 通常の敵対MonsterはMannequin本体へ敵対を維持

## config.yml

```yaml
# ja / en
language: ja

# 本体から離れられる最大距離（マス）
max-distance-blocks: 20.0

leave-body-at-anchor: true
exit-on-body-damage: true
hide-camera-player-from-others: true

force-hostile-mob-aggro: true
mob-aggro-radius-blocks: 32.0

protect-camera-player: true
disable-camera-entity-collision: true
show-boundary-message: true
```

新規導入時のデフォルトは **20マス / 日本語** です。

既存の `config.yml` はプラグイン更新時に自動上書きされません。既存環境で20マスに変更する場合は:

```text
/freecam range 20
```

または `max-distance-blocks: 20.0` に変更してください。

## 言語

対応言語:

- `ja` — 日本語
- `en` — English

設定ファイル:

```yaml
language: ja
```

コマンドでも変更できます。

```text
/freecam language
/freecam language ja
/freecam language en
```

変更は `config.yml` に保存され、その場で反映されます。

## 範囲コマンド

```text
/freecam range
/freecam range 20
/freecam range 30
```

`/freecam range <マス>` は **0.1〜256マス** の範囲で指定できます。変更値は `config.yml` に保存されます。

範囲を縮小した時点ですでに新しい範囲外にいるFreecamプレイヤーは、最後の正常な範囲内位置へ戻されます。

## コマンド

- `/freecam`
- `/freecam on`
- `/freecam off`
- `/freecam status`
- `/freecam range`
- `/freecam range <マス>`
- `/freecam language`
- `/freecam language <ja|en>`
- `/freecam reload`

エイリアス `/fc` も使用できます。

## LuckPerms 権限

- `anchoredfreecam.use` — Freecam使用権限
- `anchoredfreecam.range` — 範囲確認・変更権限
- `anchoredfreecam.language` — 言語確認・変更権限
- `anchoredfreecam.reload` — 設定再読み込み権限
- `anchoredfreecam.admin` — 上記すべて

例:

```text
/lp group member permission set anchoredfreecam.use true
/lp group admin permission set anchoredfreecam.admin true
```

## 敵対Mobについて

MannequinはLivingEntityですがPlayerではないため、VanillaのPlayer検索AIだけでは自動的にターゲットになりません。

Anchored Freecamでは通常の敵対Monsterについて、Freecam本体周辺を定期的に確認してMannequin本体へターゲットを維持します。他のプレイヤーなど、すでに別の正当なターゲットを持つMobからターゲットを奪うことはしません。

Enderman、通常Piglin、Zombified Piglin、通常Spider、Wardenなどの条件付き敵対Mobは、Freecamを使っただけでは強制敵対させません。すでにプレイヤーを狙っていた場合は本体へ引き継ぎます。

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
build/libs/AnchoredFreecam-1.1.4.jar
```
