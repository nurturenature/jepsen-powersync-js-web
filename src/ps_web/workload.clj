(ns ps-web.workload
  "Workloads for Jepsen to use in testing a PowerSync web app."
  (:require [jepsen
             [checker :as checker]
             [client :as client]
             [db :as db]]
            [ps-web.browser-app :as browser-app]))

(defn no-op
  "A no-op PowerSync webapp workload."
  [_opts]
  {:db              db/noop
   :client          client/noop
   :generator       nil
   :final-generator nil
   :checker         (checker/unbridled-optimism)})

(defn max-write-wins
  "A Max Write Wins PowerSync webapp workload."
  [opts]
  (let [no-op (no-op opts)]
    (merge
     no-op
     {:db (browser-app/webapp-in-browser)
      :checker (checker/compose
                {:webapp-log  (checker/log-file-pattern #"(?i)error" browser-app/webapp-log-file-short)
                 :console-log (checker/log-file-pattern #"(?i)error" browser-app/console-log-file-short)})})))

(def all-workloads
  "A set of all workloads"
  #{:no-op :max-write-wins})

(def workload-map
  "A map of workload names to functions
   that take CLI options and return workload maps."
  {:no-op          no-op
   :max-write-wins max-write-wins})
