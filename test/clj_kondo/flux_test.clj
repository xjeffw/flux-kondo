(ns clj-kondo.flux-test
  "Flux Lisp, the language of `.flux` files: see clj-kondo.impl.flux. The files
  of corpus/flux stand in for the Flux library (`lib`), a program that uses
  it (`app`) and a package (`pkg`)."
  (:require
   [babashka.fs :as fs]
   [clj-kondo.core :as clj-kondo]
   [clj-kondo.test-utils :refer [assert-submaps2 lint!]]
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is testing]]))

(def config
  {:linters {:unresolved-symbol {:level :error}
             :unresolved-var {:level :warning}
             :unresolved-namespace {:level :warning}
             :unused-value {:level :warning}
             :unused-binding {:level :warning}
             :unused-private-var {:level :off}
             :private-call {:level :off}
             :namespace-name-mismatch {:level :error}}})

(def lib (io/file "corpus" "flux" "lib"))

(def app (io/file "corpus" "flux" "app" "app.flux"))

(def pkg (io/file "corpus" "flux" "pkg"))

(def app-findings
  '({:file "corpus/flux/app/app.flux" :row 12 :col 4 :level :error
     :message "Unresolved symbol: println"}
    {:file "corpus/flux/app/app.flux" :row 13 :col 4 :level :error
     :message "Unresolved symbol: frobnicate"}
    {:file "corpus/flux/app/app.flux" :row 14 :col 4 :level :error
     :message "Unresolved symbol: private-helper"}
    {:file "corpus/flux/app/app.flux" :row 21 :col 4 :level :warning
     :message "Unresolved namespace nowhere.ns. Are you missing a require?"}
    {:file "corpus/flux/app/app.flux" :row 33 :col 5 :level :error
     :message "catch requires an error type, a binding symbol and at least one body form"}
    {:file "corpus/flux/app/app.flux" :row 37 :col 3 :level :error
     :message "Unresolved symbol: later-state"}))

(deftest library-and-program-test
  (testing "a program resolves what it refers of flux.core and flux.builtins,
  the vars that macros define, qualified names of loaded namespaces, and what
  forms evaluated later name; the library itself lints clean"
    (assert-submaps2 app-findings (lint! [lib app] config))))

(deftest unknown-library-test
  (testing "nothing is said about an unqualified name while flux.core and
  flux.builtins are unknown"
    (assert-submaps2
     '({:row 19 :col 4 :message "Unresolved namespace flux.other-ns. Are you missing a require?"}
       {:row 21 :col 4 :message "Unresolved namespace nowhere.ns. Are you missing a require?"}
       {:row 33 :col 5 :message "catch requires an error type, a binding symbol and at least one body form"})
     (lint! app config))))

(deftest package-test
  (testing "the description of a package is data, and the namespace of a test
  is named after its file under test/"
    (assert-submaps2
     '({:file "corpus/flux/pkg/test/pkg/misnamed.flux" :row 1 :col 5 :level :error
        :message "Namespace name does not match file name: pkg.tests.other-name"})
     (lint! [lib pkg] config))))

(deftest clojure-file-test
  (testing "a Clojure file is linted as before"
    (assert-submaps2
     '({:row 1 :col 30 :message "Unresolved symbol: exception"}
       {:row 1 :col 47 :message "Unresolved symbol: message"})
     (lint! "(ns app) (try (inc 1) (catch exception error (message error)))"
            config))))

(defn- run-findings [cache-dir & paths]
  (->> (clj-kondo/run! {:lint (map str paths)
                        :cache-dir (str cache-dir)
                        :config [config]})
       :findings
       (map #(select-keys % [:row :col :message]))
       (sort-by (juxt :row :col))))

(deftest cache-test
  (fs/with-temp-dir [cache-dir {}]
    (clj-kondo/run! {:lint [(str lib)]
                     :cache-dir (str cache-dir)
                     :dependencies true
                     :config [config]})
    (let [expected (map #(select-keys % [:row :col :message]) app-findings)]
      (testing "a program is linted against a cached library, including the vars
  its macros define"
        (is (= expected (run-findings cache-dir app))))
      (testing "linting one file of a namespace keeps what the cache has of it
  from its other files"
        (is (empty? (run-findings cache-dir (io/file lib "flux" "string.flux"))))
        (is (empty? (run-findings cache-dir (io/file lib "flux" "builtins-extra.flux"))))
        (is (empty? (run-findings cache-dir (io/file lib "flux" "core.flux"))))
        (is (= expected (run-findings cache-dir app)))))))

(deftest library-copies-test
  (testing "a namespace that two files declare, as when two copies of a library
  are read, keeps what the files that continue it with in-ns define, whichever
  are read first"
    (fs/with-temp-dir [dir {}]
      (let [copies (mapv (fn [copy]
                           (let [target (fs/file dir copy)]
                             (fs/copy-tree lib target)
                             target))
                         ["one" "two"])
            files (fn [name] (mapv #(str (io/file % "flux" name)) copies))
            expected (map #(select-keys % [:row :col :message]) app-findings)]
        (doseq [order [["builtins-extra.flux" "core.flux" "string.flux"]
                       ["core.flux" "builtins-extra.flux" "string.flux"]
                       ["core.flux" "string.flux" "builtins-extra.flux"]]]
          (fs/with-temp-dir [cache-dir {}]
            (clj-kondo/run! {:lint (concat (mapcat files order)
                                           (files "builtins.flux")
                                           (files "other-ns.flux"))
                             :cache-dir (str cache-dir)
                             :dependencies true
                             :config [config]})
            (is (= expected (run-findings cache-dir app)) (str order))))))))

(deftest private-var-of-a-continued-namespace-test
  (testing "an unused private var is reported in the file that defines it, where an
  ignore applies, whichever file that continues its namespace with in-ns is read last"
    (fs/with-temp-dir [dir {}]
      (let [config (-> config
                       (assoc-in [:linters :unused-private-var :level] :warning)
                       (assoc-in [:linters :redundant-ignore :level] :warning))
            core (fs/file dir "flux" "core.flux")
            extra (fs/file dir "flux" "builtins-extra.flux")]
        (fs/create-dirs (fs/parent core))
        (spit core (str "(ns flux.core)\n\n"
                        "#_{:clj-kondo/ignore [:unused-private-var]}\n"
                        "(defn- kept-for-the-runtime [] nil)\n\n"
                        "(defn- unused [] nil)\n"))
        (spit extra "(ns flux.builtins-extra)\n\n(in-ns 'flux.core)\n(def ^:special-form def nil)\n")
        (doseq [order [[core extra] [extra core]]]
          (assert-submaps2
           [{:file (str core) :row 6 :col 8 :message "Unused private var flux.core/unused"}]
           (lint! order config)))))))
