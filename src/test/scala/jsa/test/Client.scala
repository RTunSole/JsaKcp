package jsa.test

import jsa.io.handle.IActionHandler
import jsa.kcp.KcpClient

/**
  * Don't say anything,just do it !
  * Created by RTunSole on 2018/9/21
  */
class Client extends KcpClient{
	override def createActionHandler():IActionHandler = new TestHandle()
}
