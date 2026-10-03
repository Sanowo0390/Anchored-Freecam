# Anchored Freecam

Paper/Purpur **26.2**、Java **25** 向けのサーバー側Freecamプラグインです。

## 1.1.11

- 水中・水面の移動補正を、内部フラグ付きの予約Teleportに統一。移動イベントの `setTo()` と距離によるTP分類を廃止しました。
- 外部の `PLUGIN` / `COMMAND` Teleportは距離に関係なくFreecamを終了します。キャンセルされたTeleportでは本体を削除しません。
- 水流・浮力による、入力のない方向へのカメラ移動・速度を抑制します。前後左右入力・ジャンプ・スニークの操作方向は維持します。
- カメラの泳ぎ状態への遷移をキャンセルし、飛行を維持します。
- カメラ自身の呼吸増減を止め、本体の空気だけを同期します。本体の水没判定には `isUnderWater()` を使います。実際の本体の溺水ダメージは維持します。
- 水中の本体は可動・重力有効とし、開始時の速度・落下距離・ポーション効果を引き継ぎます。乾いた地面に着地したら固定します。
- 本体の現在位置へ範囲中心を追従させ、予約補正の実行時にも範囲を再確認します。終了済みセッションの補正は次のFreecamへ持ち越しません。
- `debug-water` で終了理由、TP、飛行、水接触、入力、呼吸、本体物理の診断ログを出せます。

**検証範囲:** Java 25 / Paper API `26.2.build.129-stable` でビルドし、モックによるイベント回帰テストを実行します。実際のPaper/Purpur・Java/統合版クライアントによる症状再現と解消確認は未実施です。ユーザー報告の直接原因は不明で、ログによる確認が必要です。[再現・診断手順](docs/WATER-DEBUG.md)を参照してください。

## 既存機能

- デフォルト範囲は本体から3次元で **15マス**。範囲外では解除せず範囲内へ戻します。
- プレイヤーのスキン・装備・名前を持つMannequin本体を残します。descriptionの「NPC」表示は非表示です。
- 幽体側のPlayerは invisible / hidePlayer / visibleByDefault で非表示にします。
- 自分の本体を右クリックすると終了して本体へ戻ります。他人の本体では終了しません。
- 本体へのダメージを本人へ転送します。通常の敵対Monsterは本体へ誘導します。
- 空中開始時の本体の落下速度・落下距離を保持し、途中で終了した場合も本人へ戻します。
- Freecam中は攻撃・破壊・設置・インタラクト・アイテム操作を禁止します。ブロック衝突は維持します。

## コマンドと権限

`/fc` は `/freecam` のエイリアスです。

| コマンド | 権限 |
| --- | --- |
| `/freecam [on\|off\|toggle\|status]` | `anchoredfreecam.use` |
| `/freecam range [マス]` | `anchoredfreecam.range` |
| `/freecam language <言語ID>` | `anchoredfreecam.language` |
| `/freecam reload` | `anchoredfreecam.reload` |

`anchoredfreecam.admin` は上記4権限を含みます。LuckPermsでは例として次の権限を設定できます。

```text
/lp group member permission set anchoredfreecam.use true
/lp group admin permission set anchoredfreecam.admin true
```

既存configの範囲を15マスへ変更する場合:

```text
/freecam range 15
```

## カスタム言語

初回に `plugins/AnchoredFreecam/lang/ja.yml` と `en.yml` を生成します。
既存YAMLをコピーして翻訳し、`ru.yml`、`uk.yml`、`de.yml`、`pt_br.yml` など任意のファイルを追加できます。
言語IDには英小文字・数字・`_`・`-` を使います。メッセージのプレースホルダーは保持してください。

```yaml
language: ru
fallback-language: en
```

```text
/freecam reload
/freecam language ru
```

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

旧設定 `plugin-teleport-correction-max-distance-blocks` は **1.1.11では使用しません**。既存configに残っていても無視します。

## ビルド

Java 25とGradle（今回の検証では9.6.1）を用います。

```text
gradle clean build
```

生成物: `build/libs/AnchoredFreecam-1.1.11.jar`

テスト結果: `build/reports/tests/test/index.html`

テストはイベントと状態遷移の検証です。ネットワークパケット、クライアントの水中操作感、実サーバーの物理・呼吸・ポーション動作、Geyser、TPAプラグイン連携を実行した証明にはなりません。
