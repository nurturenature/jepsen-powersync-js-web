(ns ps-web.nemesis
  "Nemeses to disrupt PowerSync."
  (:require [jepsen.nemesis.combined :as nc]))

(defn nemesis-package
  "Constructs combined nemeses and generators into a nemesis package."
  [opts]
  (let [opts (update opts :faults set)]
    (->> []
         (concat (nc/nemesis-packages opts))
         (filter :generator)
         nc/compose-packages)))