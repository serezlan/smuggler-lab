(ns smuggler.te-server.initializer
  (:require
   [smuggler.te-server.handler :refer [handler]])
  (:import
   (io.netty.channel ChannelInitializer)
   (io.netty.handler.codec.http HttpRequestDecoder HttpResponseEncoder ModHttpRequestDecoder)
   ))

(defn init
  [{:keys [ignore-transfer-encoding?] :as options}]
  (proxy [ChannelInitializer] []
    (initChannel [ch]
      (let [p (.pipeline ch)]
        (doto p
          (.addLast (ModHttpRequestDecoder. ignore-transfer-encoding?))
          (.addLast (HttpResponseEncoder.))
          (.addLast (handler)))))))
