# Changelog

pure `.cljc` wave/health/timing核（`ghosthacker-lightning.core`）と、
それを使うプロトタイプ実装の変更履歴（ADR-2607023200 #10）。

## Unreleased

- 初期実装: `core.cljc`（wave単位のfire-shot/wave-over?/start-wave/
  end-wave状態遷移、LIGHTNING独自のtarget-flashタイミング判定
  `flash-schedule`/`judge-shot-timing` -- `ghosthacker-groove-core`とは
  依存関係も判定モデルも独立、DUETと違いLIGHTNINGは別mechanicのため）、
  `waves.cljc`（サンプルwaveシーケンス`daemon-storm`、5wave・難度カーブ
  つき）、`terminal.clj`（プレイ可能なEnterキー発射プロトタイプ、
  future製tickerで実時刻flash+read-line判定）、`web.cljs`（ブラウザhost
  アダプタ、reagent+Web Audio、ADR-2607100900 follow-up (b)）。headless
  DOM上で実keydown操作による通し（START→カウントダウン→複数wave分の
  Space連打→result画面）を検証済み。
