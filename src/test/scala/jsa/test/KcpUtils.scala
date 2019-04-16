package jsa.test

import jsa.kcp.KcpServer

/**
  * Don't say anything,just do it !
  * Created by RTunSole on 2018/5/28
  */
object KcpUtils {
	def initKcp(kcp:KcpServer):KcpServer={
		kcp.noDelay(true,40,0,true)
		
		kcp.wndSize(512, 512)
		kcp.setMtu(300)
		kcp.setMinRto(100)
		
		kcp
	}

}
