(ns ps-web.browser-app
  "PowerSync Web App using PowerSync's JS Web SDK"
  (:require [clojure.tools.logging :refer [info]]
            [jepsen
             [db :as db]
             [control :as c]
             [util :as u]]
            [jepsen.control.util :as cu]
            [jepsen.os.debian :as debian]))

(def install-dir
  "Directory to install into."
  "/jepsen")

(def repository-name
  "Repository name."
  "jepsen-powersync-js-web")

(def repository-dir
  "Repository directory."
  (str install-dir "/" repository-name))

(def webapp-dir
  "Web app directory."
  (str repository-dir "/example-webpack"))

(def webapp-process-name   "npm")
(def webapp-bin            "/usr/bin/npm")
(def webapp-pid-file       (str install-dir "/webapp.pid"))
(def webapp-log-file-short "webapp.log")
(def webapp-log-file       (str install-dir "/" webapp-log-file-short))

(def browser-process-name   "chromium")
(def browser-bin            "/usr/bin/chromium")
(def browser-pid-file       (str install-dir "/browser.pid"))
(def browser-log-file-short "browser.log")
(def browser-log-file       (str install-dir "/" browser-log-file-short))

(defn install-packages
  "Install needed Debian packages."
  []
  (debian/update!)
  (debian/install [:chromium :extrepo])
  (c/exec :extrepo :enable :node_25.x)
  (debian/update!)
  (debian/install [:nodejs]))


(defn install-repository
  "Installs or updates GitHub repository in current directory."
  []
  (if (cu/exists? (str repository-name "/.git"))
    (do
      (info "repository" repository-name "already exists, pulling")
      (c/cd repository-name
            (c/exec :git :pull)))
    (do
      (info "repository" repository-name "does not exist, cloning")
      (c/exec :git :clone :-b :main :--depth :1 :--single-branch "https://github.com/nurturenature/jepsen-powersync-js-web.git"))))

; grepkill! is interacting poorly with killing the webapp
; FORNOW: workaround by explicitly calling killall
(defn killall
  "Kills the given named (as regex) process group.
   Assumes on node."
  [process-name]
  (u/meh ; will Exception if no processes
   (c/exec :killall :--process-group :--regexp :-- process-name)))

(defrecord WebAppInBrowser []
  db/DB
  (setup!
    [this test node]
    (info "Setting up PowerSync Browser App on" node)

    (install-packages)

    ; insure repository is installed
    (c/exec :mkdir :-p install-dir)
    (c/cd install-dir
          (install-repository))

    ; build webapp with webpack
    (c/cd webapp-dir
          (c/exec :npm :install)
          (c/exec :npm :run :build))

    (db/start! this test node))

  (teardown!
    [this test node]
    (info "Tearing down PowerSync Browser App on" node)

    (db/kill! this test node)

    ; FORNOW: intentionally leave repository, etc installed
    (c/exec :rm :-rf webapp-log-file  browser-log-file))

  ;; ; PowerSync doesn't have `primaries`.
  ;; db/Primary
  ;; (primaries
  ;;   [_db _test]
  ;;   #{})
  ;; 
  ;; (setup-primary!
  ;;   [_db _test _node]
  ;;   nil)

  db/LogFiles
  (log-files
    [_db _test _node]
    {browser-log-file browser-log-file-short
     webapp-log-file  webapp-log-file-short})

  db/Kill
  (start!
    [_this {:keys [jepsen-control-node] :as _test} node]
    ; webapp, i.e. npm
    (cu/start-daemon!
     {:chdir   webapp-dir
      :logfile webapp-log-file
      :pidfile webapp-pid-file}
     webapp-bin :run :serve)

    ; browser, i.e. chromium
    (let [webapp-url (str "https://" node "/?myHostname=" node "&jepsenControlNode=" jepsen-control-node)]
      (cu/start-daemon!
       {:chdir   install-dir
        :logfile browser-log-file
        :pidfile browser-pid-file}
       browser-bin
       :--allow-insecure-localhost ; TODO: add cert to host's trusted certs
       :--no-sandbox               ; TODO: create a non-root user to run browser? --headless and user root require --no-sandbox
       :--headless
       :--enable-logging=stderr
     ; :--log-level=2 TODO what is appropriate log level? getting console logs?
       webapp-url)))

  (kill!
    [_this _test _node]
    (killall browser-process-name)
    (killall webapp-process-name))

  db/Pause
  (pause!
    [_this _test _node]
    ; TODO: understand why sporadic Exception with exit code of 137 when using Docker,
    ;       for now, retrying is effective and safe 
    (u/timeout 10000
               :timed-out
               (do
                 (c/su
                  (u/retry 1 (cu/grepkill! :stop browser-process-name)))
                 :paused)))

  (resume!
    [_this _test _node]
    ; TODO: understand why sporadic Exception with exit code of 137 when using Docker,
    ;       for now, retrying is effective and safe 
    (u/timeout 10000
               :timed-out
               (do
                 (c/su
                  (u/retry 1 (cu/grepkill! :cont browser-process-name)))
                 :resumed))))

(defn webapp-in-browser
  "A webapp running in a browser based on PowerSync's JS Web SDK."
  []
  (WebAppInBrowser.))

