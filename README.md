# GHOST HACKER: LIGHTNING

![test](https://github.com/com-junkawasaki/ghosthacker-lightning/actions/workflows/test.yml/badge.svg)

Ghost Hacker ゲームポートフォリオ第10弾(最終ジャンル)。設計は
[ADR-2607023200](../../../90-docs/adr/2607023200-ghosthacker-game-portfolio-flow.md)
（superproject `com-junkawasaki/root`、addendum 2）を参照。

[Ghost Hacker](https://github.com/com-junkawasaki/ghosthacker)（既存カノン: Ren/Nei、
「情報は物理だ」、情報場、Ghost Battle / Daemon Battle）を土台に、FreeTEMPOの
『Oriental Quaint.』（2005）収録曲 "Lightning" に由来する、10ジャンル展開の
第10弾(シューティング)。

## コンセプト

- **ジャンル**: シューティング（弾幕シューティング）
- **主人公**: Ren単独
- **コアループ**: 情報場に大量発生したDaemonの群れを、wave単位で迎え撃つ。
  本物の連続弾幕/衝突演算エンジンは作らず（ポートフォリオ全体の「小さく
  正直な規模」に合わせ）、waveを固定シーケンスとした軽量な抽象化にした:
  各waveは決まった数のDaemon（1体1hitで撃墜、HP/多段ヒットは無し）と
  決まった総弾数を持ち、プレイヤーは周期的な「target flash」に合わせて
  1発ずつ撃つ。撃った瞬間の実経過時間がflashからの窓(window-ms)以内なら
  hit、外れればmiss。waveの弾を撃ち尽くしてもDaemonが残っていれば、その
  残数ぶん`:health`が減る——「反射神経で当てる」だけでなく「wave単位の
  継続的な圧力を生き延びる」という、シューティングらしい緊張感を最小限の
  ロジックで表現する。healthが尽きるか、全wave消化で終了、結果は
  撃墜数・生存wave数・health・gradeにまとめられる。

## 実装範囲

`src/ghosthacker_lightning/core.cljc` — pure、host-free。判定/state核:

- `flash-schedule`/`nearest-flash-delta-ms`/`shot-hit?`/`judge-shot-timing`
  — LIGHTNING**独自**のタイミング判定ヘルパー。FLOW/HARMONYの
  [ghosthacker-groove-core](https://github.com/com-junkawasaki/ghosthacker-groove-core)
  （ビートグリッド前提のジャッジ）とは依存関係も判定モデルも独立させて
  ある。理由: LIGHTNINGの核は「ビートに同期し続ける精度」ではなく
  「wave圧力の下での継続的な生存」で、DUET（同じGhost Battle機構の
  再利用と明言されているタイトル）とは異なり、それ自体で完結した別の
  小さなタイミング判定を持たせるのが妥当と判断した。
- `fire-shot`/`wave-over?`/`start-wave`/`end-wave` — 1wave分の発射・
  撃墜判定・wave終了時のhealth減少（残存Daemon数ぶん）を進める状態遷移
- `play-wave`/`play` — hits(bool列)からの通し再生
- `accuracy`/`total-daemons`/`grade`/`summary` — リザルト画面向けサマリ

`src/ghosthacker_lightning/waves.cljc` — サンプルのwaveシーケンス
（`daemon-storm`、5wave。Daemon数が増え、`:window-ms`(hit判定窓)が
狭まっていく難度カーブ）。

**プレイ可能な最小プロトタイプ**として `src/ghosthacker_lightning/terminal.clj`
がある。新規依存ゼロ（JVM標準の`future`/`read-line`/`System/currentTimeMillis`
のみ）で、バックグラウンドスレッドが実時刻でtarget flash(⚡)を刻みながら、
メインスレッドが`read-line`で入力を受けて実際の経過時間を判定する——
グラフィック/音声は無いが、実際に人がEnterキーを叩いて5wave分遊べる。

**ブラウザで遊べるホストアダプタ**が `src/ghosthacker_lightning/web.cljs`
（reagent、ADR-2607100900 follow-up (b)）: FLOW/HARMONYと同じくWeb Audio
の`AudioContext.currentTime`でtarget flashクロックと合成ブリップ音
（オシレーター、外部音声アセット不要）を駆動する。フェーズは
`:idle -> :countdown(初回のみ) -> :playing -> (:wave-intro -> :playing)* ->
:result`。Web Audio非対応環境（このリポジトリ自身のheadless検証等）では
`performance.now()`+無音に自動degrade。Spaceキーで発射。

変更履歴は [CHANGELOG.md](CHANGELOG.md)。

`ghosthacker` 本体の `ghosthacker.resources`(pure) /
`ghosthacker.import`(host adapter) と同型のレイヤ分離方針を踏襲している。

## 開発

```bash
clojure -M:test
```

Lint（clj-kondo、Clojars経由でHomebrew等の別インストール不要）:

```bash
clojure -M:lint
```

`main`へのpush/PRで `.github/workflows/test.yml` が自動でテスト+lintを実行する。

ターミナルで遊んでみる（Enterで発射、5wave完走かhealth 0で終了）:

```bash
clojure -M -m ghosthacker-lightning.terminal
```

ブラウザで遊んでみる（`npm install`は初回のみ、Spaceキーで発射）:

```bash
npm install
npx shadow-cljs watch app   # http://localhost:8303 で自動リロード開発
npx shadow-cljs release app # public/ に静的バンドルをビルド(デプロイ可能)
```

## ライセンス

MIT License — [LICENSE](LICENSE) 参照。
