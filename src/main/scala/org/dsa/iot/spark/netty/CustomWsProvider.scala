package org.dsa.iot.spark.netty

import java.net.{ URI, URISyntaxException }

import scala.util.Try

import org.dsa.iot.dslink.connection.NetworkClient
import org.dsa.iot.dslink.provider.WsProvider
import org.dsa.iot.dslink.util.http.WsClient
import org.dsa.iot.dslink.util.json.{ EncodingFormat, JsonObject }
import org.dsa.iot.shared.SharedObjects
import org.slf4j.LoggerFactory

import io.netty.bootstrap.Bootstrap
import io.netty.buffer.Unpooled
import io.netty.channel._
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http._
import io.netty.handler.codec.http.websocketx._
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import io.netty.util.CharsetUtil

/**
 * WS Provider implementation.
 */
class CustomWsProvider extends WsProvider {

  private val LOGGER = LoggerFactory.getLogger(getClass)

  override def connect(client: WsClient) = {
    if (client == null)
      throw new NullPointerException("client")

    val url = client.getUrl
    val uri = Try(new URI(url.protocol + "://" + url.host + ":" + url.port + url.path)) recover {
      case e: URISyntaxException => throw new RuntimeException(e)
    } get

    val v = WebSocketVersion.V13
    val h = new DefaultHttpHeaders
    val wsch = WebSocketClientHandshakerFactory.newHandshaker(uri, v, null, true, h, Integer.MAX_VALUE)
    val handler = new WebSocketHandler(wsch, client)

    val b = new Bootstrap
    b.group(SharedObjects.getLoop())
    b.channel(classOf[NioSocketChannel])
    b.handler(new ChannelInitializer[SocketChannel] {
      protected def initChannel(ch: SocketChannel) = {
        val p = ch.pipeline
        if (url.secure) {
          val man = InsecureTrustManagerFactory.INSTANCE
          val con = SslContextBuilder.forClient.trustManager(man).build
          p.addLast(con.newHandler(ch.alloc))
        }

        p.addLast(new HttpClientCodec)
        p.addLast(new HttpObjectAggregator(8192))
        p.addLast(handler)
      }
    })

    val fut = b.connect(url.host, url.port)
    fut.syncUninterruptibly
    handler.handshakeFuture.syncUninterruptibly
  }

  /**
   * Handles Web Socket connection events.
   */
  private[netty] class WebSocketHandler(var handshake: WebSocketClientHandshaker, client: WsClient)
      extends SimpleChannelInboundHandler[Object] {

    var handshakeFuture: ChannelPromise = null

    override def handlerAdded(ctx: ChannelHandlerContext) = {
      super.handlerAdded(ctx)
      handshakeFuture = ctx.newPromise
    }

    override def channelActive(ctx: ChannelHandlerContext) = {
      super.channelActive(ctx)
      handshake.handshake(ctx.channel)
    }

    override def channelInactive(ctx: ChannelHandlerContext) = {
      super.channelInactive(ctx)
      client.onDisconnected
    }

    def channelRead0(ctx: ChannelHandlerContext, msg: Object) = {
      val ch = ctx.channel

      if (handshake != null && !handshake.isHandshakeComplete) {

        handshake.finishHandshake(ch, msg.asInstanceOf[FullHttpResponse])
        handshake = null
        if (handshakeFuture != null) {
          handshakeFuture.setSuccess
          handshakeFuture = null
        }

        client.onConnected(new NetworkClient {

          def writable = ch.isWritable

          def write(format: EncodingFormat, data: JsonObject) = {
            val bytes = data.encode(format)
            val buf = Unpooled.wrappedBuffer(bytes)
            val frame = if (format == EncodingFormat.MESSAGE_PACK)
              new BinaryWebSocketFrame(buf)
            else if (format == EncodingFormat.JSON)
              new TextWebSocketFrame(buf)
            else {
              LOGGER.error(s"Unsupported encoding format: $format")
              null
            }
            if (frame != null) ch.writeAndFlush(frame)
          }

          def close = ctx.close

          def isConnected = ch.isOpen
        })
      } else {

        if (msg.isInstanceOf[FullHttpResponse]) {
          val response = msg.asInstanceOf[FullHttpResponse]
          throw new IllegalStateException(
            "Unexpected FullHttpResponse (getStatus=" + response.getStatus +
              ", content=" + response.content.toString(CharsetUtil.UTF_8) + ')')
        }

        val frame = msg.asInstanceOf[WebSocketFrame]
        if (frame.isInstanceOf[TextWebSocketFrame] || frame.isInstanceOf[BinaryWebSocketFrame]) {
          val content = frame.content
          val length = content.readableBytes
          val (bytes, offset) = if (content.hasArray) {
            (content.array, content.arrayOffset)
          } else {
            val buffer = new Array[Byte](length)
            content.readBytes(buffer)
            (buffer, 0)
          }
          client.onData(bytes, offset, length)
        } else if (frame.isInstanceOf[PingWebSocketFrame]) {
          val buf = frame.content().retain
          val pong = new PongWebSocketFrame(buf)
          ctx.channel().writeAndFlush(pong)
        } else if (frame.isInstanceOf[CloseWebSocketFrame]) {
          client.onDisconnected
          ctx.close
        }
      }
    }

    override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) = {
      client.onThrowable(cause)
      if (handshakeFuture != null)
        handshakeFuture.setFailure(cause)
      ctx.close
    }
  }
}
