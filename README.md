# Anchored Freecam

Paper/Purpur 1.21.11 向けの、クライアントMod不要の制限付きFreecam風プラグインです。

## 特徴

- `/freecam` / `/fc` でON/OFF
- 開始地点をアンカーとして、デフォルトでは3D距離5ブロック以内だけ移動可能
- Spectatorには変更せず通常のPlayer飛行を使うため、ブロック衝突判定を維持
- Freecam中は他プレイヤーから非表示化可能
- ダメージ、攻撃、ブロック破壊/設置、インタラクト、アイテム取得/投棄、消費、バケツ、インベントリ操作を抑止
- 終了時に元位置・allowFlight・flying・invulnerable・collidable・gliding・fallDistanceを復元
- 外部テレポート時はFreecamを解除してテレポート自体は妨害しない
- ゲームモード変更、切断、キック、プラグイン停止時にもクリーンアップ

## Geyser / Bedrock

クライアントModやFabricは不要です。サーバー側の通常のPlayer abilityと移動だけを使う構成です。

Geyser/Bedrock環境でも利用できる設計ですが、アンチチート構成によっては飛行判定の除外設定が必要になる場合があります。

## 重要な制限

これは「開始地点を身体位置として扱う」Freecam風実装です。

実際のPlayer Entityそのものを透明なカメラとして移動させるため、開始地点に攻撃可能な本体NPCを残す方式ではありません。

## コマンド

- `/freecam`
- `/freecam on`
- `/freecam off`
- `/freecam status`
- `/freecam reload`

## 権限

- `anchoredfreecam.use` — デフォルトで全員
- `anchoredfreecam.reload` — デフォルトでOP

## config.yml

```yaml
max-distance: 5.0
hide-player-from-others: true
invulnerable: true
disable-entity-collision: true
show-boundary-message: true
```

## ビルド

Java 21:

```bash
gradle build
```

生成物:

```text
build/libs/AnchoredFreecam-1.0.0.jar
```
