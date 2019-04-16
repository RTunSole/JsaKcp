package jsa.test

/**
  * Don't say anything,just do it !
  * Created by RTunSole on 2018/5/24
  */
object TestServer extends App {
	
	new Server().init(KcpUtils.initKcp).bind(8000).start()
	
}
