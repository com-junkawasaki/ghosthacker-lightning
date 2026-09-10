(ns ghosthacker-lightning.terminal
  "GHOST HACKER: LIGHTNING — minimal terminal host adapter (playable prototype).

  A real, human-playable loop built with nothing beyond JVM/Clojure already
  in this project: a background thread (`future`) sleeps to each target-
  flash's wall-clock time and prints a tick (⚡); the main thread blocks on
  `read-line` for each shot and judges the real elapsed time against
  `ghosthacker-lightning.core` (this repo's OWN small timing helper, not
  ghosthacker-groove-core -- LIGHTNING's wave/health mechanic is distinct
  from the beat-grid rhythm titles). No audio/rendering -- just proof that
  the pure core can drive a genuinely timed, interactive loop end-to-end
  across a wave sequence.

  Run: clojure -M -m ghosthacker-lightning.terminal"
  (:require [ghosthacker-lightning.core :as core]
            [ghosthacker-lightning.waves :as waves]))

(def ^:private wave-lead-in-ms
  "waveの最初のflashまでのリードタイム(ms)。プレイヤーがread-lineに
   意識を戻す猶予。"
  500)

(defn- print-flash! []
  (print "⚡ ")
  (flush))

(defn- run-ticker!
  "schedule(絶対ms時刻の列)どおりにflashを印字するfutureを起動する。
   呼び出し側はwave終了時にfuture-cancelで止めること。"
  [schedule]
  (future
    (doseq [t schedule]
      (let [wait (- t (System/currentTimeMillis))]
        (when (pos? wait)
          (Thread/sleep wait)))
      (print-flash!))))

(defn- countdown!
  "「3, 2, 1, FIRE!」を1区切りごとの間隔で表示する。interval-msはFIRE!の
   瞬間の時刻(=最初のflashの基準時刻)に合わせやすくするための導入演出。"
  [interval-ms]
  (doseq [n [3 2 1]]
    (println n)
    (Thread/sleep interval-ms))
  (println "FIRE!"))

(defn- window-for
  "waveの:window-msを読む(未指定ならcore/default-hit-window-msにfallback)。
   waves.cljcの各waveがwindow-msを持たない場合でも安全に動くための、
   純粋かつhostアダプタローカルな読み出しヘルパー。"
  [wave]
  (:window-ms wave core/default-hit-window-ms))

(defn- fire-wave!
  "1wave分: 既にstart-wave済みのstateを受け取り、scheduleの長さ(=その
   waveのshots数)ぶんread-lineで発射を待つ。都度judge-shot-timingで
   実経過時間を判定し、fire-shotをstateに適用する。標準入力がEOF(nil)に
   なったら、残りshotsを0にして(=wave失敗として)打ち切る(実プレイで
   Ctrl-D、テストで空/短いstdinを渡した時の両方に対応)。wave-over?に
   なった時点(shots尽きる/daemons全滅)でも打ち切る。"
  [state schedule window-ms]
  (loop [state state i 0]
    (if (or (core/wave-over? state) (>= i (count schedule)))
      state
      (let [line (read-line)]
        (if (nil? line)
          (assoc state :shots-left 0)
          (let [now (System/currentTimeMillis)
                hit? (core/judge-shot-timing now schedule window-ms)
                next-state (core/fire-shot state hit?)]
            (println (format " -> %s (health %d, downed %d, daemons-left %d)"
                              (if hit? "HIT" "miss")
                              (:health next-state)
                              (:daemons-downed next-state)
                              (:daemons-left next-state)))
            (recur next-state (inc i))))))))

(defn- play-wave!
  "1wave分の通し: waveの見出しを表示し、start-waveしてschedule/tickerを
   組み立て、fire-wave!で撃たせてから(future-cancelしてから)end-waveを
   適用する。"
  [state waves]
  (let [wave (core/current-wave state waves)
        window-ms (window-for wave)
        started (core/start-wave state waves)
        start-time-ms (+ (System/currentTimeMillis) wave-lead-in-ms)
        schedule (core/flash-schedule start-time-ms (:shots wave))
        ticker (run-ticker! schedule)]
    (println (format "-- wave %d/%d: %s (daemons %d, shots %d) --"
                      (inc (:wave-index state)) (count waves)
                      (name (:label wave)) (:daemons wave) (:shots wave)))
    (let [fired (fire-wave! started schedule window-ms)]
      (future-cancel ticker)
      (core/end-wave fired))))

(defn- play-loop!
  "全waveを、それぞれplay-wave!で消化するまで繰り返す。finished?に
   なったら(health尽きる/全wave終了)打ち切る。"
  [waves]
  (loop [state core/initial-state]
    (if (core/finished? state waves)
      state
      (let [next-state (play-wave! state waves)]
        (println (format " wave result: health=%d waves-cleared=%d"
                          (:health next-state) (:waves-cleared next-state)))
        (recur next-state)))))

(defn -main
  "Entry point for `clojure -M -m ghosthacker-lightning.terminal`. See the
  ns docstring."
  [& _args]
  (println "GHOST HACKER: LIGHTNING — daemon-storm")
  (println "各waveの⚡フラッシュに合わせてEnterで発射(fire)。")
  (println "準備ができたらEnterで開始:")
  (read-line)
  (countdown! wave-lead-in-ms)
  (let [state (play-loop! waves/daemon-storm)
        result (core/summary state waves/daemon-storm)]
    (println)
    (println "=== RESULT ===")
    (println (format "waves-cleared=%d/%d daemons-downed=%d health=%d accuracy=%.2f grade=%s"
                      (:waves-cleared result)
                      (:waves-total result)
                      (:daemons-downed result)
                      (:health result)
                      (double (:accuracy result))
                      (name (:grade result))))
    ;; futureはclojure.lang.Agentの非daemonスレッドプールを使うため、
    ;; これを呼ばないとロジック完了後もJVMプロセスが終了せずハングする。
    (shutdown-agents)))
