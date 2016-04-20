(ns lilypad-proto.app (:require [ring.adapter.jetty :as jetty]
                                [compojure.core :as cc]
                                [compojure.handler :as handler]
                                [clojure.java.jdbc :as sql]
                                [hiccup.page :as page])
                      (:use     [clojure.string :only (split)])
                      (:gen-class))

(def DB (or (System/getenv "DATABASE_URL")
            "postgresql://localhost:5432/lilypad"))
(def TABLE "nodes")
(def TABLE_KEY :nodes)
(def INDENT_SIZE 4)

(extend-protocol sql/IResultSetReadColumn    ;
  org.postgresql.jdbc4.Jdbc4Array            ; From SO #6055629
  (result-set-read-column [pgobj metadata i] ; Auto-convert db out to vector.
    (vec (.getArray pgobj))))                ;

(extend-protocol sql/ISQLParameter ; SO #22959804: Auto-convert db in to array.
  clojure.lang.IPersistentVector
  (set-parameter [v ^java.sql.PreparedStatement stmt ^long i]
    (let [conn (.getConnection stmt)
          meta (.getParameterMetaData stmt)
          type-name (.getParameterTypeName meta i)]
      (if-let
        [elem-type (when (= (first type-name) \_) (apply str (rest type-name)))]
        (.setObject stmt i (.createArrayOf conn elem-type (to-array v)))
        (.setObject stmt i v)))))


;;; LOW-LEVEL FUNCTIONS
(defn in? [coll elm] (some #(= elm %) coll)) ; SO #3249334

(defn get-all-rows [] (sql/query DB (str "select * from " TABLE)))

(defn get-row [id]
  (first (sql/query DB (str "select * from " TABLE " where id=" id))))

(defn newline-to-br [text]
  (clojure.string/replace text #"\r\n|\n|\r" "<br />\n"))

(defn add-val-to-vec [value vect] (vec (concat vect (vec [value]))))

(defn remove-val-from-vec [value vect]
  (vec (filter #(not= (read-string value) %) vect))) ; Unclear on type conv

;;; FUNCTIONS THAT GENERATE HTML
(defn html-page-head [title] [:head [:title (str title " - Lilypad")]])

(defn row-to-html-link [row] (seq [[:a {:href (:id row)} (:title row)] [:br]]))

(defn html-button-link [text target]
  [:button {:onclick (str "location.href='/" target "'")} text])

(defn html-multiselect [field-name values texts defaults]
  (defn default? [defaults value] (in? defaults value))
  (defn html-option [value text is-default]
    (if is-default [:option {:value value :selected ""} text]
                   [:option {:value value} text]))
  (def is-defaults (map (partial default? defaults) values))
  [:select {:multiple "" :name field-name}
    (map html-option values texts is-defaults)])

(defn html-form ([hidden] (html-form -1 hidden)) ([id hidden] 
  (def all-rows (sort-by :title (get-all-rows))) ; Sort for multiselect.
  (def row (get-row id))                     ;
  [:form {:action "/process" :method "POST"} ; No id means no defaults, since
    [:table                                  ; (get-row -1) -> nil.
      [:tr [:td "Title"]                     ;
           [:td [:input {:type "text" :name "title" :value (:title row)}]]]
      [:tr [:td "Prereqs"]
           [:td (html-multiselect "prereq" (map :id all-rows)
                                  (map :title all-rows) (:prereq row))]]
      [:tr [:td [:u "Description"]]] ; Note: non-idiomatic underscores to
      [:tr [:td "- What it is"]      ; accommodate database requirements
           [:td [:textarea {:name "desc_is"} (:desc_is row)]]]
      [:tr [:td "- What it does"]
           [:td [:textarea {:name "desc_does"} (:desc_does row)]]]
      [:tr [:td "- How to use it"]
           [:td [:textarea {:name "desc_use"} (:desc_use row)]]]
      [:tr [:td "Examples"]
           [:td [:textarea {:name "example"} (:example row)]]]
      [:tr [:td "Comments"]
           [:td [:textarea {:name "comm"} (:comm row)]]]]
    [:input {:type "hidden" :name "hidden" :value hidden}]
    [:p] [:input {:type "submit" :value "Submit"}]]))

(defn html-button-hidden-form [text target hidden]
  [:form {:action (str "/" target) :method "POST"}
    [:input {:type "hidden" :name "hidden" :value hidden}]
    [:input {:type "submit" :value text}]])

(defn html-redir [target]
  [:head [:meta {:http-equiv "refresh" :content (str "0; url=/" target)}]])

(defn html-recursively-nest-nodes [node-ids indent-level] 
  (for [row (sort-by :title (map get-row node-ids))]
    (list (repeat (* indent-level INDENT_SIZE) "&nbsp;")
          (row-to-html-link row)
          ; TODO: Optimal tail-end recursion with recur
          (html-recursively-nest-nodes (:prereq row) (inc indent-level)))))


;;; HIGH-LEVEL FUNCTIONS
(defn add-node [form-data]
  (:id (first (sql/insert! DB TABLE_KEY (update form-data :prereq vec)))))

(defn edit-node [id form-data]
  (sql/update! DB TABLE_KEY form-data [(str "id = " id)]))

(defn add-prereq [prereq row]
  (edit-node (:id row) (update row :prereq (partial add-val-to-vec prereq))))

(defn remove-prereq [prereq row]
  (edit-node (:id row)
             (update row :prereq (partial remove-val-from-vec prereq))))

(defn delete-node [id] ; TODO: confirmation
  (sql/delete! DB TABLE_KEY [(str "id = " id)])
  ; Remove deleted node from other nodes' prereqs.  Doall prevents laziness.
  (def affected-rows (sql/query DB
    (str "select * from " TABLE " where prereq @> '{" id "}'::smallint[]")))
  (doall (map (partial remove-prereq id) affected-rows)))


;;; FUNCTIONS THAT GENERATE COMPLETE WEB PAGES
(defn main-page []
  (page/html5 (html-page-head "Home")
    [:h2 "LILYPAD"]
    (html-button-link "New Node" "add")
    [:p] 
;    (map row-to-html-link (sort-by :title (get-all-rows))) ; No indents
    (html-recursively-nest-nodes (map :id (sort-by :title (get-all-rows))) 0)))

(defn test-page []
  (page/html5 
    [:head 
     [:script {:type "text/x-mathjax-config"}
       "MathJax.Hub.Config ({tex2jax:  {inlineMath:  [['$','$'],  ['\\(','\\)']]}});"]
     [:script {:type "text/javascript" :src "http://cdn.mathjax.org/mathjax/latest/MathJax.js?config=TeX-AMS-MML_HTMLorMML"}]]
    [:body "$$x = {-b \\pm \\sqrt{b^2-4ac} \\over 2a}.$$"]))

(defn add-node-page []
  (page/html5 (html-page-head "New node")
    [:h2 "NEW NODE"]
    (html-button-link "Cancel" "")
    [:p] (html-form "add")))

(defn add-new-prereq-page [old-id]
  (page/html5 (html-page-head "New prereq")
    [:h2 "NEW PREREQ"]
    (html-button-link "Cancel" "")
    [:p] (html-form (str "prereq " old-id))))

(defn edit-node-page [id]
  (page/html5 (html-page-head "Edit node")
    [:h2 "EDIT NODE"]
    [:table [:tr [:td (html-button-link "Cancel" "")] 
                 [:td (html-button-hidden-form "Delete" "process"
                                               (str "delete " id))]
                 [:td (html-button-hidden-form "New Prereq" "prereq"
                                               (str "prereq " id))]]]
    [:p] (html-form id (str "edit " id))))

(defn node-page [id] ; TODO: add postreqs to bottom
  (def row (get-row id))
  (page/html5 (html-page-head (:title row))
    [:h2 (clojure.string/upper-case (:title row))]
    [:table [:tr [:td (html-button-link "Home" "")] 
                 [:td (html-button-hidden-form "Edit" "edit" id)]]]
    (if-not (= "" (:comm row))
      (seq [[:br] [:font {:color "red"} (newline-to-br (:comm row))] [:br]]))
    [:br] [:b "Prerequisites"] [:br]
    (html-recursively-nest-nodes (:prereq row) 0)
    [:br] [:b "Description"]
    [:br] "What it is: " (newline-to-br (:desc_is row)) [:br]
    [:br] "What it does: " (newline-to-br (:desc_does row)) [:br]
    [:br] "How to use it: " (newline-to-br (:desc_use row)) [:br]
    [:br] [:b "Examples"] [:br] (newline-to-br (:example row))))

(defn process-form-page [task raw-form-data]
  ; Ensure that a lone prereq is still a vector (not a string).
  (def form-data (update raw-form-data :prereq vec))
  (def task-name (first (split task #" ")))
  (def id        (last  (split task #" "))) ; If task 1 word, id = task-name.
  (case task-name
    "add"    (page/html5 (html-redir (add-node form-data)))
    "prereq" (let [new-id (add-node form-data) old-node (get-row id)]
               (add-prereq new-id old-node)
               (page/html5 (html-redir new-id)))
    "edit"   (do (edit-node id form-data) (page/html5 (html-redir id)))
    "delete" (do (delete-node id) (page/html5 (html-redir "")))))


(cc/defroutes routes
  (cc/GET  "/"        []                (main-page))
  (cc/GET  "/test"    []                (test-page))
  (cc/GET  "/add"     []                (add-node-page))
  (cc/POST "/prereq"  [hidden]          (add-new-prereq-page hidden))
  (cc/POST "/edit"    [hidden]          (edit-node-page hidden))
  (cc/POST "/process" [hidden & params] (process-form-page hidden params))
  (cc/GET  "/:id"     [id]              (node-page id)))

(defn -main []
  (jetty/run-jetty (handler/site routes)
    {:port (Integer. (or (System/getenv "PORT") "8080")) :join? false}))
