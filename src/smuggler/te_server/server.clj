(ns smuggler.te-server.server
  (:require
   [clojure.core.async :as async]
   [mount.core :refer [defstate]]
   [taoensso.timbre :refer [error]]
   [smuggler.te-server.initializer :as initializer]
   )
   (:import
    (io.netty.bootstrap ServerBootstrap)
    (io.netty.channel.nio NioEventLoopGroup )
    (io.netty.channel.socket.nio NioServerSocketChannel)
    (io.netty.handler.logging LoggingHandler LogLevel)
    ))

(def ^:private port 4000)
(def ^:private worker-group (atom nil))
(def ^:private boss-group (atom nil))

(defn start []
  (let [bg (NioEventLoopGroup. 1)
        wg (NioEventLoopGroup.)
        b (ServerBootstrap.)]
    (reset! boss-group bg)
    (reset! worker-group wg)
    
      (doto b
        (.group bg wg)
        (.channel (.getClass (NioServerSocketChannel.)))
        (.handler (LoggingHandler. LogLevel/INFO))
        (.childHandler (initializer/init {:ignore-transfer-encoding? false})))

      (let [ch (-> b
                   (.bind port)
                   (.sync)
                   (.channel))]
        (println "System ready at port " port)
        (.. ch closeFuture sync)
        (println "Disconnecting..."))))

(defstate server
  :start (async/thread (start))
  :stop (do
          (.shutdownGracefully @boss-group)
          (.shutdownGracefully @worker-group)
          (reset! worker-group nil)
          (reset! boss-group nil)))
