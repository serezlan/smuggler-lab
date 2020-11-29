(ns smuggler.te-server.initializer
  (:require
   [smuggler.te-server.handler :refer [handler]])
  (:import
   (io.netty.channel ChannelInitializer)
   (io.netty.handler.codec.http HttpRequestDecoder HttpResponseEncoder)
   ))

(defn init []
  (proxy [ChannelInitializer] []
    (initChannel [ch]
      (let [p (.pipeline ch)]
        (doto p
          (.addLast (HttpRequestDecoder.))
          (.addLast (HttpResponseEncoder.))
          (.addLast (handler)))))))
