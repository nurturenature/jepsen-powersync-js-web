(ns ps-web.client
  "A PowerSync web client is a WebSocket Channel back to a WebSocket Server
   running under the Jepsen control node."
  (:require [clojure.tools.logging.readable :refer [info]]
            [jepsen.client :as client]
            [org.httpkit.server :as hk-server]))

(def websocket-port 8090)

(def channels (atom #{}))

(defn on-open
  [ch]
  (info "on-open: ch:" ch)
  (swap! channels conj ch))

(defn on-receive
  [ch message]
  (info "on-receive: ch:" ch ", message:" message)
  (doseq [ch @channels]
    (hk-server/send! ch (str "Broadcasting: " message))))

(defn on-close
  [ch status-code]
  (info "on-close: ch:" ch ", status-code:" status-code)
  (swap! channels disj ch))

(defn websocket-handler [ring-req]
  (assert (:websocket? ring-req))
  (hk-server/as-channel ring-req
                        {:on-open    on-open
                         :on-receive on-receive
                         :on-close   on-close}))

(def websocket-server (hk-server/run-server websocket-handler {:port websocket-port}))

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
