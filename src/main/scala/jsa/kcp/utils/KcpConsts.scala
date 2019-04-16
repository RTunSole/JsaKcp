package jsa.kcp.utils

/**
  * Don't say anything,just do it !
  * Created by RTunSole on 2018/5/28
  */
object KcpConsts {
	val IKCP_RTO_NDL = 30 // no delay min rto
	
	val IKCP_RTO_MIN = 100 // normal min rto
	
	val IKCP_RTO_DEF = 200
	val IKCP_RTO_MAX = 60000
	val IKCP_CMD_PUSH = 81 // cmd: push data
	
	val IKCP_CMD_ACK = 82 // cmd: ack
	
	val IKCP_CMD_WASK = 83 // cmd: window probe (ask)
	
	val IKCP_CMD_WINS = 84 // cmd: window size (tell)
	
	val IKCP_ASK_SEND = 1 // need to send IKCP_CMD_WASK
	
	val IKCP_ASK_TELL = 2 // need to send IKCP_CMD_WINS
	
	val IKCP_WND_SND = 32
	val IKCP_WND_RCV = 32
	val IKCP_MTU_DEF = 1400
	val IKCP_ACK_FAST = 3
	val IKCP_INTERVAL = 100
	val IKCP_OVERHEAD = 24
	val IKCP_DEADLINK = 10
	val IKCP_THRESH_INIT = 2
	val IKCP_THRESH_MIN = 2
	val IKCP_PROBE_INIT = 7000 // 7 secs to probe window size
	
	val IKCP_PROBE_LIMIT = 120000 // up to 120 secs to probe window
	
}
