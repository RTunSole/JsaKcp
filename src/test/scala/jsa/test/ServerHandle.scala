package jsa.test

import java.nio.ByteBuffer

import jsa.io.handle.IActionHandler
import jsa.io.message.{ByteMessage, Message}
import jsa.io.session.IoSession

/**
  * Don't say anything,just do it !
  * Created by RTunSole on 2018/5/24
  */
class ServerHandle extends IActionHandler {
	
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
		session.write(msg)
	}
	
	override def messageSent[T](session: IoSession, msg: Message[T]): Unit = {
	
	}
	
	
	override def exceptionCaught[T](session: IoSession, msg: Option[Message[T]], cause: Throwable): Unit = {
		cause.printStackTrace()
	}

}
