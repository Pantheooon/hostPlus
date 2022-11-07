package cn.pantheon
import io.netty.bootstrap.Bootstrap
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http.*
import java.net.URL
import java.util.*
import java.util.concurrent.TimeUnit



object ServerInitializer : ChannelInitializer<Channel>() {
    @Throws(Exception::class)
    override fun initChannel(ch: Channel) {
        with(ch.pipeline()) {
            addLast("httpCodec", HttpServerCodec())
            addLast("aggregator", HttpObjectAggregator(1024 * 1024))
            addLast("serverHandle", ServerHandler())
        }
    }
}

fun startServer(){
    val host = "127.0.0.1"
    val port = 18080
    val bossGroup = NioEventLoopGroup(2)
    val workerGroup = NioEventLoopGroup(2)
    try {
        val bootstrap = ServerBootstrap()
        bootstrap.group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel::class.java)
            .option(ChannelOption.SO_BACKLOG, 128)
            .childOption(ChannelOption.SO_KEEPALIVE, true)
            .childHandler(ServerInitializer)
        println("server start,ip:$host,port:$port")
        bootstrap.bind(host, port).sync().addListener {
            if (it.isSuccess) {
                startScheduleHostRecord(workerGroup)
            }
        }.channel().closeFuture().sync()
    } finally {
        bossGroup.shutdownGracefully()
        workerGroup.shutdownGracefully()
    }

}

fun startScheduleHostRecord(workerGroup:NioEventLoopGroup){
    val provider = HostPlusProvider()
    provider.init()
    workerGroup.scheduleAtFixedRate({
        val records = provider.provide()
        if (records.isNotEmpty()){
            println("schedule host record : $records")
            HostHolder.clear()
            records.forEach { HostHolder.saveHostRecord(it) }
        }
    }, 0, 1, TimeUnit.MINUTES)


}

fun main(args: Array<String>) {

    startServer()

}

class ServerHandler : ChannelInboundHandlerAdapter() {
    private var host: String? = null
    private var port: Int? = null
    private var https: Boolean = false

    private var outBoundChannel: Channel? = null

    override fun channelRead(serverCtx: ChannelHandlerContext, clientMsg: Any) {
        if (clientMsg is FullHttpRequest) {
            val method = clientMsg.method
            if (HttpMethod.CONNECT.equals(method)) {
                https = true
                setHostAndPort(clientMsg)
                val response = DefaultFullHttpResponse(clientMsg.protocolVersion, HttpResponseStatus.OK)
                serverCtx.writeAndFlush(response).addListener {
                    if (it.isSuccess) {
                        with(serverCtx.channel().pipeline()) {
                            remove("httpCodec")
                            remove("aggregator")
                        }
                    }
                }

                return
            }
            setHostAndPort(clientMsg)
        }


        if (outBoundChannel == null) {
            initializeOutBoundChannel(serverCtx, clientMsg)
        } else {
            outBoundChannel!!.writeAndFlush(clientMsg)

        }
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        ctx.close()
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        println("${host}:${port} error close---->")
        ctx.close()
    }

    private fun initializeOutBoundChannel(serverCtx: ChannelHandlerContext, clientMsg: Any) {
        val bootstrap = Bootstrap()
        bootstrap.group(serverCtx.channel().eventLoop())
        bootstrap.channel(NioSocketChannel::class.java)
        val clientFuture = bootstrap.handler(object : ChannelInitializer<SocketChannel>() {

            override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable?) {
                ctx.close()
                outBoundChannel = null
                System.err.println("${host}:${port} out error close---->")
            }

            @Throws(java.lang.Exception::class)
            override fun initChannel(ch: SocketChannel) {
                if (!https) {
                    ch.pipeline().addLast(HttpClientCodec())
                }
                ch.pipeline().addLast(object : ChannelInboundHandlerAdapter() {
                    override fun channelRead(ctx: ChannelHandlerContext?, msg: Any?) {
                        serverCtx.writeAndFlush(msg)
                    }

                    override fun channelInactive(ctx: ChannelHandlerContext) {
                        ctx.close()
                        outBoundChannel = null
                    }

                    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable?) {
                        ctx.close()
                        outBoundChannel = null
                    }
                })
            }
        }).connect(host, port!!).addListener {
            if (it.isSuccess && it is ChannelFuture) {
                outBoundChannel = it.channel()
                it.channel().writeAndFlush(clientMsg)
            }
        }
    }

    private fun setHostAndPort(msg: FullHttpRequest) {
        var uri: String = msg.uri.lowercase(Locale.getDefault())
        if (https) {
            if (!uri.startsWith("https://")) uri = "https://${uri}"
        } else {
            if (!uri.startsWith("http://")) uri = "http://${uri}"
        }
        val url = URL(uri)
        host = url.host.ifEmpty { msg.headers().get("HOST") }
        port = if (url.port != -1) url.port else url.defaultPort
        if (!https) {
            val hostRecord = HostHolder.getHostRecord(host!!, port!!)
            if (hostRecord != null) {
                this.host = hostRecord.targetHost
                this.port = hostRecord.targetPort
                msg.uri = "http://${host}:${port}/${url.path}"
                println("${hostRecord.sourceHost}--->${hostRecord.targetHost}")
            }
        }

    }

}