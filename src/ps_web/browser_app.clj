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

(def browser-app-dir
  "Web app directory."
  (str repository-dir "/ps-web-app"))

(def app-url
  "URL of browser app."
  (str "file:" browser-app-dir "/index.html"))

(def pid-file (str install-dir "/ps-web-app.pid"))

(def log-file-short "ps-web-app.log")
(def log-file       (str install-dir "/" log-file-short))

(def app-ps-name "chromium")
(def app-bin-name "/usr/bin/chromium")

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
      (c/exec :git :clone :-b :main :--depth :1 :--single-branch "https://github.com/nurturenature/jepsen-powersync-web.git"))))

(defrecord BrowserApp []
  db/DB
  (setup!
    [this test node]
    (info "Setting up PowerSync Browser App on" node)

    (install-packages)

    (c/exec :mkdir :-p install-dir)
    (c/cd install-dir
          (install-repository))

    (db/start! this test node))

  (teardown!
    [this test node]
    (info "Tearing down PowerSync Browser App on" node)

    (db/kill! this test node)

    ; FORNOW: intentionally leave repository, etc installed
    (c/exec :rm :-rf pid-file log-file))

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
    {log-file log-file-short})

  db/Kill
  (start!
    [_this _test _node]
    (cu/start-daemon!
     {:chdir   browser-app-dir
      :logfile log-file
      :pidfile pid-file}
     app-bin-name
     :--headless
     app-url))

  (kill!
    [_this _test _node]
    ; TODO: understand why sporadic Exception with exit code of 137 when using Docker,
    ;       for now, retrying is effective and safe 
    (u/timeout 10000
               :timed-out
               (do
                 (c/su
                  (u/retry 1 (cu/grepkill! app-ps-name)))
                 :killed)))

  db/Pause
  (pause!
    [_this _test _node]
    ; TODO: understand why sporadic Exception with exit code of 137 when using Docker,
    ;       for now, retrying is effective and safe 
    (u/timeout 10000
               :timed-out
               (do
                 (c/su
                  (u/retry 1 (cu/grepkill! :stop app-ps-name)))
                 :paused)))

  (resume!
    [_this _test _node]
    ; TODO: understand why sporadic Exception with exit code of 137 when using Docker,
    ;       for now, retrying is effective and safe 
    (u/timeout 10000
               :timed-out
               (do
                 (c/su
                  (u/retry 1 (cu/grepkill! :cont app-ps-name)))
                 :resumed))))

(defn browser-app
  "Installs a browser app based on PowerSync's JS Web SDK."
  []
  (BrowserApp.))

