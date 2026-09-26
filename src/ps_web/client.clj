(ns ps-web.client
  "A PowerSync web client is a WebSocket Channel back to a WebSocket Server
   running under the Jepsen control node."
  (:require [jepsen.client :as client]
            [ps-web.websocket :as ws]))

(def websocket-server
  "The first client will start a WebSocket Server for all clients to use."
  (atom nil))

(defrecord PSBrowserClient [conn]
  client/Client
  (open!
    [this _test node]
    ; first client starts WebSocket Server
    (locking websocket-server
      (when-not @websocket-server
        (swap! websocket-server (constantly (ws/websocket-server)))))

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
