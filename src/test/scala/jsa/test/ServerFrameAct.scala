package jsa.test

import jsa.core.schedule.ITask
import jsa.kcp.session.KcpSession
import jsa.udp.session.UdpSessions.sessions
/**
  * Don't say anything,just do it !
  * Created by Amend.inTime on 2016/12/25.
  */
class ServerFrameAct extends ITask{
    val taskId = "ServerFrameAct_1000"

    override def doTask(): Unit = {
		sessions.values.foreach{case s:KcpSession =>s.update()}
    }
}

object ServerFrameAct{
	def apply(): ServerFrameAct = new ServerFrameAct()
}

