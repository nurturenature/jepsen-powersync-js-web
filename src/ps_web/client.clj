(ns ps-web.client
  (:require [jepsen.client :as client]))

(defrecord PSBrowserClient [conn]
  client/Client
  (open!
    [this _test node]
    (let [; TODO real websocket
          websocket :websocket]
      (assoc this
             :node      node
             :websocket websocket)))

  (setup!
    [_this _test])

  (invoke!
    [{:keys [node _websocket] :as _this} _test {:keys [f value] :as op}]
    (assert (= f :txn))
    (let [op (assoc op :node node)]
      ; TODO use websocket
      (assoc op
             :type  :ok
             :value value)))

  (teardown!
    [_this _test])

  (close!
    [{:keys [_websocket] :as this} _test]
    ; TODO close websocket
    (dissoc this
            :node
            :websocket)))

(defn websocket-client
  "Create a client for the PowerSync browser app."
  []
  (PSBrowserClient. nil))
