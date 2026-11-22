package com.taigashinohara.mochimori

/**
 * The explanation shown BEFORE a checklist row is fixed (v0.3.25,
 * user request: 「いきなり設定画面に飛ぶのではなく、何をしたら
 * 節電になるのか／かわりに何ができなくなるのか／設定画面で実際に
 * どういじればいいのか」を先に示して).
 *
 * Until v0.3.24 tapping a row jumped straight to a settings screen
 * (or, for temperature, to the cooling dialog), and the user landed
 * on a screen with no idea what to change there. Since v0.3.25 a
 * tap first opens a small sheet with three parts, in the user's
 * own order. v0.3.25 showed it only for rows that were NOT saving
 * (SavingChecklist.Status.NG); the user then reported 「説明が出ない」
 * because on their phone most rows were already saving, so their
 * taps still jumped straight to settings. Since v0.3.26 EVERY row
 * opens the sheet first (see ChecklistSheet for the per-status
 * plan):
 *  1. what — what change saves battery, and why, in concrete terms.
 *     For items the app can write itself (brightness, screen-off
 *     timeout — both plain WRITE_SETTINGS values, the same writes
 *     ProfileManager and the home screen's fixBrightness already
 *     perform) the what says exactly what the app will change.
 *  2. tradeoff — what the user gives up, stated honestly. A fix
 *     whose cost is hidden is a fix the user cannot judge.
 *  3. steps — settings items only: the numbered taps on the screen
 *     the row's intent actually opens (the destinations are the
 *     ones SavingChecklist's tap handler has always used; see
 *     files/DESIGN.md §12.3).
 *
 * The sheet is shown only at the moment of tapping (後出し, B
 * rank): the checklist rows themselves keep their v0.3.24 form —
 * name, measured value, status mark — and gain no permanent text.
 *
 * Kind decides the sheet's primary button:
 *  - IN_APP: the app applies the change itself; button
 *    「この内容で直す」.
 *  - SETTINGS: the change lives on an Android settings screen;
 *    button 「設定を開く」.
 *  - APP_SCREEN: the destination is inside this app (the auto
 *    saving rule lives on the rules screen); the button names it.
 *  - INFO: nothing to switch (temperature) — the what carries the
 *    concrete actions instead, and the only button closes.
 *
 * Pure data, no Android imports: the unit tests pin that every one
 * of the 13 items carries a what and a tradeoff, and that steps
 * exist exactly for the settings items.
 */
object ChecklistGuide {

    enum class Kind { IN_APP, SETTINGS, APP_SCREEN, INFO }

    data class Guide(
        val kind: Kind,
        /** What change saves battery (and why), concretely. For
         *  IN_APP items: the exact change the app will make. */
        val what: String,
        /** What stops working / gets worse in exchange. */
        val tradeoff: String,
        /** Numbered steps on the opened settings screen. SETTINGS
         *  items only; empty for every other kind. */
        val steps: List<String> = emptyList(),
        /** The sheet's primary button label. */
        val actionLabel: String,
    )

    private val guides: Map<SavingChecklist.Item, Guide> = mapOf(
        SavingChecklist.Item.BRIGHTNESS to Guide(
            kind = Kind.IN_APP,
            what = "アプリが明るさを約30%（77/255）に下げます。画面の表示は電池を大きく使うので、下げると持ちが伸びます",
            tradeoff = "明るさの自動調整はオフになります。屋外など明るい場所では見づらくなります",
            actionLabel = "この内容で直す",
        ),
        SavingChecklist.Item.SCREEN_TIMEOUT to Guide(
            kind = Kind.IN_APP,
            what = "アプリが、画面が消えるまでの時間を30秒にします。使っていない間の表示が短くなり、その分の電池を節約できます",
            tradeoff = "読んでいる途中で触らないと、画面が消えてしまうことがあります",
            actionLabel = "この内容で直す",
        ),
        SavingChecklist.Item.TEMPERATURE to Guide(
            kind = Kind.INFO,
            what = "電池は熱くなると減りが早くなります。効くのはこの3つです。\n" +
                "・重いアプリ（ゲーム・動画）を閉じる\n" +
                "・充電しながらの使用を控える\n" +
                "・ケースを外して、涼しい場所で休ませる",
            tradeoff = "冷めるまで、ゲームや動画など重い操作は控える必要があります",
            actionLabel = "わかった",
        ),
        SavingChecklist.Item.POWER_SAVER to Guide(
            kind = Kind.SETTINGS,
            what = "省電力モード（バッテリーセーバー）をONにします。裏側の動作が抑えられ、電池が長持ちします",
            tradeoff = "メールやSNSの通知が遅れて届くことがあります",
            steps = listOf("開いた画面で「バッテリーセーバーを使う」をONにする"),
            actionLabel = "設定を開く",
        ),
        SavingChecklist.Item.AUTO_SAVING to Guide(
            kind = Kind.APP_SCREEN,
            what = "このアプリの自動節電をONにします。残量が30%以下になると、明るさと消灯時間を自動で節電側に切り替えます",
            tradeoff = "残量が減ると、明るさが自動で下がり、画面が早めに消えるようになります",
            actionLabel = "ルール画面を開く",
        ),
        SavingChecklist.Item.WIFI to Guide(
            kind = Kind.SETTINGS,
            what = "Wi-FiをOFFにします。つながっていない間も接続先を探し続けて電池を使うので、止めるとその分を節約できます",
            tradeoff = "フリーWi-Fiなどへ自動でつながらなくなります。モバイルデータの使用量が増えることがあります",
            steps = listOf("開いた画面で「Wi-Fi」のスイッチをOFFにする"),
            actionLabel = "設定を開く",
        ),
        SavingChecklist.Item.MOBILE_DATA to Guide(
            kind = Kind.SETTINGS,
            what = "モバイルデータをOFFにします。電波の送受信が止まる分、電池が持ちます",
            tradeoff = "Wi-Fiのない場所では、ネット・メール・SNSがすべて止まります",
            steps = listOf("開いた画面で「モバイルデータ」をOFFにする（見当たらない時は、設定の「ネットワークとインターネット」→ 使っているSIM の画面にあります）"),
            actionLabel = "設定を開く",
        ),
        SavingChecklist.Item.LOCATION to Guide(
            kind = Kind.SETTINGS,
            what = "位置情報をOFFにします。GPSなどの測位が止まり、その分の電池を使わなくなります",
            tradeoff = "地図や天気などのアプリが、現在地を使えなくなります",
            steps = listOf("開いた画面で「位置情報を使用」をOFFにする"),
            actionLabel = "設定を開く",
        ),
        SavingChecklist.Item.NFC to Guide(
            kind = Kind.SETTINGS,
            what = "NFCをOFFにします。かざす操作の待ち受けが止まります",
            tradeoff = "おサイフケータイやタッチ決済、交通系ICへのタッチが使えなくなります",
            steps = listOf("開いた画面で「NFCを使用」をOFFにする"),
            actionLabel = "設定を開く",
        ),
        SavingChecklist.Item.BLUETOOTH to Guide(
            kind = Kind.SETTINGS,
            what = "BluetoothをOFFにします。接続先を探す動作が止まります",
            tradeoff = "ワイヤレスイヤホンやスマートウォッチとの接続が切れます",
            steps = listOf("開いた画面で「Bluetooth」をOFFにする"),
            actionLabel = "設定を開く",
        ),
        SavingChecklist.Item.DARK_THEME to Guide(
            kind = Kind.SETTINGS,
            what = "ダークテーマをONにします。有機ELの画面では黒い部分はほとんど電池を使わないので、節約になります",
            tradeoff = "明るい場所では、画面が見づらいことがあります",
            steps = listOf("開いた画面で「ダークテーマ」をONにする"),
            actionLabel = "設定を開く",
        ),
        SavingChecklist.Item.HAPTIC_FEEDBACK to Guide(
            kind = Kind.SETTINGS,
            what = "タッチ時のバイブをOFFにします。タップのたびの小さな振動モーターの消費がなくなります",
            tradeoff = "タップした時の手応え（振動）がなくなります",
            steps = listOf(
                "開いた画面で「バイブレーションとハプティクス」をタップ",
                "「タッチ操作時のバイブ」をOFFにする",
            ),
            actionLabel = "設定を開く",
        ),
        SavingChecklist.Item.DATA_SAVER to Guide(
            kind = Kind.SETTINGS,
            what = "データセーバーをONにします。アプリの裏側の通信が抑えられ、通信に使う電池が減ります",
            tradeoff = "通知が遅れたり、画像の読み込みが遅くなったりすることがあります",
            steps = listOf(
                "開いた画面で「データセーバー」をタップ（見当たらない時は「ネットワークとインターネット」の中にあります）",
                "「データセーバーを使う」をONにする",
            ),
            actionLabel = "設定を開く",
        ),
    )

    /** The guide for one checklist item. Every item has one; a
     *  missing entry is a programming error, not a state. */
    fun forItem(item: SavingChecklist.Item): Guide = guides.getValue(item)
}

/**
 * How the explanation sheet behaves for one row tap (v0.3.26).
 *
 * v0.3.25 opened the sheet only for NG rows; OK / UNKNOWN rows
 * still jumped straight to their destination, and the user —
 * whose rows were mostly OK — reported 「説明が出ない」. Now every
 * status opens the sheet, and this pure plan decides, from the
 * row's status and the guide's kind, the three things the sheet
 * needs:
 *
 *  - statusLine: one honest line about the CURRENT state, shown
 *    at the top of the sheet. OK rows say the item is already
 *    saving; UNKNOWN rows say the state is still being read. NG
 *    needs no line (the sheet itself is the explanation of a
 *    problem) and INFO (temperature) has no saving state to
 *    report.
 *  - primaryAction: what the sheet's primary button does.
 *    APPLY_FIX only for an NG row the app can write itself —
 *    re-applying an already-OK value would be a lie of a button.
 *    Every other actionable row goes to the destination the tap
 *    has always led to (the settings screen that owns the value;
 *    for an OK in-app row that is the display settings, the same
 *    place its tap went before v0.3.25). INFO closes only.
 *  - actionLabel: the button's words, matching where it leads.
 *    An OK in-app row opens a settings screen, so its label is
 *    「設定を開く」, not the fix label; the auto-saving row still
 *    waiting on the WRITE_SETTINGS permission opens the
 *    permission screen, so its label says so.
 *
 * Pure function of (status, guide): the unit tests pin all
 * 4 statuses × 4 kinds.
 */
object ChecklistSheet {

    enum class PrimaryAction { APPLY_FIX, OPEN_DESTINATION, CLOSE_ONLY }

    data class Plan(
        /** One line about the current state, or null for none. */
        val statusLine: String?,
        val primaryAction: PrimaryAction,
        /** The primary button's label. */
        val actionLabel: String,
    )

    /** The current-state line for a status, or null when the
     *  status needs none (NG, INFO). */
    fun statusLine(status: SavingChecklist.Status): String? = when (status) {
        SavingChecklist.Status.OK -> "いまは節電できています"
        SavingChecklist.Status.UNKNOWN -> "いまの状態は確認中です"
        SavingChecklist.Status.NG, SavingChecklist.Status.INFO -> null
    }

    fun plan(status: SavingChecklist.Status, guide: ChecklistGuide.Guide): Plan {
        val action = when {
            guide.kind == ChecklistGuide.Kind.INFO -> PrimaryAction.CLOSE_ONLY
            status == SavingChecklist.Status.NG &&
                guide.kind == ChecklistGuide.Kind.IN_APP -> PrimaryAction.APPLY_FIX
            else -> PrimaryAction.OPEN_DESTINATION
        }
        val label = when {
            action != PrimaryAction.OPEN_DESTINATION -> guide.actionLabel
            // An in-app row that is not being fixed here leads to
            // the display settings screen that owns the value.
            guide.kind == ChecklistGuide.Kind.IN_APP -> "設定を開く"
            // The auto-saving row's UNKNOWN is the missing
            // WRITE_SETTINGS permission; its destination is the
            // permission screen, not the rules screen.
            guide.kind == ChecklistGuide.Kind.APP_SCREEN &&
                status == SavingChecklist.Status.UNKNOWN -> "許可の画面を開く"
            else -> guide.actionLabel
        }
        return Plan(statusLine(status), action, label)
    }
}
