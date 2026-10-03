# Anchored Freecam

Paper/Purpur **26.2** 向けの、サバイバルサーバー用・クライアントMod不要の制限付きFreecamプラグインです。

## 1.1.10 の主な変更

- デフォルト範囲を **15マス** に変更
- 言語をJavaコード内固定から **外部YAML方式** へ変更
- `plugins/AnchoredFreecam/lang/*.yml` を自動検出
- `ru.yml`、`uk.yml`、`de.yml`、`pt_br.yml` など任意の言語を追加可能
- `/freecam language <言語ID>` で任意の追加言語へ切り替え
- 言語コマンドのTAB補完も `lang/` 内の実ファイルから自動生成
- 翻訳キーが不足している場合は `fallback-language` から自動補完
- `/freecam reload` で `config.yml` と言語YAMLを再読み込み
- 水中判定・呼吸ゲージを幽体離脱カメラ側ではなくアンカー本体側へ同期
- カメラが水中に入っても呼吸を消費しない
- 本体が水中なら本体側の残り空気を呼吸ゲージへ反映
- カメラの水中浮力を抑制し、上下入力していない時の受動的なY移動を止める
- 水中の受動Y移動を PlayerMoveEvent と PlayerVelocityEvent の両方で抑制
- アンカー側Mannequinに実プレイヤー名のネームプレートを表示
- 幽体カメラPlayerは invisible + hidePlayer + visibleByDefault=false でより強く非表示化
- `/tpa`・`/tpaccept`・Homes・管理者 `/tp` などの外部Teleportに対応
- Freecam中の本人が外部Teleportされる場合はFreecamを終了してTeleportを許可
- 他人がFreecam中プレイヤーへTeleportする場合、幽体座標ではなくアンカー本体座標へ自動リダイレクト
- 落下中・ジャンプ中など空中でFreecamを開始した場合、本体Mannequinへ速度・重力・落下距離を引き継ぐ
- 本体が落下している間はFreecamのアンカー中心も本体位置へ追従
- 本体が着地したらその位置で固定
- 空中でFreecamを手動終了した場合も、本体の落下速度と落下距離を実Playerへ戻すため落下停止に悪用できない
- Mannequinのデフォルト説明文（NPC表記）を非表示
- 本体のプレイヤー名ネームプレートは維持
- 自分の本体を右クリックするとFreecamを終了して本体位置へ戻る
- `PLUGIN` TeleportをすべてTPA扱いする挙動を修正
- 小さい同一ワールド内のPLUGIN補正TeleportではFreecamを終了しない
- 水面境界では `isInWater()` だけでなく足元・目の位置も見て水中補正を維持
- 本体の目が実際には水上なのにDROWNINGが発生した場合は誤判定として無視

## 言語ファイル

初回起動時に次のファイルが自動生成されます。

```text
plugins/AnchoredFreecam/
├─ config.yml
└─ lang/
   ├─ ja.yml
   └─ en.yml
```

追加言語は `lang/` にYAMLを置くだけです。

たとえばロシア語を追加する場合:

```text
plugins/AnchoredFreecam/lang/ru.yml
```

ウクライナ語なら:

```text
plugins/AnchoredFreecam/lang/uk.yml
```

その後、

```text
/freecam reload
/freecam language ru
```

または、

```text
/freecam language uk
```

で使用できます。

言語IDはファイル名から自動取得します。使用可能文字は英小文字・数字・`_`・`-` です。

例:

```text
ja.yml       -> ja
en.yml       -> en
ru.yml       -> ru
uk.yml       -> uk
de.yml       -> de
pt_br.yml    -> pt_br
zh_cn.yml    -> zh_cn
```

## カスタム言語ファイル例

`lang/ru.yml` を作る場合は、`ja.yml` または `en.yml` をコピーして翻訳するのが簡単です。

```yaml
no-permission: "..."
reloaded: "..."
current-range: "..."
range-usage: "..."
range-number: "..."
range-limits: "..."
range-set: "..."
current-language: "..."
language-usage: "..."
language-unsupported: "..."
language-set: "..."
console-usage: "..."
already-on: "..."
already-off: "..."
status: "..."
usage: "..."
state-on: "ON"
state-off: "OFF"
spectator-denied: "..."
vehicle-denied: "..."
body-spawn-failed: "..."
freecam-enabled: "..."
freecam-disabled-return: "..."
freecam-disabled: "..."
body-damaged: "..."
boundary-return: "..."
```

プレースホルダー `{range}`、`{label}`、`{state}`、`{language}`、`{languages}`、`{min}`、`{max}` は消さずに翻訳できます。

## フォールバック

`config.yml`:

```yaml
language: ja
fallback-language: ja
```

たとえば、

```yaml
language: ru
fallback-language: en
```

とすると、`ru.yml` に存在しないキーだけ `en.yml` から読み込みます。

そのため、新しいメッセージがプラグイン側に追加された後でも、古いカスタム翻訳ファイルが即座に壊れにくい構成です。

## 範囲

新規導入時のデフォルト:

```yaml
max-distance-blocks: 15.0
```

既存環境では:

```text
/freecam range 15
```

で変更できます。

## コマンド

- `/freecam`
- `/freecam on`
- `/freecam off`
- `/freecam status`
- `/freecam range`
- `/freecam range <マス>`
- `/freecam language`
- `/freecam language <言語ID>`
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

## 基本仕様

- 開始地点にプレイヤーのスキン・装備をコピーしたMannequin本体を残す
- 実Player Entityは不可視のカメラとして飛行
- 他プレイヤーからカメラPlayerを非表示
- 本体から設定距離以上は離れられない
- 範囲を越えた場合はFreecam解除ではなく、最後の正常な範囲内位置へ戻す
- ブロック衝突判定を維持
- Freecam中の攻撃・破壊・設置・インタラクト・アイテム操作を禁止
- 本体へのダメージはFreecam終了後に本人へ転送
- 通常の敵対MonsterはMannequin本体へ敵対を維持

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
build/libs/AnchoredFreecam-1.1.10.jar
```


## TPA / 外部Teleport

Freecam中のPlayer Entityはカメラ位置に存在するため、通常のTPAプラグインはそのままだと幽体座標を参照します。

1.1.7では `COMMAND` / `PLUGIN` 原因のTeleportを汎用的に処理します。

- Freecam中の本人がTeleportされる: Freecamを終了し、そのTeleportを通常どおり実行
- 他プレイヤーがFreecam中の人のカメラ座標へTeleportされる: 宛先をMannequin本体へ変更
- 特定のTPAプラグインAPIには依存しないため、一般的なTPA/Homes/管理者TPで利用可能

`teleport-camera-match-radius-blocks` は、Teleport先が幽体カメラ位置とどれだけ近ければ「そのPlayerへのTeleport」と判定するかを指定します。


## 空中でFreecamを開始した場合

1.1.8では、空中でFreecamを開始しても開始地点に本体が固定されません。

- Mannequinへ開始時の速度をコピー
- 重力を有効化
- 落下距離を引き継ぐ
- 落下中はアンカー中心もMannequinへ追従
- 着地後にMannequinを固定
- 落下ダメージが発生した場合は通常どおり本体ダメージとして処理
- 落下途中でFreecamをOFFにした場合は、Mannequinの現在位置・速度・落下距離をPlayerへ戻す

これにより、落下中にFreecamをON/OFFして空中停止する用途には使えないようにしています。


## 本体表示と右クリック終了

Mannequinのデフォルトdescriptionは `null` にしているため、名前の下に出るNPC表記は表示しません。プレイヤー名のネームプレートは `show-body-nameplate: true` の場合そのまま表示します。

Freecam中に自分のMannequin本体を右クリックすると、その操作をキャンセルしてFreecamを終了し、本体位置へ戻ります。他人の本体を右クリックしても自分のFreecamは終了しません。


## 水中・水面の位置補正

Paperの `PLUGIN` TeleportはTPA専用ではなく、プラグインによる位置補正にも使われます。1.1.10では、小さい同一ワールド内のPLUGIN TeleportはFreecam終了条件にしません。

```yaml
plugin-teleport-correction-max-distance-blocks: 3.0
```

この距離以内のPLUGIN Teleportは移動補正として扱います。それより大きいPLUGIN TeleportやCOMMAND Teleportは、TPA/Home等の実TeleportとしてFreecamを終了します。

また、水面では `isInWater()` が境界で変化しやすいため、足元ブロックと目の位置も含めて水接触を判定します。
