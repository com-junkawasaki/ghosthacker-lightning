(ns ghosthacker-lightning.web
  "GHOST HACKER: LIGHTNING -- browser host adapter (ADR-2607100900 follow-up
  (b)). ClojureScript, not kotoba wasm/clojurewasm: real-time shot timing
  and audio are host-imports neither can provide yet (ADR-2607100030
  addendum 2's clojurewasm constraint).

  Same shot-timing shape as ghosthacker_lightning/terminal.clj: Web Audio
  drives both the target-flash clock (`AudioContext.currentTime`, more
  precise than `performance.now()` for scheduled audio) and a synthesized
  flash blip (an oscillator, no external asset needed), judged with
  `ghosthacker-lightning.core/judge-shot-timing` (LIGHTNING's OWN small
  timing helper, not ghosthacker-groove-core) against the nearest schedule
  entry. Falls back to `js/performance.now` with no sound when Web Audio
  is unavailable (e.g. this repo's own headless verification script), same
  graceful-degradation spirit as the rest of this monorepo's host-import
  facades.

  One input event (Space keydown) per shot, across a fixed wave sequence
  (`ghosthacker-lightning.waves/daemon-storm`): :idle -> :countdown (first
  wave only) -> :playing -> (:wave-intro -> :playing)* -> :result. Health
  drops when a wave ends with Daemons still standing; the run ends at
  :result either because all waves were attempted or health hit 0."
  (:require [reagent.core :as r]
            [reagent.dom :as rdom]
            [ghosthacker-lightning.core :as core]
            [ghosthacker-lightning.waves :as waves]))

;; --- clock / audio ----------------------------------------------------

(defonce ^:private !ctx (atom nil))

(defn- ensure-ctx! []
  (when-not @!ctx
    (when-let [ctor (or (.-AudioContext js/window) (.-webkitAudioContext js/window))]
      (reset! !ctx (new ctor))))
  @!ctx)

(defn- now-ms []
  (if-let [ctx @!ctx]
    (* 1000 (.-currentTime ctx))
    (.now js/performance)))

(defn- schedule-tick!
  "Schedules a short synthesized blip at absolute t-ms via the audio
  clock (sample-accurate, unlike setTimeout). No-ops silently with no
  AudioContext (headless / unsupported browser)."
  [t-ms freq]
  (when-let [ctx @!ctx]
    (let [t (/ t-ms 1000.0)
          osc (.createOscillator ctx)
          gain (.createGain ctx)]
      (set! (.-value (.-frequency osc)) freq)
      (.setValueAtTime (.-gain gain) 0.001 t)
      (.linearRampToValueAtTime (.-gain gain) 0.22 (+ t 0.005))
      (.exponentialRampToValueAtTime (.-gain gain) 0.001 (+ t 0.09))
      (.connect osc gain)
      (.connect gain (.-destination ctx))
      (.start osc t)
      (.stop osc (+ t 0.1)))))

;; --- state --------------------------------------------------------------

(def ^:private countdown-tick-ms
  "カウントダウンの1区切りの長さ(ms)。terminal.clj同様、最初のflashの
   基準時刻に合わせやすくするための導入演出用。"
  500)

(def ^:private wave-lead-in-ms
  "waveの最初のflashまでのリードタイム(ms)。"
  300)

(def ^:private wave-clear-pause-ms
  "wave間の一瞬の間(:wave-introの表示時間、ms)。"
  900)

(defonce state
  (r/atom {:phase :idle          ; :idle | :countdown | :wave-intro | :playing | :result
           :waves waves/daemon-storm
           :game-state nil
           :schedule nil
           :window-ms nil
           :last-judgment nil
           :countdown-label "3"}))

;; --- wave transitions -----------------------------------------------------

(defn- start-wave! []
  (let [{:keys [game-state waves]} @state
        wave (core/current-wave game-state waves)
        window-ms (:window-ms wave core/default-hit-window-ms)
        go-time-ms (+ (now-ms) wave-lead-in-ms)
        schedule (core/flash-schedule go-time-ms (:shots wave))
        started (core/start-wave game-state waves)]
    (doseq [t schedule] (schedule-tick! t 880))
    (swap! state assoc
           :phase :playing
           :game-state started
           :schedule schedule
           :window-ms window-ms
           :last-judgment nil)))

(defn- start-game! []
  (ensure-ctx!)
  (swap! state assoc
         :phase :countdown
         :game-state core/initial-state
         :schedule nil
         :window-ms nil
         :last-judgment nil
         :countdown-label "3")
  (let [go-time-ms (+ (now-ms) (* 3 countdown-tick-ms))]
    (schedule-tick! (- go-time-ms (* 3 countdown-tick-ms)) 440)
    (schedule-tick! (- go-time-ms (* 2 countdown-tick-ms)) 440)
    (schedule-tick! (- go-time-ms countdown-tick-ms) 440))
  (doseq [[i label] (map-indexed vector ["3" "2" "1"])]
    (js/setTimeout #(swap! state assoc :countdown-label label) (* i countdown-tick-ms)))
  (js/setTimeout start-wave! (* 3 countdown-tick-ms)))

(defn- fire! []
  (when (= (:phase @state) :playing)
    (let [{:keys [game-state schedule window-ms waves]} @state
          hit? (core/judge-shot-timing (now-ms) schedule window-ms)
          next-gs (core/fire-shot game-state hit?)
          judgment (if hit? :hit :miss)]
      (if (core/wave-over? next-gs)
        (let [ended (core/end-wave next-gs)]
          (if (core/finished? ended waves)
            (swap! state assoc :game-state ended :last-judgment judgment :phase :result)
            (do
              (swap! state assoc :game-state ended :last-judgment judgment :phase :wave-intro)
              (js/setTimeout start-wave! wave-clear-pause-ms))))
        (swap! state assoc :game-state next-gs :last-judgment judgment)))))

(defn- restart! []
  (swap! state assoc
         :phase :idle
         :game-state nil
         :schedule nil
         :window-ms nil
         :last-judgment nil
         :countdown-label "3"))

;; --- keyboard input ---------------------------------------------------

(defn- on-keydown [e]
  (when (= (.-code e) "Space")
    (.preventDefault e)
    (fire!)))

;; --- views ------------------------------------------------------------

(defn- health-bar [health]
  (let [pct (-> (/ health core/initial-health) (max 0) (min 1) (* 100))]
    [:div.lightning-health-track
     [:div.lightning-health-fill {:style {:width (str pct "%")}}]]))

(defn- start-screen []
  [:div.lightning-app
   [:h1 "GHOST HACKER: LIGHTNING"]
   [:p.lightning-sub "情報場に発生したDaemonの大群を、⚡フラッシュに合わせて撃ち抜け。"]
   [:button {:on-click start-game!} "START"]])

(defn- countdown-screen []
  [:div.lightning-app
   [:h1 "GHOST HACKER: LIGHTNING"]
   [:div.lightning-countdown (:countdown-label @state)]])

(defn- wave-intro-screen []
  (let [{:keys [game-state waves]} @state
        wave (core/current-wave game-state waves)]
    [:div.lightning-app
     [:h1 "GHOST HACKER: LIGHTNING"]
     [:div.lightning-wave-counter (str "wave " (inc (:wave-index game-state)) "/" (count waves))]
     [:div.lightning-wave-label (str (name (:label wave)) " incoming...")]
     (health-bar (:health game-state))]))

(defn- playing-screen []
  (let [{:keys [game-state last-judgment waves]} @state
        wave (core/current-wave game-state waves)]
    [:div.lightning-app
     [:h1 "GHOST HACKER: LIGHTNING"]
     [:div.lightning-hud
      [:span (str "wave " (inc (:wave-index game-state)) "/" (count waves))]
      [:span (str "daemons " (:daemons-left game-state) "/" (:daemons wave))]
      [:span (str "shots " (:shots-left game-state) "/" (:shots wave))]]
     (health-bar (:health game-state))
     [:div.lightning-judgment (when last-judgment (if (= last-judgment :hit) "HIT" "miss"))]
     [:p.lightning-hint "Space で発射"]]))

(defn- result-screen []
  (let [{:keys [game-state waves]} @state
        summary (core/summary game-state waves)]
    [:div.lightning-app
     [:h1 "GHOST HACKER: LIGHTNING"]
     [:h2 (str "grade: " (name (:grade summary)))]
     [:p (str "waves cleared " (:waves-cleared summary) "/" (:waves-total summary))]
     [:p (str "daemons downed " (:daemons-downed summary))]
     [:p (str "health " (:health summary) "/" core/initial-health)]
     [:p (str "accuracy " (.toFixed (* 100 (:accuracy summary)) 0) "%")]
     [:button {:on-click restart!} "もう一度"]]))

(defn app []
  (case (:phase @state)
    :countdown [countdown-screen]
    :wave-intro [wave-intro-screen]
    :playing [playing-screen]
    :result [result-screen]
    [start-screen]))

(defn ^:export mount []
  (when-let [el (.getElementById js/document "app")]
    (.addEventListener js/window "keydown" on-keydown)
    (rdom/render [app] el)))

(defn ^:export init [] (mount))
