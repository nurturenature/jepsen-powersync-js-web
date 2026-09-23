(ns ps-web.workload
  "Workloads for Jepsen to use in testing a PowerSync web app."
  (:require [jepsen
             [checker :as checker]
             [client :as client]
             [db :as db]]))

(defn no-op
  "A no-op PowerSync web app workload."
  [_opts]
  {:db              db/noop
   :client          client/noop
   :generator       nil
   :final-generator nil
   :checker         (checker/unbridled-optimism)})

(def all-workloads
  "A set of all workloads"
  #{:no-op})

(def workload-map
  "A map of workload names to functions that take CLI options and return
  workload maps."
  {:no-op no-op})
