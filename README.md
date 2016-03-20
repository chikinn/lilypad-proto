Lilypad Prototype
=================

A CRUD webapp to store knowledge like Wikipedia, but organize and present it in
a way that facilitates prerequisite-by-prerequisite learning.

Deployed on Heroku at 
[lilypad-proto.herokuapp.com](http://lilypad-proto.herokuapp.com).

Written in Clojure with the Compojure web framework.  HTML generated with
Hiccup.  Content stored in a PostgreSQL database.

Database info
-------------

Table name: nodes

Fields:
* **id, smallserial** Internal database ID number, also used for node URLs
* **title, text** Node name
* **prereq, smallint[]** Prerequisites, stored as an array of IDs
* **desc_is, text** Node description: what it is
* **desc_does, text** Node description: what it does
* **desc_use, text** Node description: how to use it
* **example, text** Example(s) of how a node's knowledge can be applied
* **comm, text** Internal comments for our benefit during development

Generate an empty nodes table:
* in the Leiningen REPL
  `(sql/db-do-commands DB (sql/create-table-ddl TABLE_KEY [:id :smallserial] [:title :text] [:prereq "smallint[]"] [:desc_is :text] [:desc_does :text] [:desc_use :text] [:example :text] [:comm :text]))`
* or analogously in psql (though this may cause permissions issues)
  `create table nodes (id smallserial, title text, prereq smallint[], desc_is text, desc_does text, desc_use text, example text, comm text);`
