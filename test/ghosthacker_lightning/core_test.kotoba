(ns ghosthacker-lightning.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [ghosthacker-lightning.core :as core]
            [ghosthacker-lightning.waves :as waves]))

;; --- timing ---------------------------------------------------------------

(deftest flash-schedule-test
  (testing "start-time-ms起点、interval-ms間隔でcount回分の絶対時刻を返す"
    (is (= [1000 1600 2200] (core/flash-schedule 1000 3 600)))
    (is (= [1000 1600 2200] (core/flash-schedule 1000 3))) ; default interval
    (is (= [] (core/flash-schedule 1000 0)))))

(deftest nearest-flash-delta-ms-test
  (testing "最も近いflashまでの符号なし距離"
    (is (= 0 (core/nearest-flash-delta-ms 1000 [1000 1600 2200])))
    (is (= 50 (core/nearest-flash-delta-ms 1050 [1000 1600 2200])))
    (is (= 50 (core/nearest-flash-delta-ms 1550 [1000 1600 2200])))
    (is (= 200 (core/nearest-flash-delta-ms 2400 [1000 1600 2200]))))
  (testing "scheduleが空ならnil"
    (is (nil? (core/nearest-flash-delta-ms 1000 [])))))

(deftest shot-hit-boundary-test
  (testing "delta-msがwindow-ms以内(境界含む)ならhit"
    (is (true? (core/shot-hit? 0 150)))
    (is (true? (core/shot-hit? 150 150)))
    (is (false? (core/shot-hit? 151 150)))
    (is (false? (core/shot-hit? 1000 150))))
  (testing "デフォルトwindow(150ms)での境界"
    (is (true? (core/shot-hit? core/default-hit-window-ms)))
    (is (false? (core/shot-hit? (inc core/default-hit-window-ms)))))
  (testing "delta-msがnilならfalse"
    (is (false? (core/shot-hit? nil 150)))))

(deftest judge-shot-timing-test
  (let [schedule [1000 1600 2200]]
    (testing "flashちょうど、および窓内はhit"
      (is (true? (core/judge-shot-timing 1000 schedule 150)))
      (is (true? (core/judge-shot-timing 1140 schedule 150)))
      (is (true? (core/judge-shot-timing 1600 schedule)))) ; default window
    (testing "窓外はmiss"
      (is (false? (core/judge-shot-timing 1200 schedule 150)))
      (is (false? (core/judge-shot-timing 1300 schedule))))))

;; --- fire-shot / wave state -------------------------------------------------

(deftest fire-shot-hit-transition-test
  (testing "hitはdaemons-left/shots-leftを両方減らし、downed/hits/shots-firedを積む"
    (let [state (core/start-wave core/initial-state [{:label :w :daemons 3 :shots 5}])
          next (core/fire-shot state true)]
      (is (= 2 (:daemons-left next)))
      (is (= 4 (:shots-left next)))
      (is (= 1 (:daemons-downed next)))
      (is (= 1 (:hits next)))
      (is (= 1 (:shots-fired next)))
      (is (= [true] (:log next))))))

(deftest fire-shot-miss-transition-test
  (testing "missはshots-leftのみ減り、daemons-left/downed/hitsは不変"
    (let [state (core/start-wave core/initial-state [{:label :w :daemons 3 :shots 5}])
          next (core/fire-shot state false)]
      (is (= 3 (:daemons-left next)))
      (is (= 4 (:shots-left next)))
      (is (= 0 (:daemons-downed next)))
      (is (= 0 (:hits next)))
      (is (= 1 (:shots-fired next)))
      (is (= [false] (:log next))))))

(deftest fire-shot-inactive-noop-test
  (testing "wave未開始(daemons-left/shots-leftが0)ならfire-shotは何もしない"
    (is (= core/initial-state (core/fire-shot core/initial-state true)))
    (is (= core/initial-state (core/fire-shot core/initial-state false))))
  (testing "shots尽きた後のfire-shotも何もしない"
    (let [exhausted (assoc (core/start-wave core/initial-state [{:label :w :daemons 1 :shots 1}])
                            :shots-left 0)]
      (is (= exhausted (core/fire-shot exhausted true))))))

(deftest wave-over-boundary-test
  (testing "daemons-leftが0、またはshots-leftが0以下ならwave-over"
    (is (core/wave-over? {:daemons-left 0 :shots-left 5}))
    (is (core/wave-over? {:daemons-left 3 :shots-left 0}))
    (is (core/wave-over? {:daemons-left 0 :shots-left 0}))
    (is (not (core/wave-over? {:daemons-left 1 :shots-left 1})))))

(deftest wave-active-test
  (is (not (core/wave-active? core/initial-state)))
  (is (core/wave-active? {:daemons-left 1 :shots-left 1}))
  (is (not (core/wave-active? {:daemons-left 0 :shots-left 1})))
  (is (not (core/wave-active? {:daemons-left 1 :shots-left 0}))))

;; --- start-wave / finished? / game-over? -----------------------------------

(def ^:private sample-waves
  [{:label :a :daemons 2 :shots 4}
   {:label :b :daemons 3 :shots 4}])

(deftest start-wave-and-current-wave-test
  (testing "start-waveはcurrent-waveの:daemons/:shotsを取り込む"
    (let [state (core/start-wave core/initial-state sample-waves)]
      (is (= 2 (:daemons-left state)))
      (is (= 4 (:shots-left state)))))
  (testing "finished?ならstart-waveは何もしない"
    (let [done (assoc core/initial-state :wave-index (count sample-waves))]
      (is (= done (core/start-wave done sample-waves))))))

(deftest finished-and-game-over-test
  (testing "healthが0以下ならgame-over?"
    (is (core/game-over? {:health 0}))
    (is (core/game-over? {:health -1}))
    (is (not (core/game-over? {:health 1}))))
  (testing "wave-indexがwaves数に達したらall-waves-done?"
    (is (core/all-waves-done? {:wave-index 2} sample-waves))
    (is (not (core/all-waves-done? {:wave-index 1} sample-waves))))
  (testing "finished?はgame-over?かall-waves-done?のいずれか"
    (is (core/finished? {:health 0 :wave-index 0} sample-waves))
    (is (core/finished? {:health 5 :wave-index 2} sample-waves))
    (is (not (core/finished? {:health 5 :wave-index 0} sample-waves)))))

;; --- end-wave ---------------------------------------------------------------

(deftest end-wave-cleared-test
  (testing "daemons-leftが0(全滅=クリア)ならwaves-clearedを積み、healthは減らない"
    (let [state (-> core/initial-state
                    (assoc :health 10 :daemons-left 0 :shots-left 2 :wave-index 0)
                    core/end-wave)]
      (is (= 10 (:health state)))
      (is (= 1 (:waves-cleared state)))
      (is (= 1 (:wave-index state)))
      (is (= 0 (:daemons-left state)))
      (is (= 0 (:shots-left state))))))

(deftest end-wave-failed-test
  (testing "daemons-leftが残っていれば、その数だけhealthを減らしwaves-clearedは増えない"
    (let [state (-> core/initial-state
                    (assoc :health 10 :daemons-left 3 :shots-left 0 :wave-index 0)
                    core/end-wave)]
      (is (= 7 (:health state)))
      (is (= 0 (:waves-cleared state)))
      (is (= 1 (:wave-index state))))))

(deftest end-wave-health-clamped-at-zero-test
  (testing "残りDaemon数がhealthを超えても0未満にはならない"
    (let [state (-> core/initial-state
                    (assoc :health 2 :daemons-left 5 :shots-left 0)
                    core/end-wave)]
      (is (= 0 (:health state))))))

;; --- play-wave / play (full playthrough) ------------------------------------

(deftest play-wave-full-clear-test
  (testing "全shot hitならwaveを全滅させてクリアする"
    (let [waves [{:label :w :daemons 3 :shots 5}]
          state (core/play-wave core/initial-state waves [true true true])]
      (is (= 0 (:daemons-left state)))
      (is (= 3 (:daemons-downed state)))
      (is (= 1 (:waves-cleared state)))
      (is (= (:health core/initial-state) (:health state)))
      (is (= 1 (:wave-index state))))))

(deftest play-wave-all-miss-test
  (testing "全shot missならwave失敗、healthがdaemons分減る(end-wave後daemons-left/
            shots-leftは0にリセットされるので、代わりにdaemons-downed/waves-cleared/
            healthで失敗を検証する)"
    (let [waves [{:label :w :daemons 3 :shots 5}]
          state (core/play-wave core/initial-state waves [false false false false false])]
      (is (= 0 (:daemons-downed state)))
      (is (= 0 (:waves-cleared state)))
      (is (= (- core/initial-health 3) (:health state))))))

(deftest play-wave-shots-run-out-before-hits-exhausted-test
  (testing "hitsベクタがshots数より短ければ、そこで打ち切ってend-waveを適用する"
    (let [waves [{:label :w :daemons 5 :shots 8}]
          state (core/play-wave core/initial-state waves [true true])]
      ;; 2 hits適用済みだがhits列がそこで尽きた -> shots-leftを使い切って
      ;; いなくてもend-waveされ、残りdaemons(3)分healthが減る
      (is (= 2 (:daemons-downed state)))
      (is (= (- core/initial-health 3) (:health state)))
      (is (= 1 (:wave-index state))))))

(deftest play-full-multi-wave-playthrough-test
  (testing "daemon-stormを最後まで、全waveクリア相当のhitsで再生するとgrade :s級になる"
    (let [waves-hits (mapv (fn [wave] (vec (repeat (:shots wave) true))) waves/daemon-storm)
          state (core/play waves/daemon-storm waves-hits)
          result (core/summary state waves/daemon-storm)]
      (is (= (count waves/daemon-storm) (:waves-cleared result)))
      (is (= (core/total-daemons waves/daemon-storm) (:daemons-downed result)))
      (is (= core/initial-health (:health result)))
      (is (= 5 (:waves-total result)))
      (is (not (:game-over? result)))
      (is (= :s (:grade result)))))
  (testing "全miss続きだとhealthが早々に尽き、途中のwaveで打ち切られる(finished?)"
    (let [waves-hits (mapv (fn [wave] (vec (repeat (:shots wave) false))) waves/daemon-storm)
          state (core/play waves/daemon-storm waves-hits)
          result (core/summary state waves/daemon-storm)]
      (is (core/game-over? state))
      (is (:game-over? result))
      (is (= :terminated (:grade result)))
      (is (< (:wave-index state) (count waves/daemon-storm)))
      (is (zero? (:daemons-downed result))))))

;; --- accuracy / grade / summary ----------------------------------------------

(deftest accuracy-test
  (testing "shots-fired 0なら1.0(未プレイをミス扱いにしない)"
    (is (= 1.0 (core/accuracy core/initial-state))))
  (testing "hits/shots-firedの割合"
    (is (= 0.5 (core/accuracy {:shots-fired 4 :hits 2})))
    (is (= 1.0 (core/accuracy {:shots-fired 3 :hits 3})))
    (is (= 0.0 (core/accuracy {:shots-fired 3 :hits 0})))))

(deftest grade-thresholds-test
  (let [waves [{:label :w :daemons 10 :shots 10}]]
    (testing "game-overなら問答無用でterminated"
      (is (= :terminated (core/grade {:health 0 :daemons-downed 10} waves))))
    (testing "健康満タン+ほぼ全滅(>=0.95)で:s"
      (is (= :s (core/grade {:health core/initial-health :daemons-downed 10} waves))))
    (testing "健康が減っていれば0.95撃墜でも:s未満(:a)"
      (is (= :a (core/grade {:health (dec core/initial-health) :daemons-downed 10} waves))))
    (testing "0.8/0.6/0.4のしきい値"
      (is (= :a (core/grade {:health 1 :daemons-downed 8} waves)))
      (is (= :b (core/grade {:health 1 :daemons-downed 6} waves)))
      (is (= :c (core/grade {:health 1 :daemons-downed 4} waves)))
      (is (= :d (core/grade {:health 1 :daemons-downed 3} waves))))))

(deftest summary-shape-test
  (testing "summaryはホストアダプタが必要とする全キーを返す"
    (let [state (core/play-wave core/initial-state [{:label :w :daemons 3 :shots 5}]
                                 [true true false])
          result (core/summary state [{:label :w :daemons 3 :shots 5}])]
      (is (= #{:daemons-downed :waves-cleared :waves-total :health :accuracy :grade :game-over?}
             (set (keys result))))
      (is (= 2 (:daemons-downed result)))
      (is (= 1 (:waves-total result))))))
