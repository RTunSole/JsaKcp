package jsa.kcp.utils

import java.nio.ByteBuffer

import jsa.io.session.IoSession
import jsa.io.codec.CurrentCodec
import jsa.io.handle.CurrentHandler
import jsa.kcp.actor.Actors
import jsa.kcp.session.KcpSession

/**
  * Don't say anything,just do it !
  * Created by RTunSole on 2018/9/28
  */
object MessageUtils {
	def receive(session: IoSession, bytes: Array[Byte]) = {
		//session.setAttribute("receive",session.getAttribute("receive").get.asInstanceOf[Int]+1)
		//session.setAttribute("receiveSize",session.getAttribute("receiveSize").get.asInstanceOf[Int]+bytes.length)
		if (!session.isClosed) {
			//Actors.get(kcp.getConv).execute(() => {
			Actors.get(session.sessionId.toInt).execute(() => {
				val kcpSession = session.asInstanceOf[KcpSession]
				val kcp = kcpSession.kcp
				val errCode = try {
					kcp.input(bytes)
				}catch {
					case _:Throwable=> -9
				}
				
				errCode match {
					case 0 =>
                        while (kcpSession.canReceive){
							val len = kcp.peekSize
							val bb = ByteBuffer.allocate(len)
							val n = kcp.receive(bb)
							if (n > 0) try{
								val msg = CurrentCodec.decode(bb, kcpSession)(0)
								session.onReceived(msg)
								CurrentHandler.messageReceived(session, msg)
							}catch {
								case e:Exception=>CurrentHandler.exceptionCaught(session, None, e)
							}
						}
					case _ =>
						kcpSession.close()
						CurrentHandler.exceptionCaught(session, None, new Exception(s"receive data error code:${errCode}"))
				}
				//println(s"${kcpSession.sessionId} ${kcpSession.readTimeout} ${kcpSession.lastTime} ")
			})
		}
	}
	
}
