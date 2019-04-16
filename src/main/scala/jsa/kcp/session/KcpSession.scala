package jsa.kcp.session

import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

import jsa.core.schedule.ITask
import jsa.io.codec.CurrentCodec
import jsa.io.handle.CurrentHandler
import jsa.io.message.Message
import jsa.io.session.IoChannel
import jsa.io.utils.ChannelUtils.channelToDatagramChannel
import jsa.kcp.Kcp
import jsa.kcp.actor.Actors
import jsa.kcp.utils.ScheduleUtil
import jsa.udp.session.UdpSession

import scala.annotation.tailrec
import scala.collection.mutable.ListBuffer

/**
  * Don't say anything,just do it !
  * Created by RTunSole on 2018/5/24
  */
class KcpSession(channel: IoChannel) extends UdpSession(channel) {
	/*	setAttribute("send",0)
		setAttribute("receive",0)
		setAttribute("sendSize",0)
		setAttribute("receiveSize",0)*/
	
	val kcp = new Kcp() {
		/**
		  * 经过kcp处理过的数据包（原始数据来自send（bb）中的bb）
		  *
		  * @param msg
		  */
		override def writeKcp(msg: ByteBuffer): Unit = {
			//setAttribute("send",getAttribute("send").get.asInstanceOf[Int]+1)
			//setAttribute("sendSize",getAttribute("sendSize").get.asInstanceOf[Int]+msg.remaining())
			if (!isClosed) {
				var sendSize = 0
				val totalSize = msg.remaining()
				while (sendSize < totalSize) {
					val size = channel.send(msg, remoteAddress)
					sendSize = sendSize + size
					//if(sendSize < totalSize) println("===================================")
				}
			}
		}
	}
	
	override def write[T](msg: Message[T]): Unit = {
		if (!isClosed && msg !=null) {
			Actors.get(sessionId.toInt).execute(() => {
				if (canSend()) {
					val byteMsg = CurrentCodec.encode(msg, this).array()
					val errCode = try {
						kcp.send(byteMsg)
					} catch {
						case _: Throwable => -9
					}
					
					errCode match {
						case 0 =>
							if (isFastFlush) update() else setNextUpdate(-1)
						case _ =>
							close()
							CurrentHandler.exceptionCaught(this, Option(msg), new Exception(s"send data error code:${errCode} "))
					}
				}
			})
		}
	}
	
	def isFastFlush = true
	
	/**
	  * Returns {@code true} if there are bytes can be received.
	  *
	  * @return
	  */
	def canReceive: Boolean = kcp.canReceive
	
	def setNextUpdate(next: Long) = {
		kcp.setNextUpdate(next)
	}
	
	/**
	  * Returns {@code true} if the kcp can send more bytes.
	  *
	  * @param curCanSend last state of canSend
	  * @return { @code true} if the kcp can send more bytes
	  */
	def canSend(curCanSend: Boolean = true): Boolean = kcp.canSend(curCanSend)
	
	def update(cur: Long = System.currentTimeMillis): Unit = {
		if (!isClosed) {
			//println(cur)
			//update kcp status
			try {
				kcp.update(cur)
				kcp.setNextUpdate(kcp.check(cur))
			} catch {
				case _: Throwable => close()
			}
		}
	}
	
	val updateTask = new ITask {
		override val taskId: String = "KcpServer_checkUpdate_100"
		
		override def doTask(): Unit = {
			check()
		}
	}
	
	def check(): Unit = {
		if (!isClosed) {
			if (kcp.checkFlush) {
				Actors.get(sessionId.toInt).execute(() => {
					//update kcp status
					val cur = System.currentTimeMillis
					try {
						if (cur >= kcp.getNextUpdate) {
							update(cur)
						}
						/*val interval = if (kcp.getNextUpdate == cur) 10 else (kcp.getNextUpdate - cur).toInt
						ScheduleUtil.schedule.scheduleOnce(interval, TimeUnit.MILLISECONDS, updateTask)*/
					} catch {
						case _: Throwable => close()
					}
				})
			} /*else {
				ScheduleUtil.schedule.scheduleOnce(kcp.getInterval, TimeUnit.MILLISECONDS, updateTask)
			}*/
		}
	}
	
	override def close(im: Boolean = false): Unit = {
		if (channel.serverMode) super.close() else super.close(im)
		
		kcp.release()
	}
	
	
}
