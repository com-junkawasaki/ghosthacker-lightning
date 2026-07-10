(ns ghosthacker-lightning.terminal-test
  "terminalの`-main`はshutdown-agentsを呼ぶため、共有JVMで動くテスト
   プロセス全体のagentスレッドプールを止めてしまう(以降のテストが
   futureを使えなくなる)。よって-mainそのものはテストしない。同じ理由で
   play-wave!/play-loop!（内部でrun-ticker!経由のfutureを起動する）も
   直接テストしない -- future-cancelで即座に止めても、テストプロセスの
   agent送出プールに非daemonスレッドが残りうる(実際に問題になるのは
   -mainのshutdown-agents欠落と同型)ため、futureを一切起動しない
   fire-wave!/countdown!/window-for（private var経由）だけを直接叩く。
   実プロセスとしての-main自体は手動検証済み(EOF/連打/全wave完走の
   いずれも正しく完了しプロセスがハングしないことを確認)。"
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [ghosthacker-lightning.core :as core]
            [ghosthacker-lightning.terminal :as terminal]))

(def ^:private fire-wave! #'terminal/fire-wave!)
(def ^:private countdown! #'terminal/countdown!)
(def ^:private window-for #'terminal/window-for)

(defn- silently [thunk]
  (binding [*out* (java.io.StringWriter.)]
    (thunk)))

(defn- capture-out [thunk]
  (let [w (java.io.StringWriter.)]
    (binding [*out* w] (thunk))
    (str w)))

(deftest fire-wave-eof-boundary-test
  (testing "stdinがEOF(空)ならshots-leftをゼロに落として即座に打ち切る(ハングしない)"
    (let [started (core/start-wave core/initial-state [{:label :w :daemons 3 :shots 4}])
          schedule (core/flash-schedule (System/currentTimeMillis) 4)
          state (silently #(with-in-str "" (fire-wave! started schedule 150)))]
      (is (= 0 (:shots-left state)))
      (is (= 3 (:daemons-left state)))
      (is (zero? (:shots-fired state))))))

(deftest fire-wave-partial-input-boundary-test
  (testing "shots数に満たない入力(途中でEOF)は、そこまでのfireで打ち切る"
    (let [started (core/start-wave core/initial-state [{:label :w :daemons 5 :shots 5}])
          schedule (core/flash-schedule (System/currentTimeMillis) 5)
          state (silently #(with-in-str "\n\n" (fire-wave! started schedule 150)))]
      (is (= 2 (:shots-fired state)))
      (is (= 0 (:shots-left state))) ; EOF後にshots-leftをゼロへ落とす
      (is (core/wave-over? state)))))

(deftest fire-wave-all-inputs-consumed-test
  (testing "shots数ぶん入力すればループを終える(その時点のjudgeは実時刻依存で
            hit/missどちらもあり得るためshots-firedのみ検証する)"
    (let [started (core/start-wave core/initial-state [{:label :w :daemons 8 :shots 3}])
          schedule (core/flash-schedule (System/currentTimeMillis) 3)
          state (silently #(with-in-str "\n\n\n" (fire-wave! started schedule 150)))]
      (is (= 3 (:shots-fired state)))
      (is (core/wave-over? state)))))

(deftest fire-wave-stops-when-wave-already-clear-test
  (testing "daemons-leftが既に0(全滅済み)ならread-lineを1回も読まず即返す"
    (let [cleared (assoc (core/start-wave core/initial-state [{:label :w :daemons 2 :shots 4}])
                          :daemons-left 0)
          schedule (core/flash-schedule (System/currentTimeMillis) 4)]
      ;; with-in-strを渡さない(=stdinを読めばエラーになる)ことで、
      ;; read-lineが一切呼ばれないことを保証する。
      (is (= cleared (fire-wave! cleared schedule 150))))))

(deftest countdown-test
  (testing "3, 2, 1, FIRE!の順で表示される（高速化のためinterval-msを小さくする）"
    (let [output (capture-out #(countdown! 5))]
      (is (= ["3" "2" "1" "FIRE!"] (str/split-lines output))))))

(deftest window-for-test
  (testing ":window-msが指定されていればそれを使う"
    (is (= 120 (window-for {:label :w :daemons 1 :shots 1 :window-ms 120}))))
  (testing "未指定ならcore/default-hit-window-msにfallbackする"
    (is (= core/default-hit-window-ms (window-for {:label :w :daemons 1 :shots 1})))))
