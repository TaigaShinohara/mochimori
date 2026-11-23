# 交付前 UI 検証（実機なし検証）

エミュレータ／KVM が使えないビルド VM の代替として、交付前に JVM 上で
画面を検証する仕組み。統括の横断標準「実機なし検証の標準化」
（本人の決定 2026-11-23、AGENTS.md "Pre-delivery UI verification
standard"）のもち守り実装。正本はこのファイル。

## 構成

- **Robolectric 4.16.1** が本物の MainActivity を JVM 上で起動し、
  本物のレイアウトパスを通す。
- **Roborazzi 1.40.0**（+ `roborazzi-junit-rule`）が画面を撮影し、
  ゴールデン画像と照合する。Paparazzi ではなく Roborazzi なのは、
  このアプリの画面が Activity 内でプログラム的に構築されており、
  実 Activity を起動できる方式が必要なため（先行事例＝Migakeru と
  同一レシピ）。
- テスト本体: `app/src/test/java/com/taigashinohara/mochimori/MainActivityScreenTest.kt`
- ゴールデン画像: `app/src/test/snapshots/images/`（Roborazzi 既定）

### カバー画面（スクリーンショット 4 件）

| テスト | 画面 | 守るもの |
|---|---|---|
| `onboardingEntry_rendersNonBlank` | オンボーディング入口（1 ページ目） | 初回起動の入口が真っ白でないこと |
| `homeScreen_rendersNonBlank` | ホーム（起動直後） | 顔スロット（weight 駆動）が高さ 0 に潰れていないこと＝v0.3.19/20 の真っ白障害の形 |
| `rulesScreen_rendersNonBlank` | ルール画面 | タブ遷移先の表示 |
| `tipsScreen_rendersNonBlank` | コツ画面 | タブ遷移先の表示 |

### Robolectric スモーク（遷移・ロジック 2 件）

- `tabSwitching_showsEachScreen`：ホーム→ルール→コツ→ホームの
  タブ切替で各画面の見出しが出ること。
- `onboardingFlow_pagesAdvanceAndFinish`：「次へ」×2→「あとで」で
  ページが進み、ホームへ着地し、`onboarding_done` が保存され、
  ボトムナビが戻ること。

各スクリーンショットテストには構造アサーション（decor view と
コンテンツの高さ > 0、期待文言の存在、ホームでは BatteryFaceView の
実寸 > 0）が付いており、ゴールデン照合の前にレイアウト崩壊を
検出する。照合の変化しきい値は 0.02（液体の波アニメーションの
微細な揺らぎを吸収する。真っ白画面は約 100% の差分になる）。

既存の純粋ロジックテスト（143 件）は従来どおり別枠で数える。
この画面テスト 6 件はそれに加わる分。

## 実行方法

入口は `verify-ui.sh`（リポジトリ直下）。

```bash
./verify-ui.sh          # ゴールデン照合（交付前チェックの既定）
./verify-ui.sh record   # ゴールデンの再記録
./verify-ui.sh all      # ユニットテスト全件（画面テストは照合モード）
```

- VM のビルド直列化ルール（AGENTS.md、2026-11-23）に従い、Gradle
  実行は `~/workspace/tools/with-build-lock.sh`（flock）経由。
  他プロジェクトのビルドが走っていれば順番を待つ（kill しない）。
- `GRADLE_USER_HOME` はプロジェクトローカル
  (`.local/gradle-home`)。共有 gradle home の journal ロックが
  全チャットのビルドを直列化し、2026-11-23 の横展開時にテスト
  実行が繰り返しタイムアウトしたための隔離。依存は通常どおり
  ローカルミラー経由（ディスクキャッシュ共有）で解決される。
- Robolectric の android-all ランタイムはテスト JVM からネットに
  出られないため、オフライン配置する:
  `.local/robolectric-deps/android-all-instrumented-15-robolectric-13954326-i7.jar`
  （約 200MB、git 管理外）。再取得は Maven Central から同名ファイルを
  curl で落として置くだけ。
- テスト JVM は IPv4 強制（`java.net.preferIPv4Stack=true`、
  ループバック IPv6 の遮断対策）とタイムゾーン Asia/Tokyo 固定
  （決定性のため）。設定は `app/build.gradle.kts` の
  `tasks.withType<Test>()` に集約。

## ゴールデンの更新手順

1. 画面を意図して変えたら `./verify-ui.sh record` で再記録する。
2. 生成された PNG を目視で確認する（崩壊した画面を正解として
   記録しないため）。
3. `./verify-ui.sh`（照合）でもう一度流し、一致を確認する。
4. PNG とコードを同じコミットに入れる。

## 導入時の記録（2026-11-23）

- Roborazzi 1.40 の JUnit ルール（`roborazzi-junit-rule`）は使わない。
  ルールの CaptureType（LastImage / AllImage / Gif）は Espresso の
  ViewInteraction を包んだ CaptureRoot の事前登録が必須で、素の
  Robolectric + 実 Activity では成立しない（実測:
  "captureRoot is required for LastImage / AllImage and Gif" で
  全件失敗）。代わりに core の `captureRoboImage(view, file, options)`
  を直接使い、ゴールデンの置き場所と名前を明示する。他プロジェクトが
  ルール方式のレシピを持っている場合も同じ落とし穴がある。
- 横展開の初日は全プロジェクト同時ビルドで VM が過負荷になり
  （負荷 6 超・OOM kill・VM 再起動）、本検証の初回実行も複数回
  巻き込まれた。ビルド直列化ルール（`with-build-lock.sh`、当初
  1 本→同日中に最大 2 本へ改定）導入後はロック経由で実行する。
- 結果（2026-11-23）: 記録ラン成功（画面テスト 6 件合格、
  ゴールデン 4 枚を `app/src/test/snapshots/images/` に生成し目視
  確認済み）。照合ラン（全件）で 149 件合格・失敗 0（既存 143 件 +
  画面テスト 6 件）。ゴールデン照合込みで一致した。安定確認の
  2 回目は Gradle の up-to-date 判定でテストが再実行されなかった
  ため、独立実行としての照合は記録ランと照合ランの 2 回分。
  `build.sh assembleDebug`（ラッパー経由）も成功し、アプリ本体の
  ビルド成立を確認した。
  注意: Robolectric 環境では電池状態が「確認中」になるため、ホームの
  ゴールデンでは液体と顔は描かれない（窓は空）。顔スロットの実寸
  チェック（高さ・幅 > 0）は構造アサーション側で担保している。
