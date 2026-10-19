(ns app
  (:require [flux.string :as str]))

;; what every namespace refers of flux.core and flux.builtins
(defn greet [name]
  (message (str "hello " name) *buffer*)
  (keepv inc [1 2])
  (ignore-errors (str/trim name) (str/trimr name)))

;; a name of clojure.core that Flux lacks, a name nothing has, and a private var of flux.core
(defn missing [x]
  (println x)
  (frobnicate x)
  (private-helper x))

;; qualified names of loaded namespaces need no require
(defn qualified [x]
  (flux.builtins/message x)
  (flux.other-ns/other-fn x)
  (flux.other-ns/other-private x)
  (nowhere.ns/thing x))

;; the error type of a catch clause is not evaluated
(defn caught [x]
  (try
    (inc x)
    (catch exception error (message error))
    (finally (message "done"))))

(defn uncaught [x]
  (try
    (inc x)
    (catch exception)))

;; macros that define vars
(defn early []
  later-state)

(defstate later-state 1)
(defhandler [:kind handler-name] 2)

(defn late []
  [later-state handler-name])

;; forms that are evaluated later, and var references, can name what the file defines below
(after! [flux.other-ns :as other]
  (other/other-fn (below-fn 1)))

(on-reload!
  (below-fn 2))

(def below-var #'below-fn)

(defn below-fn [x]
  x)

;; special forms of Flux's own
(defn locked [buffer]
  (with-read-lock [lock buffer]
    (set-local! *buffer* lock)
    (message lock)))

;; collections changed in place, a vector called with a default, and an escape Flux keeps
(defn in-place [t]
  (conj! t 1)
  ([1 2 3] 9 :default)
  "don\'t")
