# Anchored Freecam

Paper/Purpur **26.2**、Java **25** 向けの制限付きサーバー側Freecamプラグインです。
おそらくGeyser経由のプレイヤーでも問題なく動作します。
### ⚠️このプラグインはAIのみで作られました⚠️

## 機能
- 自分のいる場所にマネキンを配置し、疑似スペクテイターモードになる事が出来ます。
- デフォルト範囲は本体から **15マス**。範囲外には行けないようになっています。
- Freecam中は攻撃・破壊・設置・インタラクト・アイテム操作を禁止します。ブロック衝突は維持します。
- 自分の本体を右クリックすると終了して本体へ戻ります。
- 本体へのダメージを本人へ転送します。通常の敵対Monsterは本体へ誘導します。

## コマンドと権限

`/fc` は `/freecam` のエイリアスです。

| コマンド | 権限 |
| --- | --- |
| `/freecam [on\|off\|toggle\|status]` | `anchoredfreecam.use` |
| `/freecam range [マス]` | `anchoredfreecam.range` |
| `/freecam language <言語ID>` | `anchoredfreecam.language` |
| `/freecam reload` | `anchoredfreecam.reload` |

`anchoredfreecam.admin` は上記4権限を含みます。

## カスタム言語

初回に `plugins/AnchoredFreecam/lang/ja.yml` と `en.yml` を生成します。
既存YAMLをコピーして翻訳し、`ru.yml`、`uk.yml`、`de.yml`、`pt_br.yml` など任意のファイルを追加できます。
言語IDには英小文字・数字・`_`・`-` を使います。メッセージのプレースホルダーは保持してください。

TAB補完は言語ファイルから生成します。不足する翻訳キーは `fallback-language` から補完します。既存のカスタム言語に新規必須キーはありません。

## TPA / 外部Teleport

- Freecam中の本人が外部の `PLUGIN` / `COMMAND` Teleportを受けると、Freecamを終了してそのTeleportを許可します。0～3マスのTPA/Homeも対象です。
- 他プレイヤーのTeleport先がカメラ位置と一致すると、Mannequin本体の現在位置へ変更します。
- 内部補正はフラグで識別し、Freecamを終了しません。
- 他プラグインにキャンセルされたTeleportではFreecamを維持します。MONITORでイベントを書き換えないプラグインとの併用を前提とします。

```yaml
redirect-teleports-to-body: true
teleport-camera-match-radius-blocks: 0.75
```

宛先座標による照合なので、近接する複数のカメラや偶然一致する座標から、Teleportの本来の対象プレイヤーを一意に特定することはできません。
また、他プラグインによる補正とTPAはどちらも `PLUGIN` 原因になり得るため、距離だけでは区別しません。他プラグインの補正で解除された場合は診断ログとそのプラグイン側の確認が必要です。

## ビルド

Java 25とGradle（今回の検証では9.6.1）を用います。

```text
gradle clean build
```

生成物: `build/libs/AnchoredFreecam-1.1.14.jar`

テスト結果: `build/reports/tests/test/index.html`

テストはイベントと状態遷移の検証です。ネットワークパケット、クライアントの水中操作感、実サーバーの物理・呼吸・ポーション動作、Geyser、TPAプラグイン連携を実行した証明にはなりません。
