# もち守り (MochiMori)

「何もしなくても、電池が持つ」を目指す、静かな自動節電アプリ（Android）。

残量が減ってくると、画面の明るさと消灯時間を自動でそっと調整します。
ホーム画面の主役はガラスの器に入ったバッテリーの液体で、今の状態が
「節電できてる（ひんやり）」か「節電できてない（炎）」かが一目で分かります。
液体は端末の傾きに合わせて揺れ、パーティクルで波打ちます。

- パッケージ名: `com.taigashinohara.mochimori`
- 権限: 画面設定の変更（WRITE_SETTINGS）／通知（POST_NOTIFICATIONS）／振動（VIBRATE）の3つだけ
- デザイン: Material Design 3 ＋ Simple Glass（UIアート）

## ドキュメント

- 設計: [files/DESIGN.md](files/DESIGN.md)
- アート方針: [docs/ART_DIRECTION.md](docs/ART_DIRECTION.md)
- UIカタログ: [docs/UI_CATALOG.md](docs/UI_CATALOG.md)
- 各種レビュー記録: [docs/](docs/)

## ビルド

通常の Android 開発環境でビルドできます（依存解決は google() / mavenCentral()、Gradle 8.9 + JDK 21 で確認）。

```sh
gradle :app:assembleRelease
```

署名鍵はリポジトリに含まれていません。リリースビルドには自分のキーストアを
`.local/signing.properties` に用意してください（形式は `app/build.gradle.kts` 参照）。

## ライセンス

MIT License
