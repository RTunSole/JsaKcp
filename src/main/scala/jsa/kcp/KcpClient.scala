package jsa.kcp

import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit

import jsa.core.schedule.ITask
import jsa.io.session.IoChannel
import jsa.kcp.session.KcpSession
import jsa.kcp.utils.{KcpConsts, MessageUtils, ScheduleUtil}
import jsa.udp.DatagramClient

/**
  * Don't say anything,just do it !
  * Created by RTunSole on 2018/5/24
  */
trait KcpClient extends DatagramClient{
    private var conv:Int=_
	private var nodelay = true
	private var interval = KcpConsts.IKCP_INTERVAL
	private var resend = 0
	private var nc = true
	private var sndWnd = KcpConsts.IKCP_WND_SND
	private var rcvWnd = KcpConsts.IKCP_WND_RCV
	private var mtu = KcpConsts.IKCP_MTU_DEF
	private var minRto = KcpConsts.IKCP_RTO_MIN
	private var stream = false

	override val receive = MessageUtils.receive _
	override def createSession(channel:IoChannel)= new KcpSession(channel)

    override def connect(address: InetSocketAddress): Unit = {
        super.connect(address)
        getSession() match {
            case s:KcpSession =>
                val kcp=s.kcp
                kcp.noDelay(nodelay, interval, resend, nc)
                kcp.wndSize(sndWnd, rcvWnd)
                kcp.setMinRto(minRto)
                kcp.setMtu(mtu)
                kcp.setStream(stream)
                kcp.setConv(conv)
				
                //ScheduleUtil.schedule.scheduleOnce(interval,TimeUnit.MILLISECONDS, s.updateTask)
				ScheduleUtil.schedule.startWithFixedDelay(kcp.getInterval,kcp.getInterval, TimeUnit.MILLISECONDS, s.updateTask)
        }
    }

    def setConv(c:Int)={
        this.conv=c
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