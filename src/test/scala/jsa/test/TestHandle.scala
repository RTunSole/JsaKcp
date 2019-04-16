package jsa.test

import java.nio.ByteBuffer

import jsa.io.handle.IActionHandler
import jsa.io.message.Message
import jsa.io.session.IoSession

/**
  * Don't say anything,just do it !
  * Created by RTunSole on 2018/5/24
  */
class TestHandle extends IActionHandler {
	private var start = 0L
	private var c = 0
	private var interval = 0L
	
	override def sessionCreated(session: IoSession): Unit = {
	
	}
	
	override def sessionIdle(session: IoSession): Unit = {
	
	}
	
	override def sessionClosed(session: IoSession): Unit = {
		//System.out.println("客户端离开:" + session)
		//System.out.println("waitSnd:" + channel.kcp.waitSnd)
		//println(s"${session} is closed ")
	}
	
	override def messageReceived[T](session: IoSession, msg: Message[T]): Unit = {
		/*val content = bb.toString(Charset.forName("utf-8"))
		System.out.println("conv:" + channel.kcp.getConv() + " recv:" + content + " kcp-->" + channel)
		val buf = PooledByteBufAllocator.DEFAULT.buffer(2048)
		buf.writeBytes(content.getBytes(Charset.forName("utf-8")))
		
		channel.send(buf) //测试发送到服务器
		bb.release()
		*/
		/*val content = bb.toString(Charset.forName("utf-8"))
		System.out.println("conv:" + channel.kcp.getConv() + " recv:" + content + " kcp-->" + channel)*/
		val b=ByteBuffer.wrap(msg.data.asInstanceOf[Array[Byte]])
		val i=b.getInt
		val t=System.currentTimeMillis()-session.getAttribute(i).get.asInstanceOf[Long]
		session.setAttribute(i,t)

		//println(s"re RTT:${i}->${t}")
		println(s"re RTT:${i}->${t}->${System.currentTimeMillis()-session.getAttribute("start").get.asInstanceOf[Long]}")
		if (c == 0) {
			start = System.currentTimeMillis
			interval= System.nanoTime()
		}
		
		c += 1
		if (c >= 300) {
			var sum=0L
			(0 until 300).foreach(i=>{
				sum +=session.getAttribute(i).get.asInstanceOf[Long]
			})
			println(s"send:${session.getAttribute("send")} receive:${session.getAttribute("receive")} average ${sum/300}" )
			println(s"sendSize:${session.getAttribute("sendSize")} receiveSize:${session.getAttribute("receiveSize")} cost:${(System.currentTimeMillis - session.getAttribute("start").get.asInstanceOf[Long])}" )
			//c = 0
			
		}else{
			//println(s"c:${c} time:${System.nanoTime - interval}")
			//session.write(msg)
		}
		
		interval= System.nanoTime
		
		//session.write(msg)
		
	}
	
	override def messageSent[T](session: IoSession, msg: Message[T]): Unit = {

	}
	
	override def exceptionCaught[T](session: IoSession, msg: Option[Message[T]], cause: Throwable): Unit = {
		cause.printStackTrace()
	}
	
}
