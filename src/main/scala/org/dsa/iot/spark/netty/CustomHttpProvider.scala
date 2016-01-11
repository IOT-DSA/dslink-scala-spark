package org.dsa.iot.spark.netty

import java.util.Map

import collection.JavaConverters.mapAsScalaMapConverter
import util.control.NonFatal

import org.dsa.iot.dslink.provider.HttpProvider
import org.dsa.iot.dslink.util.URLInfo
import org.dsa.iot.dslink.util.http.HttpResp
import org.dsa.iot.shared.SharedObjects

import io.netty.bootstrap.Bootstrap
import io.netty.channel._
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http._
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import io.netty.util.CharsetUtil

/**
 * HTTP Provider implementation.
 */
class CustomHttpProvider extends HttpProvider {

  def post(url: URLInfo, content: Array[Byte], headers: Map[String, String]): HttpResp = {
    if (url == null)
      throw new NullPointerException("url")

    try {
      val handler = new HttpHandler

      val b = new Bootstrap
      b.group(SharedObjects.getLoop)
      b.channel(classOf[NioSocketChannel])
      b.handler(new Initializer(handler, url.secure))

      val fut = b.connect(url.host, url.port)
      val chan = fut.sync.channel
      chan.writeAndFlush(populateRequest(url.path, content, headers))
      fut.channel.closeFuture.sync
      populateResponse(handler)
    } catch {
      case NonFatal(e) => throw new RuntimeException(e)
    }
  }

  private def populateRequest(uri: String, content: Array[Byte], headers: Map[String, String]): HttpRequest = {
    val request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, uri)
    val buf = request.content
    if (content != null)
      buf.writeBytes(content)

    {
      val h = request.headers
      Option(headers) foreach (_.asScala foreach {
        case (name, value) => h.set(name, value)
      })
      val len = String.valueOf(buf.readableBytes)
      h.set(HttpHeaders.Names.CONTENT_LENGTH, len)
    }
    request
  }

  private def populateResponse(handler: HttpHandler): HttpResp = {
    val throwable = handler.getThrowable
    if (throwable != null)
      throw throwable

    val resp = new HttpResp()
    resp.setStatus(handler.getStatus)
    resp.setBody(handler.getContent)
    resp
  }
}

/**
 * Initializes the HTTP channel.
 */
private[netty] class Initializer(handler: HttpHandler, secure: Boolean)
    extends ChannelInitializer[SocketChannel] {

  override protected def initChannel(ch: SocketChannel) = {
    val p = ch.pipeline

    if (secure) {
      val man = InsecureTrustManagerFactory.INSTANCE
      val con = SslContextBuilder.forClient.trustManager(man).build
      p.addLast(con.newHandler(ch.alloc()))
    }

    p.addLast(new HttpClientCodec)
    p.addLast(new HttpContentDecompressor)
    p.addLast(handler)
  }
}

/**
 * Reads HTTP data.
 */
private[netty] class HttpHandler extends SimpleChannelInboundHandler[Object] {
  private val content = new StringBuffer
  private var status: HttpResponseStatus = null
  private var t: Throwable = null

  protected def channelRead0(ctx: ChannelHandlerContext, msg: Object) = {
    if (msg.isInstanceOf[HttpResponse])
      status = msg.asInstanceOf[HttpResponse].getStatus
    if (msg.isInstanceOf[HttpContent]) {
      val buf = msg.asInstanceOf[HttpContent].content
      content.append(buf.toString(CharsetUtil.UTF_8))
    }
    if (msg.isInstanceOf[LastHttpContent])
      ctx.close
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, t: Throwable) = {
    this.t = t
    ctx.close
  }

  def getThrowable = t

  def getStatus = status

  def getContent = content.toString
}