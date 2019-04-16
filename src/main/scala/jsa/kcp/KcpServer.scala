package jsa.kcp

import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

import jsa.io.session.IoSession
import jsa.core.schedule.{ITask, Schedule}
import jsa.kcp.actor.Actors
import jsa.kcp.session.KcpSession
import jsa.kcp.utils.{KcpConsts, MessageUtils, ScheduleUtil}
import jsa.udp.DatagramServer
import jsa.udp.session.UdpSessions.sessions

/**
  * Don't say anything,just do it !
  * Created by RTunSole on 2018/5/24
  */
trait KcpServer extends DatagramServer {
	private var nodelay = true
	private var interval = KcpConsts.IKCP_INTERVAL
	private var resend = 0
	private var nc = true
	private var sndWnd = KcpConsts.IKCP_WND_SND
	private var rcvWnd = KcpConsts.IKCP_WND_RCV
	private var mtu = KcpConsts.IKCP_MTU_DEF
	private var minRto = KcpConsts.IKCP_RTO_MIN
	private var stream = false
	private val schedule = Schedule()
	
	override val receiveHandle = MessageUtils.receive _
	
	override def start(): Unit = {
		super.start()
		
		//checkUpdate()
	}
	
	private def checkUpdate(): Unit = {
		schedule.startWithFixedDelay(0, 40, TimeUnit.MILLISECONDS, new ITask {
			override val taskId: String = "KcpServer_checkUpdate_100"
			
			override def doTask(): Unit = {
				sessions.values.foreach {
					case s: KcpSession =>s.check()
				}
			}
		})
	}
	
	override def onConnected(session:IoSession,bytes: Array[Byte]): Unit = {
		session match {
			case s:KcpSession =>
				val kcp=s.kcp
				kcp.noDelay(nodelay, interval, resend, nc)
				kcp.wndSize(sndWnd, rcvWnd)
				kcp.setMinRto(minRto)
				kcp.setMtu(mtu)
				kcp.setStream(stream)
				//conv应该在客户端第一次建立时获取
				kcp.setConv(ByteBuffer.wrap(bytes.slice(0,4)).order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt)
				
				//ScheduleUtil.schedule.scheduleOnce(kcp.getInterval, TimeUnit.MILLISECONDS, s.updateTask)
				ScheduleUtil.schedule.startWithFixedDelay(kcp.getInterval,kcp.getInterval, TimeUnit.MILLISECONDS, s.updateTask)
		}
	}
	
	/**
	  * 关掉
	  */
	override def shutdown(): Unit = {
		super.shutdown()
		
		Actors.shutdown()
		schedule.shutdown
	}
	
	def setMtu(mtu: Int): Unit = {
		this.mtu = mtu
	}
	
	def wndSize(sndWnd: Int, revWnd: Int) = {
		this.sndWnd = sndWnd
		this.rcvWnd = revWnd
	}
	
	def setMinRto(minRto: Int) = {
		this.minRto = minRto
	}
	
	def noDelay(nodelay: Boolean, internal: Int, resend: Int, nc: Boolean) = {
		this.nodelay = nodelay
		this.interval = internal
		this.resend = resend
		this.nc = nc
	}
	
	def setStream(isStream: Boolean) = {
		this.stream = isStream
	}
	
}
