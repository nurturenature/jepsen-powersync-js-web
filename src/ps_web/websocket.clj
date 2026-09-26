(ns ps-web.websocket
  "A WebSocket server is started by Jepsen to control the PowerSync webapp running in the browser."
  (:require [cheshire.core :as json]
            [clojure.tools.logging.readable :refer [info]]
            [ring.adapter.jetty :as jetty]
            [ring.websocket :as ws]))

(def sockets (atom #{}))

(defn on-open
  [socket]
  (info "on-open: socket:" socket)
  (swap! sockets conj socket))

(defn on-message
  [socket message]
  (let [message (json/parse-string message true)]
    (info "on-message: socket:" socket ", message:" message)))

(defn on-close
  [socket code reason]
  (info "on-close: socket:" socket ", code:" code ", reason:" reason)
  (swap! sockets disj socket))

(defn on-error
  [socket throwable]
  (info "on-error: socket:" socket ", throwable:" throwable)
  (throw throwable))

(defn websocket-handler [request]
  (assert (ws/upgrade-request? request))
  {::ws/listener
   {:on-open    on-open
    :on-message on-message
    :on-close   on-close
    :on-error   on-error}})

(def jetty-opts
  {:port         8090
   :host         "localhost"
   :join?        false ; don't block thread until server ends
   :ssl?         true
   :ssl-port     443
   :keystore     :TODO
   :key-password :TODO})

(defn websocket-server
  "Starts and returns a WebSocket server."
  []
  (jetty/run-jetty websocket-handler jetty-opts))
