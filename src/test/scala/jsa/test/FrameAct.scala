package jsa.test

import jsa.core.schedule.ITask
import jsa.kcp.actor.Actors
import jsa.kcp.session.KcpSession

/**
  * Don't say anything,just do it !
  * Created by Amend.inTime on 2016/12/25.
  */
class FrameAct(kcpChannel: KcpSession) extends ITask{
    val taskId = "FrameAct_2122"

    override def doTask(): Unit = {
		//println(s"doTask:${kcpChannel.sessionId} time:${System.currentTimeMillis()}")
		kcpChannel.check()
		/*Actors.get(kcpChannel.sessionId.toInt).execute(() => {
			kcpChannel.update()
		})*/
    }
}

object FrameAct{
    def apply(kcpChannel: KcpSession): FrameAct = new FrameAct(kcpChannel)
}