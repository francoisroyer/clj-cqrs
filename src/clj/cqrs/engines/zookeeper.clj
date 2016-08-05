(ns cqrs.engines.zookeeper
  (:import
    [java.net InetSocketAddress]
    [org.apache.zookeeper.server ZooKeeperServer NIOServerCnxnFactory]
    [org.apache.commons.io FileUtils]
    [org.I0Itec.zkclient ZkClient]
    [org.I0Itec.zkclient.serialize ZkSerializer]
    [java.util Properties])
  (:use [clojure.java.io :only (file)]))

(defn tmp-dir
  [& parts]
  (.getPath (apply file (System/getProperty "java.io.tmpdir") "clj-cqrs" parts)))


(defn create-zookeeper
  [{:keys [zookeeper-port]}]
  (let [tick-time 500
        zk (ZooKeeperServer. (file (tmp-dir "zookeeper-snapshot")) (file (tmp-dir "zookeeper-log")) tick-time)]
    (doto (NIOServerCnxnFactory.)
      (.configure (InetSocketAddress. "127.0.0.1" zookeeper-port) 100)
      (.startup zk))))
