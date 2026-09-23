(ns ps-web.cli
  "Command-line entry point for PowerSync web app tests."
  (:require [causal.checker.mww
             [stats :as stats]
             [util :as util]]
            [clojure.string :as str]
            [jepsen
             [checker :as checker]
             [cli :as cli]
             [generator :as gen]
             [tests :as tests]]
            [jepsen.checker.timeline :as timeline]
            [jepsen.os.debian :as debian]
            [ps-web
             [nemesis :as nemesis]
             [workload :as workload]]))

(def nemeses
  "A collection of valid nemeses."
  #{:no-op})

(def all-nemeses
  "Combinations of nemeses for tests"
  [[]
   [:no-op]])

(def special-nemeses
  "A map of special nemesis names to collections of faults"
  {:none []
   :all  nemeses})

(defn parse-nemesis-spec
  "Takes a comma-separated nemesis string and returns a collection of keyword
  faults."
  [spec]
  (->> (str/split spec #",")
       (map keyword)
       (mapcat #(get special-nemeses % [%]))))

(defn test-name
  "Given opts, returns a meaningful test name."
  [{:keys [lazyfs? nemesis nodes rate time-limit workload] :as _opts}]
  (let [nemesis (into #{} nemesis)]
    (str (name workload)
         "-" (str/join "," (map name nemesis))
         (when lazyfs?
           "-lazyfs")
         "-" (count nodes) "ps"
         "-" rate "tps"
         "-" time-limit "s")))

(defn powersync-test
  "Given options from the CLI, constructs a test map."
  [opts]
  (let [workload-name (:workload opts)
        workload ((workload/workload-map workload-name) opts)
        db       (:db workload)
        nemesis  (nemesis/nemesis-package
                  {:db                 db
                   :nodes              (:nodes opts)
                   :faults             (:nemesis opts)
                   :interval           (:nemesis-interval opts)
                   :disconnect-orderly {:targets [nil]}
                   :disconnect-random  {:targets [nil]}
                   :stop-start         {:targets [nil]}
                   :partition-sync     {:targets [nil]}
                   :partition-postgres {:targets [nil]}
                   :partition-both     {:targets [nil]}
                   :pause              {:targets [nil]}
                   :kill               {:targets [nil]}
                   :unsynced-data-report {:targets nil}})]
    (merge tests/noop-test
           opts
           {:name      (test-name opts)
            :os        debian/os
            :db        db
            :checker   (checker/compose
                        {:perf               (checker/perf
                                              {:nemeses (:perf nemesis)})
                         :timeline           (timeline/html)
                         :stats              (checker/stats)
                         :completions-by-node (stats/completions-by-node)
                         :exceptions         (checker/unhandled-exceptions)
                         :logs-ps-client     (checker/log-file-pattern #"(SEVERE)|(ERROR)" "TODO")
                         :workload           (:checker workload)})
            :client    (:client workload)
            :nemesis   (:nemesis nemesis)
            :generator (gen/phases
                        (gen/log "Workload with nemesis")
                        (->> (:generator workload)
                             (gen/stagger    (/ (:rate opts)))
                             (gen/nemesis    (:generator nemesis))
                             (gen/time-limit (:time-limit opts)))

                        (gen/log "Final nemesis")
                        (gen/nemesis (:final-generator nemesis))

                        (gen/log "Final workload")
                        (->> (:final-generator workload)
                             (gen/stagger (/ (:rate opts)))))})))

(def cli-opts
  "Command line options"
  [[nil "--client-timeout SECS" "The number of seconds to wait before timing out a client connection."
    :default  3
    :parse-fn parse-long
    :validate [pos? "Must be a positive integer"]]

   [nil "--key-count NUM" "The total number of keys."
    :default  util/key-count
    :parse-fn parse-long
    :validate [pos? "Must be a positive integer"]]

   [nil "--keys-txn NUM" "The number of keys to act on in a transactions."
    :default  4
    :parse-fn parse-long
    :validate [pos? "Must be a positive integer"]]

   [nil "--lazyfs? BOOLEAN" "Mount data dir in a lazy filesystem that can lose non fsync'd writes?"
    :default  false
    :parse-fn parse-boolean
    :validate [boolean? "Must be a boolean."]]

   [nil "--nemesis FAULTS" "A comma-separated list of nemesis faults to enable"
    :parse-fn parse-nemesis-spec
    :validate [(partial every? nemeses)
               (str "Faults must be " nemeses ", or the special faults all or none.")]]

   [nil "--nemesis-interval SECS" "Roughly how long between nemesis operations."
    :default 5
    :parse-fn parse-long
    :validate [pos? "Must be a positive number."]]

   ["-r" "--rate HZ" "Approximate request rate, in hz"
    :default 100
    :parse-fn parse-long
    :validate [pos? "Must be a positive number."]]

   ["-w" "--workload NAME" "What workload should we run?"
    :default  :no-op
    :parse-fn keyword
    :missing  (str "Must specify a workload: " (cli/one-of workload/workload-map))
    :validate [workload/workload-map (cli/one-of workload/workload-map)]]])

(defn all-tests
  "Turns CLI options into a sequence of tests."
  [opts]
  (let [nemeses   (if-let [n (:nemesis opts)] [n] all-nemeses)
        workloads (if-let [w (:workload opts)] [w] workload/all-workloads)]
    (for [n nemeses, w workloads, _i (range (:test-count opts))]
      (powersync-test (assoc opts :nemesis n :workload w)))))

(defn opt-fn
  "Transforms CLI options before execution."
  [parsed]
  parsed)

(defn -main
  "CLI.
   `lein run` to list commands."
  [& args]
  (cli/run! (merge (cli/single-test-cmd {:test-fn  powersync-test
                                         :opt-spec cli-opts
                                         :opt-fn   opt-fn})
                   (cli/test-all-cmd {:tests-fn all-tests
                                      :opt-spec cli-opts
                                      :opt-fn   opt-fn})
                   (cli/serve-cmd))
            args))
