# Anchored Freecam

Paper/Purpur **26.2**、Java **25** 向けの制限付きサーバー側Freecamプラグインです。
### ⚠️このプラグインはAIのみで作られました⚠️

## 機能

- デフォルト範囲は本体から3次元で **15マス**。範囲外では解除せず範囲内へ戻します。
- プレイヤーのスキン・装備・名前を持つMannequin本体を残します。descriptionの「NPC」表示は非表示です。
- 幽体側のPlayerは invisible / hidePlayer / visibleByDefault で非表示にします。
- Freecam中は本人へのメインハンド表示を空にします。ホットバー選択変更時と終了時には実インベントリから再同期し、実アイテムは変更しません。
- 自分の本体を右クリックすると終了して本体へ戻ります。他人の本体では終了しません。
- 本体へのダメージを本人へ転送します。通常の敵対Monsterは本体へ誘導します。
- クリエイティブ中は敵対誘導を行わず、本体・カメラへのターゲットを解除します。本体へのダメージでもFreecamを終了しません。
- 空中開始時の本体の落下速度・落下距離を保持し、途中で終了した場合も本人へ戻します。
- Freecam中は攻撃・破壊・設置・インタラクト・アイテム操作を禁止します。ブロック衝突は維持します。

## 1.1.12の修正

- 範囲外検出時に保存済みの境界位置を本体位置で上書きしないよう修正。本体移動・範囲縮小で保存位置が範囲外になった場合は、境界近くの衝突しない位置へ補正します。
- 幽体からの攻撃を本体ダメージ処理より先に除外し、他人のFreecamを解除できないよう修正。通常のプレイヤーから本体への攻撃は維持します。
- 本人の選択中メインハンドへ空装備の表示を送信し、選択変更・終了時にインベントリを再同期します。オフハンドと本体の複製装備は変更しません。

確認手順と検証範囲は [1.1.12の検証メモ](docs/FIXES-1.1.12.md) を参照してください。

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

生成物: `build/libs/AnchoredFreecam-1.1.13.jar`

テスト結果: `build/reports/tests/test/index.html`

テストはイベントと状態遷移の検証です。ネットワークパケット、クライアントの水中操作感、実サーバーの物理・呼吸・ポーション動作、Geyser、TPAプラグイン連携を実行した証明にはなりません。
