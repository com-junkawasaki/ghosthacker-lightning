(ns ghosthacker-lightning.waves
  "GHOST HACKER: LIGHTNING -- sample wave sequence (pure data,
  ADR-2607023200 #10).

  `daemon-storm`: 情報場に発生したDaemonの大量発生を波状に迎え撃つ、
  5wave構成の短い1本。各waveは{:label :daemons :shots :window-ms}を
  持つ:

  - `:daemons` -- そのwaveのDaemon数(1体1hitで撃墜、シンプルさのため
    HP/多段ヒットは無し)。
  - `:shots` -- そのwaveでプレイヤーが撃てる総弾数。
  - `:window-ms` -- そのwaveのhit判定窓(ms、core/default-hit-window-msの
    上書き)。waveが進むほど窓を狭めることで『threat level(脅威度)』を
    表現する -- Daemon数を増やすだけでなく、後半waveほどタイミングの
    精度が要求される。

  終盤ほどDaemonが増え窓が狭まる、無理のない難度カーブ。"
  )

(def daemon-storm
  [{:label :recon-swarm          :daemons 3 :shots 8  :window-ms 180}
   {:label :breach-wave          :daemons 4 :shots 8  :window-ms 160}
   {:label :overflow-cluster     :daemons 4 :shots 9  :window-ms 150}
   {:label :cascade-failure      :daemons 5 :shots 9  :window-ms 140}
   {:label :root-daemon-vanguard :daemons 5 :shots 10 :window-ms 120}])
