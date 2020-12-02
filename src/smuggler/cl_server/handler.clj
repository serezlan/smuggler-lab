(ns smuggler.cl-server.handler
  (:require
   [taoensso.timbre :refer [error]]
   )
  (:import
   (io.netty.handler.codec.http HttpRequest DefaultFullHttpResponse HttpVersion HttpHeaderNames HttpHeaderValues HttpResponseStatus HttpContent LastHttpContent HttpUtil HttpObject)
   (io.netty.channel SimpleChannelInboundHandler ChannelFutureListener)
   (io.netty.buffer Unpooled)
   (io.netty.util CharsetUtil)
   ))

;; (def ^:private sb (StringBuilder.))
;; (def ^:private request (atom nil))

(defn- append-decoder-result [buf obj]
  {:pre [(instance? StringBuilder buf) (instance? HttpObject obj)]}
  (let [result (.decoderResult obj)]
    (when-not (.isSuccess result)
      (doto buf
        (.append ".. WITH DECODER FAILURE ")
        (.append (.cause result))
        (.append "\r\n")))))

(defn- send-100-continue [ctx]
  (let [response (DefaultFullHttpResponse. HttpVersion/HTTP_1_1 HttpResponseStatus/CONTINUE (Unpooled/EMPTY_BUFFER))]
    (.write ctx response)))

(defn- process-http-request [ctx msg request buffer]
  (reset! request msg)

  (when (HttpUtil/is100ContinueExpected @request)
    (send-100-continue ctx))

  (let [version (.protocolVersion @request)
        uri (.uri @request)
        host (-> @request (.headers) (.get "Host" "Unknown"))
        method (.method @request)
        headers (.headers msg)
        names (.names headers)]
    (doto buffer
      (.setLength 0)
      (.append "Welcome to WWW server\r\n")
      (.append "----------\r\n")
      (.append (str "Method: " method "\r\n"))
      (.append (str "Version: " version "\r\n"))
      (.append (str "Hostname: " host "\r\n"))
      (.append (str "URI: " uri "\r\n")))

    (when-not (.isEmpty headers)
      (.append buffer "----------\r\n")
      (doseq [hn names]
        (.append buffer (str hn ": " (.get headers hn) "\r\n")))
      (.append buffer "\r\n"))

    (append-decoder-result buffer @request)))

(defn- write-response [ctx msg request buffer]
  (let [keep-alive (HttpUtil/isKeepAlive @request)
        status (if (.. msg decoderResult isSuccess)
                 HttpResponseStatus/OK
                 HttpResponseStatus/BAD_REQUEST)
        content (Unpooled/copiedBuffer buffer CharsetUtil/UTF_8)
        response (DefaultFullHttpResponse. HttpVersion/HTTP_1_1 status content)
        headers (.headers response)]
    (.set headers HttpHeaderNames/CONTENT_TYPE "text/html; charset=UTF-8")

    (when keep-alive
      (.setInt headers HttpHeaderNames/CONTENT_LENGTH (.readableBytes content))
      (.set headers HttpHeaderNames/CONNECTION HttpHeaderValues/KEEP_ALIVE))
    
    (.write ctx response)
    keep-alive))

(defn- process-http-content [ctx msg request buffer]
  (let [content (.content msg)]
    (when (.isReadable content)
      (doto buffer
        (.append "Content:\r\n")
        (.append (.toString content CharsetUtil/UTF_8))
        (.append "\r\n"))
      (append-decoder-result buffer @request))

    (when (instance? LastHttpContent msg)
      (let [trailing-headers (.trailingHeaders msg)]
        (.append buffer "EOF\r\n")

        (when-not (.isEmpty trailing-headers)
          (.append buffer "\r\n")
          (doseq [tn (.names trailing-headers)]
            (doseq [tv (.getAll trailing-headers tn)]
              (.append (str "Trailing header : " tn " = " tv "\r\n"))))))

      (when-not (write-response ctx msg request buffer)
        (-> ctx
            (.writeAndFlush Unpooled/EMPTY_BUFFER)
            (.addListener ChannelFutureListener/CLOSE))))))

(defn- channel-read-0 [ctx msg request buffer]
  (when (instance? HttpRequest msg)
    (process-http-request ctx msg request buffer))

  (when (instance? HttpContent msg)
    (process-http-content ctx msg request buffer)))


(defn handler []
  (let [request (atom nil)
        buffer (StringBuilder.)]
    (proxy [SimpleChannelInboundHandler] []
    (channelReadComplete [ctx]
      (.flush ctx))

    (channelRead0 [ctx msg]
      (channel-read-0 ctx msg request buffer ))

    (exceptionCaught [ctx cause]
      (error cause)
      (.close ctx)))))
