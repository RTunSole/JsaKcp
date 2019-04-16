package jsa.test

import jsa.io.session.IoChannel
import jsa.kcp.KcpServer
import jsa.kcp.session.KcpSession

/**
  * Don't say anything,just do it !
  * Created by RTunSole on 2018/9/21
  */
class Server extends KcpServer{
	override def createActionHandler()=new ServerHandle()
	override def createSession(channel:IoChannel)= new KcpSession(channel){
        override val readTimeout = 10*1000
	}
}
