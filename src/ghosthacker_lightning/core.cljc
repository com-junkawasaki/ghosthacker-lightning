(ns ghosthacker-lightning.core
  "GHOST HACKER: LIGHTNING -- wave-based bullet-hell shooter core
  (ADR-2607023200 #10: portfolio title #10, Ren solo,
  \"Daemon大量発生に対抗する弾幕シューティング\").

  Pure, host-free judgment/state engine. Deliberately NOT a continuous
  physics/collision bullet-hell engine -- per ADR-2607023200's brief, this
  is a small, honest turn/wave abstraction that captures the genre's shape
  (continuous pressure across waves of enemies) without simulating bullets
  in flight:

  - A fixed sequence of *waves* (`ghosthacker-lightning.waves`), each with
    a Daemon count and a fixed number of shots to fire at them (a Daemon
    goes down in exactly one hit, for scope -- no HP/multi-hit Daemons,
    no upgrades, no weapon types).
  - Each shot is fired one at a time via `fire-shot`, which takes the
    current state plus a `hit?` boolean the HOST determines. The host
    (terminal.clj / web.cljs) determines that boolean from real elapsed
    time against a periodic \"target flash\" schedule -- this namespace
    supplies that timing judgment itself (`flash-schedule` /
    `judge-shot-timing`) as a small, self-contained helper. LIGHTNING is
    mechanically and thematically distinct enough from the beat-grid
    rhythm titles (FLOW/HARMONY, `ghosthacker-groove-core`) to warrant its
    own timing helper rather than importing groove-core -- unlike DUET,
    which is explicitly framed as reusing the SAME Ghost Battle mechanic.
  - If a wave ends (shots exhausted) with Daemons still standing,
    `:health` drops by the number of surviving Daemons in that wave --
    the wave-pressure mechanic that makes LIGHTNING a survival shooter
    rather than a pure accuracy test. The run ends when either all waves
    are cleared/attempted or `:health` reaches 0.

  No rendering/input/audio I/O lives here -- those are host adapters
  layered on top, same split as every other title in this portfolio."
  )

;; --- timing (LIGHTNING's own -- not ghosthacker-groove-core) -----------

(def default-flash-interval-ms
  "既定のtarget-flash周期(ms)。ホストアダプタはこの間隔で発光/合成音を
   周期的に鳴らし、プレイヤーはそれに合わせて撃つ。"
  600)

(def default-hit-window-ms
  "既定のhit判定窓(ms)。最も近いflashからこのms以内に撃てればhit。"
  150)

(defn flash-schedule
  "start-time-msを起点に、interval-ms間隔でcount回分のflash絶対時刻(ms)を
   昇順vectorで返す(FLOWのbeat-scheduleに相当するLIGHTNING独自版)。"
  ([start-time-ms count] (flash-schedule start-time-ms count default-flash-interval-ms))
  ([start-time-ms count interval-ms]
   {:pre [(pos? interval-ms) (nat-int? count)]}
   (vec (for [i (range count)] (+ start-time-ms (* i interval-ms))))))

(defn- abs-ms
  "cljc両対応の絶対値(JVM/CLJSともに`Math/abs`のオーバーロード差異に
   触れないための自前実装)。"
  [x]
  (if (neg? x) (- x) x))

(defn nearest-flash-delta-ms
  "t(絶対ms)から、schedule中最も近いflashまでの符号なし距離(ms)を返す。
   scheduleが空ならnil。"
  [t schedule]
  (when (seq schedule)
    (apply min (map #(abs-ms (- t %)) schedule))))

(defn shot-hit?
  "delta-ms(nearest-flash-delta-msの結果)がwindow-ms以内ならhit。
   delta-msがnil(scheduleが空)ならfalse。"
  ([delta-ms] (shot-hit? delta-ms default-hit-window-ms))
  ([delta-ms window-ms]
   (boolean (and (some? delta-ms) (<= delta-ms window-ms)))))

(defn judge-shot-timing
  "t(絶対ms)がscheduleのいずれかのflashからwindow-ms以内ならhit。
   nearest-flash-delta-ms + shot-hit?の合成(ホストアダプタが1発ごとに
   呼ぶ最短経路)。"
  ([t schedule] (judge-shot-timing t schedule default-hit-window-ms))
  ([t schedule window-ms]
   (shot-hit? (nearest-flash-delta-ms t schedule) window-ms)))

;; --- wave / health / score state ----------------------------------------

(def initial-health
  "既定の初期health。1wave分の未撃墜Daemonがそのままhealthを削るため、
   単純な『残機』ではなくwave失敗の深刻度を吸収するバッファとして、
   最終waveのDaemon数(waves.cljc既定で最大5)より十分大きい値にしてある
   -- 1敗で即詰みにならない程度の緩衝。"
  10)

(def initial-state
  "run開始時の状態。daemons-left/shots-leftは『wave未開始』を0で表す
   (start-waveが実際のwave値を入れるまでは常に非active)。"
  {:wave-index 0
   :health initial-health
   :daemons-left 0
   :shots-left 0
   :daemons-downed 0
   :shots-fired 0
   :hits 0
   :waves-cleared 0
   :log []})

(defn current-wave
  "waves(各要素{:label :daemons :shots ...})のうち、stateが現在
   対峙すべきwaveを返す。全wave消化後はnil。"
  [state waves]
  (nth waves (:wave-index state) nil))

(defn game-over?
  "healthが尽きたか。"
  [state]
  (<= (:health state) 0))

(defn all-waves-done?
  "waves全てを消化し終えたか(wave-indexがwaves数に達した)。"
  [state waves]
  (>= (:wave-index state) (count waves)))

(defn finished?
  "runが終了条件(health尽きる、または全wave消化)に達したか。"
  [state waves]
  (or (game-over? state) (all-waves-done? state waves)))

(defn start-wave
  "現在のwave-indexに対応するwaveから、daemons-left/shots-leftを設定する。
   既にfinished?ならstateをそのまま返す(呼び出し側の責任でfinished?を
   先にチェックすること)。"
  [state waves]
  (if (finished? state waves)
    state
    (let [wave (current-wave state waves)]
      (assoc state
             :daemons-left (:daemons wave)
             :shots-left (:shots wave)))))

(defn wave-active?
  "現在wave-active(daemons-left/shots-leftが両方とも正)かどうか。
   start-wave前や、shots/daemons尽き後はfalse。"
  [state]
  (and (pos? (:daemons-left state)) (pos? (:shots-left state))))

(defn fire-shot
  "1発分の発射をstateに適用する。hit?がtrueならdaemons-leftを1減らし、
   daemons-downed/hitsを加算する。真偽に関わらずshots-left/shots-firedは
   必ず1減る/増える。wave-active?でない状態(未開始/既に終了済み)なら
   stateをそのまま返す(ホストが誤って撃ち尽くした後に呼んでも安全)。"
  [state hit?]
  (if-not (wave-active? state)
    state
    (-> state
        (update :shots-fired inc)
        (update :shots-left dec)
        (update :log conj (boolean hit?))
        (cond-> hit? (-> (update :daemons-left dec)
                         (update :daemons-downed inc)
                         (update :hits inc))))))

(defn wave-over?
  "現在wave-startedな前提で、daemons-leftが0(=wave clear)、または
   shots-leftが尽きた(=wave失敗もしくは丁度撃墜完了)かで終了判定する。
   start-wave前に呼ぶと(0,0からの成り立ちで)常にtrueを返す点に注意
   -- terminal.clj/web.cljsはstart-wave直後のみこれをチェックする。"
  [state]
  (or (zero? (:daemons-left state))
      (<= (:shots-left state) 0)))

(defn end-wave
  "wave終了時の処理: daemons-leftが残っていれば(=クリア失敗)、その数
   だけhealthを減らす(0未満にはならない)。0ならwaves-clearedを加算する。
   wave-indexを1進め、daemons-left/shots-leftを0にリセットする。"
  [state]
  (let [remaining (:daemons-left state)
        cleared? (zero? remaining)]
    (-> state
        (update :health #(max 0 (- % remaining)))
        (cond-> cleared? (update :waves-cleared inc))
        (update :wave-index inc)
        (assoc :daemons-left 0 :shots-left 0))))

(defn play-wave
  "waves中の現在waveをstart-waveしてから、hits(bool列)で順に発射し、
   wave-over?になるかhitsを使い切るまで進める。使い切って終わっても
   （撃ち尽くしていなくても）そこで打ち切り、end-waveまで適用する
   (ホストが途中で中断した場合に相当)。finished?なら何もせず返す。"
  [state waves hits]
  (if (finished? state waves)
    state
    (loop [state (start-wave state waves)
           hs (seq hits)]
      (if (or (wave-over? state) (nil? hs))
        (end-wave state)
        (recur (fire-shot state (first hs)) (next hs))))))

(defn play
  "全waveをwaves-hits(wave毎のhits bool列を並べたベクタ)で順に再生する。
   finished?(health尽きる/全wave終了)になったら、waves-hitsが残っていても
   打ち切る。"
  [waves waves-hits]
  (loop [state initial-state
         wh (seq waves-hits)]
    (if (or (finished? state waves) (nil? wh))
      state
      (recur (play-wave state waves (first wh)) (next wh)))))

;; --- summary / grade -----------------------------------------------------

(defn accuracy
  "shots-firedのうちhitsが占める割合(0.0〜1.0)。shots-firedが0なら1.0
   (未プレイをミス扱いにしない)。"
  [state]
  (if (zero? (:shots-fired state))
    1.0
    (/ (:hits state) (double (:shots-fired state)))))

(defn total-daemons
  "waves全体のDaemon総数(gradeのdowned-ratio計算に使う)。"
  [waves]
  (reduce + (map :daemons waves)))

(defn grade
  "runの最終評価。healthが尽きていれば(生き延びられなかった)問答無用で
   :terminated。生き残っていれば、waves全体に対するdaemons-downedの
   割合(downed-ratio)としてhealthが満タンかどうかも加味して判定する。"
  [state waves]
  (let [total (total-daemons waves)
        downed-ratio (if (zero? total) 1.0 (/ (:daemons-downed state) (double total)))]
    (cond
      (game-over? state) :terminated
      (and (>= downed-ratio 0.95) (= (:health state) initial-health)) :s
      (>= downed-ratio 0.8) :a
      (>= downed-ratio 0.6) :b
      (>= downed-ratio 0.4) :c
      :else :d)))

(defn summary
  "runの結果サマリ。ホストアダプタのリザルト画面にそのまま渡せる形。"
  [state waves]
  {:daemons-downed (:daemons-downed state)
   :waves-cleared (:waves-cleared state)
   :waves-total (count waves)
   :health (:health state)
   :accuracy (accuracy state)
   :grade (grade state waves)
   :game-over? (game-over? state)})
