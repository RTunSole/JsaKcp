package jsa.kcp

import java.nio.{ByteBuffer, ByteOrder}

import jsa.io
import jsa.io.IoBuffer

import scala.annotation.tailrec
import scala.collection.mutable.ListBuffer

/**
  * Don't say anything,just do it !
  * Created by RTunSole.liu on 2018/10/7.
  */
trait Kcp {
	val IKCP_RTO_NDL = 30 // no delay min rto
	val IKCP_RTO_MIN = 100 // normal min rto
	val IKCP_RTO_DEF = 200
	val IKCP_RTO_MAX = 60000
	val IKCP_CMD_PUSH: Byte = 81 // cmd: push data
	val IKCP_CMD_ACK: Byte = 82 // cmd: ack
	val IKCP_CMD_WASK: Byte = 83 // cmd: window probe (ask)
	val IKCP_CMD_WINS: Byte = 84 // cmd: window size (tell)
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
	
	private var conv = 0
	private var mtu = IKCP_MTU_DEF
	private var mss = mtu - IKCP_OVERHEAD
	private var state = 0
	private var snd_una: Long = 0
	private var snd_nxt: Long = 0
	private var rcv_nxt: Long = 0
	private val ts_recent: Long = 0
	private val ts_lastack: Long = 0
	private var ssthresh = IKCP_THRESH_INIT
	private var rx_rttval = 0
	private var rx_srtt = 0
	private var rx_rto = IKCP_RTO_DEF
	private var rx_minrto = IKCP_RTO_MIN
	private var snd_wnd = IKCP_WND_SND
	private var rcv_wnd = IKCP_WND_RCV
	
	private var rmt_wnd = IKCP_WND_RCV
	private var cwnd = 0
	private var probe = 0
	private var current: Long = 0
	private var interval = IKCP_INTERVAL
	private var ts_flush: Long = IKCP_INTERVAL
	private var xmit = 0
	private var nodelay = true
	private var updated: Boolean = false
	private var ts_probe: Long = 0
	private var probe_wait = 0
	private var dead_link = IKCP_DEADLINK
	private var incr = 0
	private val snd_queue = new ListBuffer[Segment]()
	private val rcv_queue = new ListBuffer[Segment]()
	private val snd_buf = new ListBuffer[Segment]()
	private val rcv_buf = new ListBuffer[Segment]()
	private val acklist = new ListBuffer[Int]()
	private var buffer = IoBuffer(0)
	private var fastresend = 0
	private var nocwnd = true
	private var stream = false
	
	private var nextUpdate: Long = 0 //the next update time.
	
	
	private def _ibound_(lower: Int, middle: Int, upper: Int) = Math.min(Math.max(lower, middle), upper)
	
	private def _itimediff(later: Long, earlier: Long) = (later - earlier).toInt
	
	private def long2Uint(n: Long) = n & 0x00000000FFFFFFFFL
	
	/**
	  * SEGMENT
	  */
	case class Segment(val conv: Int = 0, var cmd: Byte = 0, var frg: Byte = 0, var wnd: Short = 0,
					   var ts: Long = 0, var sn: Long = 0, var una: Long = 0, val data: IoBuffer = IoBuffer(0)) {
		var resendts: Long = 0
		var rto = 0
		var fastack = 0
		var xmit = 0
		
		/**
		  * encode a segment into buffer
		  *
		  * @return
		  */
		def encode() = {
			val buf = IoBuffer(IKCP_OVERHEAD)
			buf.putInt(conv)
			buf.put(cmd)
			buf.put(frg)
			buf.putShort(wnd)
			buf.putInt(ts.toInt)
			buf.putInt(sn.toInt)
			buf.putInt(una.toInt)
			buf.putInt(data.readableBytes())
			//buf.flip()
			
			buf.array()
		}
	}
	
	/**
	  * check the size of next message in the recv queue
	  *
	  * @return
	  */
	def peekSize: Int = {
		if (rcv_queue.isEmpty) -1
		else {
			val seq = rcv_queue.head
			if (seq.frg == 0) seq.data.readableBytes()
			else {
				if (rcv_queue.size < seq.frg + 1) -1
				else {
					def loopList(list: ListBuffer[Segment], len: Int): Int = {
						list.headOption match {
							case Some(seg) =>
								val length = len + seg.data.readableBytes()
								if (seg.frg == 0) length
								else loopList(list.tail, length)
							case None => len
						}
					}
					
					loopList(rcv_queue, 0)
				}
			}
		}
	}
	
	/**
	  * user/upper level recv: returns size, returns below zero for EAGAIN
	  *
	  * @param buffer
	  * @return
	  */
	def receive(buffer: ByteBuffer): Int = {
		if (rcv_queue.isEmpty) -1
		else {
			val pSize = peekSize
			if (pSize < 0) -2
			else {
				val recover = rcv_queue.size >= rcv_wnd
				
				// merge fragment.
				def loopList(list: ListBuffer[Segment], count: Int, len: Int): (Int, Int) = {
					list.headOption match {
						case Some(seg) =>
							val length = len + seg.data.readableBytes()
							buffer.put(seg.data.array())
							
							if (seg.frg == 0) (count + 1, length)
							else loopList(list.tail, count + 1, length)
						case None => (count, len)
					}
				}
				
				val (c, len) = loopList(rcv_queue, 0, 0)
				(0 until c).foreach(_ => {
					rcv_queue.remove(0)
				})
				
				if (len != pSize) -1
				else {
					// move available data from rcv_buf -> rcv_queue
					def loopList2(list: ListBuffer[Segment], count: Int): Int = {
						list.headOption match {
							case Some(seg) =>
								if (seg.sn == rcv_nxt && rcv_queue.size < rcv_wnd) {
									rcv_queue += (seg)
									rcv_nxt += 1
									loopList2(list.tail, count + 1)
								} else count
							case None => count
						}
					}
					
					val count = loopList2(rcv_buf, 0)
					(0 until count).foreach(_ => {
						rcv_buf.remove(0)
					})
					
					// fast recover
					if (rcv_queue.size < rcv_wnd && recover) { // ready to send back IKCP_CMD_WINS in ikcp_flush
						// tell remote my window size
						probe |= IKCP_ASK_TELL
					}
					
					len
				}
			}
		}
	}
	
	/**
	  * user/upper level send, returns below zero for error
	  *
	  * @param data
	  * @return
	  */
	def send(data: Array[Byte]): Int = {
		data.length match {
			case 0 => -1
			case len =>
				// append to previous segment in streaming mode (if possible)
				var index = if (this.stream && !this.snd_queue.isEmpty) {
					val seg = snd_queue.reverse.head
					val extend = Math.min(len, seg.data.writableBytes())
					seg.data.put(data, 0, extend)
					
					extend
				} else 0
				
				var left = len - index
				val count = (if (left <= mss) 1 else (left + mss - 1) / mss)
				count match {
					case _ if count > 255 => -2
					case _ =>
						//fragment
						(0 until count).foreach(i => {
							val size = if (left > mss) mss else left
							val seg = Segment(conv, 0, frg = if (this.stream) 0 else (count - i - 1).toByte, data = io.IoBuffer(size))
							seg.data.put(data, index, size)
							index += size
							left -= size
							snd_queue += seg
						})
						
						0
				}
		}
	}
	
	/**
	  *
	  * when you received a low level packet (eg. UDP packet), call it
	  *
	  * @param bytes
	  * @return
	  */
	def input(bytes: Array[Byte]): Int = {
		val data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
		val una_temp = snd_una
		var flag = 0
		var maxAck = 0
		var checkTs=0
		
		@tailrec
		def parseData(data: ByteBuffer): Int = {
			if (data.remaining() > IKCP_OVERHEAD) {
				val conv = data.getInt()
				conv match {
					case _ if (this.conv != conv) => -1
					case _ =>
						val cmd = data.get
						val frg = data.get
						val wnd = data.getShort
						val ts = data.getInt
						val sn = data.getInt
						val una = data.getInt
						val len = data.getInt
						
						checkTs=ts
						
						if (data.remaining() < len) -2
						else {
							val uintCurrent = long2Uint(current)
							cmd match {
								case IKCP_CMD_ACK =>
									rmt_wnd = wnd
									parse_una(una)
									shrink_buf()
									val rtt = _itimediff(uintCurrent, ts)
									if (rtt >= 0) update_ack(rtt)
									parse_ack(sn)
									shrink_buf()
									if (flag == 0) {
										flag = 1
										maxAck = sn
									} else if (_itimediff(sn, maxAck) > 0) maxAck = sn
									parseData(data)
								case IKCP_CMD_PUSH =>
									rmt_wnd = wnd
									parse_una(una)
									shrink_buf()
									
									if (_itimediff(sn, rcv_nxt + rcv_wnd) < 0) {
										ack_push(sn, ts)
										if (_itimediff(sn, rcv_nxt) >= 0) {
											val seg = Segment(conv, cmd, frg, wnd, ts, sn, una, IoBuffer(len))
											if (len > 0) {
												seg.data.put(data, len)
											}
											
											parse_data(seg)
										} else {
											data.position(data.position() + len)
										}
									} else {
										data.position(data.position() + len)
									}
									parseData(data)
								case IKCP_CMD_WASK =>
									// ready to send back IKCP_CMD_WINS in Ikcp_flush
									// tell remote my window size
									probe |= IKCP_ASK_TELL
									parseData(data)
								case IKCP_CMD_WINS => parseData(data)
								case _ => -3
							}
						}
				}
			} else 0
		}
		
		bytes.length match {
			case n if (n < IKCP_OVERHEAD) => -1
			case _ =>
				val result = parseData(data)
				result match {
					case 0 =>
						if (flag != 0) parse_fastack(maxAck,checkTs)
						if (_itimediff(snd_una, una_temp) > 0) {
							if (this.cwnd < this.rmt_wnd) {
								if (this.cwnd < this.ssthresh) {
									this.cwnd += 1
									this.incr += mss
								}
								else {
									if (this.incr < mss) this.incr = mss
									this.incr += (mss * mss) / this.incr + (mss / 16)
									if ((this.cwnd + 1) * mss <= this.incr) this.cwnd += 1
								}
								
								if (this.cwnd > this.rmt_wnd) {
									this.cwnd = this.rmt_wnd
									this.incr = this.rmt_wnd * mss
								}
							}
						}
						0
					case n => n
				}
		}
	}
	
	private def shrink_buf(): Unit = {
		if (snd_buf.size > 0) snd_una = snd_buf.head.sn
		else snd_una = snd_nxt
	}
	
	private def parse_ack(sn: Int): Unit = {
		if (!(_itimediff(sn, snd_una) < 0 || _itimediff(sn, snd_nxt) >= 0)) {
			@tailrec
			def loopList(queue: ListBuffer[Segment]): Unit = {
				queue.headOption match {
					case Some(seg) =>
						if (sn == seg.sn) {
							snd_buf -= seg
						} else if (_itimediff(sn, seg.sn) >= 0) loopList(queue.tail)
					case None =>
				}
			}
			
			loopList(snd_buf)
		}
	}
	
	
	private def parse_una(una: Int): Unit = {
		@tailrec
		def loopList(queue: ListBuffer[Segment], c: Int): Int = {
			queue.headOption match {
				case Some(seg) =>
					if (_itimediff(una, seg.sn) > 0) {
						loopList(queue.tail, c + 1)
					} else c
				case None => c
			}
		}
		
		val count = loopList(snd_buf, 0)
		(0 until count).foreach(_ => snd_buf.remove(0))
	}
	
	private def parse_fastack(sn: Int,ts:Int): Unit = {
		if (!(_itimediff(sn, snd_una) < 0 || _itimediff(sn, snd_nxt) >= 0)) {
			@tailrec
			def loopList(queue: ListBuffer[Segment]): Unit = {
				queue.headOption match {
					case Some(seg) =>
						if (_itimediff(sn, seg.sn) >= 0 && sn != seg.sn && _itimediff(seg.ts,ts) <= 0) {
							seg.fastack += 1
							loopList(queue.tail)
						}
					case None =>
				}
			}
			
			loopList(snd_buf)
		}
	}
	
	/**
	  * update ack.
	  *
	  * @param rtt
	  */
	private def update_ack(rtt: Int): Unit = {
		if (rx_srtt == 0) {
			rx_srtt = rtt
			rx_rttval = rtt / 2
		}
		else {
			var delta = rtt - rx_srtt
			if (delta < 0) delta = -delta
			rx_rttval = (3 * rx_rttval + delta) / 4
			rx_srtt = (7 * rx_srtt + rtt) / 8
			if (rx_srtt < 1) rx_srtt = 1
		}
		val rto = rx_srtt + Math.max(interval, 4 * rx_rttval)
		rx_rto = _ibound_(rx_minrto, rto, IKCP_RTO_MAX)
	}
	
	/**
	  * ack append
	  *
	  * @param sn
	  * @param ts
	  */
	private def ack_push(sn: Int, ts: Int): Unit = {
		acklist += (sn)
		acklist += (ts)
	}
	
	private def parse_data(newseg: Segment): Unit = {
		val sn = newseg.sn
		if (!(_itimediff(sn, rcv_nxt + rcv_wnd) >= 0 || _itimediff(sn, rcv_nxt) < 0)) {
			var temp = -1
			var repeat = false
			
			@tailrec
			def loopList(queue: ListBuffer[Segment], index: Int): Unit = {
				queue.headOption match {
					case Some(seg) =>
						if (sn == seg.sn) {
							repeat = true
						} else if (_itimediff(sn, seg.sn) > 0) {
							temp = index
						} else loopList(queue.tail, index - 1)
					case None =>
				}
			}
			
			loopList(rcv_buf.reverse, rcv_buf.size - 1)
			
			if (!repeat)
				if (temp == -1) rcv_buf.insert(0, newseg) else rcv_buf.insert(temp + 1, newseg)
			
			@tailrec
			def loopList2(queue: ListBuffer[Segment], c: Int): Int = {
				queue.headOption match {
					case Some(seg) =>
						if (seg.sn == rcv_nxt && rcv_queue.size < rcv_wnd) {
							rcv_queue += (seg)
							rcv_nxt += 1
							loopList2(queue.tail, c + 1)
						} else c
					case None => c
				}
			}
			
			// move available data from rcv_buf -> rcv_queue
			val c = loopList2(rcv_buf, 0)
			(0 until c).foreach(_ => rcv_buf.remove(0))
		}
	}
	
	private def wnd_unused = {
		if (rcv_queue.size < rcv_wnd) rcv_wnd - rcv_queue.size else 0
	}
	
	def checkFlush: Boolean = {
		if (acklist.size > 0) return true
		if (probe != 0) return true
		if (snd_buf.size > 0) return true
		if (snd_queue.size > 0) return true
		false
	}
	
	/**
	  * force flush
	  */
	private def flush(): Unit = {
		// flush acknowledges
		val seg = Segment(conv = conv, cmd = IKCP_CMD_ACK, wnd = wnd_unused.toShort, una = rcv_nxt)
		val c = acklist.size / 2
		(0 until c).foreach(i => {
			if (buffer.readableBytes() + IKCP_OVERHEAD > mtu) {
				writeKcp(buffer.toByteBuffer())
				buffer = IoBuffer((mtu + IKCP_OVERHEAD) * 3)
			}
			//seg = Segment(conv = conv, cmd = IKCP_CMD_ACK, wnd = wnd_unused.toShort, una = rcv_nxt, sn = acklist(i * 2 + 0).toLong, ts = acklist(i * 2 + 1).toLong)
			seg.sn=acklist(i * 2 + 0).toLong
			seg.ts = acklist(i * 2 + 1).toLong
			buffer.put(seg.encode())
		})
		
		acklist.clear()
		// probe window size (if remote window size equals zero)
		if (rmt_wnd == 0) {
			if (probe_wait == 0) {
				probe_wait = IKCP_PROBE_INIT
				ts_probe = current + probe_wait
			}
			else if (_itimediff(current, ts_probe) >= 0) {
				if (probe_wait < IKCP_PROBE_INIT) probe_wait = IKCP_PROBE_INIT
				probe_wait += probe_wait / 2
				if (probe_wait > IKCP_PROBE_LIMIT) probe_wait = IKCP_PROBE_LIMIT
				ts_probe = current + probe_wait
				probe |= IKCP_ASK_SEND
			}
		}else {
			ts_probe = 0
			probe_wait = 0
		}
		
		// flush window probing commands
		if ((probe & IKCP_ASK_SEND) != 0) {
			seg.cmd=IKCP_CMD_WASK.toByte
			//val seg = Segment(conv = conv, cmd = IKCP_CMD_WASK.toByte, wnd = segment.wnd, sn = segment.sn)
			if (buffer.readableBytes() + IKCP_OVERHEAD > mtu) {
				writeKcp(buffer.toByteBuffer())
				buffer = IoBuffer((mtu + IKCP_OVERHEAD) * 3)
			}
			buffer.put(seg.encode())
		}
		
		if ((probe & IKCP_ASK_TELL) != 0) {
			//val seg = Segment(conv = conv, cmd = IKCP_CMD_WINS.toByte, wnd = segment.wnd, sn = segment.sn)
			seg.cmd=IKCP_CMD_WINS.toByte
			if (buffer.readableBytes() + IKCP_OVERHEAD > mtu) {
				writeKcp(buffer.toByteBuffer())
				buffer = IoBuffer((mtu + IKCP_OVERHEAD) * 3)
			}
			buffer.put(seg.encode())
		}
		probe = 0
		
		// move data from snd_queue to snd_buf
		// calculate window size
		var cwnd_temp = Math.min(snd_wnd, rmt_wnd)
		if (!nocwnd) cwnd_temp = Math.min(cwnd, cwnd_temp)
		
		@tailrec
		def loopList(queue: ListBuffer[Segment], c: Int): Int = {
			queue.headOption match {
				case Some(segment) =>
					if (_itimediff(snd_nxt, snd_una + cwnd_temp) < 0) {
						//newseg =Segment(conv, IKCP_CMD_PUSH, frg = seg.frg, wnd = seg.wnd, ts = long2Uint(current), sn = snd_nxt, una = rcv_nxt, data = seg.data)
						val newseg = segment
						newseg.cmd = IKCP_CMD_PUSH
						newseg.wnd = seg.wnd
						newseg.ts = long2Uint(current)
						newseg.sn = snd_nxt
						newseg.una = rcv_nxt
						newseg.resendts = current
						newseg.rto = rx_rto
						newseg.fastack = 0
						newseg.xmit = 0
						snd_nxt += 1
						snd_buf += (segment)
						
						loopList(queue.tail, c + 1)
					} else c
				case None => c
			}
		}
		
		val cc = loopList(snd_queue, 0)
		(0 until cc).foreach(_ => snd_queue.remove(0))
		
		var change = 0
		var lost = 0
		// calculate resent
		val resent = if (fastresend > 0) fastresend else Integer.MAX_VALUE
		val rtomin = if (!nodelay) rx_rto >> 3 else 0
		// flush data segments
		for (segment <- snd_buf) {
			var needsend = false
			if (segment.xmit == 0) {
				needsend = true
				segment.xmit += 1
				segment.rto = rx_rto
				segment.resendts = current + segment.rto + rtomin
			}
			else if (_itimediff(current, segment.resendts) >= 0) {
				needsend = true
				segment.xmit += 1
				xmit += 1
				if (!nodelay) segment.rto += rx_rto
				else segment.rto += rx_rto / 2
				segment.resendts = current + segment.rto
				lost = 1
			}
			else if (segment.fastack >= resent) {
				needsend = true
				segment.xmit += 1
				segment.fastack = 0
				segment.resendts = current + segment.rto
				change += 1
			}
			
			if (needsend) {
				segment.ts = long2Uint(current)
				segment.wnd = seg.wnd
				segment.una = rcv_nxt
				
				val need = IKCP_OVERHEAD + segment.data.readableBytes()
				if (buffer.readableBytes() + need > mtu) {
					writeKcp(buffer.toByteBuffer())
					buffer = IoBuffer((mtu + IKCP_OVERHEAD) * 3)
				}
				buffer.put(segment.encode())
				if (segment.data.readableBytes() > 0) buffer.put(segment.data.array())
				if (segment.xmit >= dead_link) state = -1
			}
		}
		
		// flash remain segments
		if (buffer.readableBytes() > 0) {
			writeKcp(buffer.toByteBuffer())
			buffer = IoBuffer((mtu + IKCP_OVERHEAD) * 3)
		}
		
		// update ssthresh
		if (change != 0) {
			val inflight = (snd_nxt - snd_una).toInt
			ssthresh = inflight / 2
			if (ssthresh < IKCP_THRESH_MIN) ssthresh = IKCP_THRESH_MIN
			cwnd = ssthresh + resent
			incr = cwnd * mss
		}
		
		if (lost != 0) {
			ssthresh = cwnd / 2
			if (ssthresh < IKCP_THRESH_MIN) ssthresh = IKCP_THRESH_MIN
			cwnd = 1
			incr = mss
		}
		if (cwnd < 1) {
			cwnd = 1
			incr = mss
		}
	}
	
	/**
	  * update state (call it repeatedly, every 10ms-100ms), or you can ask
	  * ikcp_check when to call it again (without ikcp_input/_send calling).
	  *
	  * @param current current timestamp in millisec.
	  */
	def update(current: Long): Unit = {
		this.current = current
		if (!updated) {
			updated = true
			ts_flush = this.current
		}
		var slap = _itimediff(this.current, ts_flush)
		if (slap >= 10000 || slap < -10000) {
			ts_flush = this.current
			slap = 0
		}
		/*if (slap >= 0) {
			ts_flush += interval
			if (_itimediff(this.current, ts_flush) >= 0) ts_flush = this.current + interval
			
			flush()
		}*/
		
		if (slap >= 0) {
			ts_flush += interval
			if (_itimediff(this.current, ts_flush) >= 0) ts_flush = this.current + interval
		} else ts_flush = this.current + interval
		
		flush()
	}
	
	def canReceive: Boolean = {
		rcv_queue.headOption match {
			case Some(seg) =>
				seg.frg match {
					case 0 => true
					case _ =>
						// Some segments have not arrived yet
						if (rcv_queue.size < seg.frg + 1) {
							false
						}else true
				}
			case None => false
		}
		
		/*if (rcv_queue.isEmpty) return false
		val seg = rcv_queue.head
		if (seg.frg == 0) return true
		if (rcv_queue.size < seg.frg + 1) { // Some segments have not arrived yet
			return false
		}
		true*/
	}
	
	/**
	  * Returns {@code true} if the kcp can send more bytes.
	  *
	  * @param curCanSend last state of canSend
	  * @return { @code true} if the kcp can send more bytes
	  */
	def canSend(curCanSend: Boolean = true): Boolean = {
		val max = getSndWnd() * 2
		val _waitSnd = waitSnd()
		if (curCanSend) _waitSnd < max
		else {
			val threshold = Math.max(1, max / 2)
			_waitSnd < threshold
		}
	}
	
	/**
	  * Determine when should you invoke ikcp_update: returns when you should
	  * invoke ikcp_update in millisec, if there is no ikcp_input/_send calling.
	  * you can call ikcp_update in that time, instead of call update repeatly.
	  * Important to reduce unnacessary ikcp_update invoking. use it to schedule
	  * ikcp_update (eg. implementing an epoll-like mechanism, or optimize
	  * ikcp_update when handling massive kcp connections)
	  *
	  * @param current
	  * @return
	  */
	def check(current: Long): Long = {
		val cur = current
		updated match {
			case false => cur
			case true =>
				var ts_flush_temp = this.ts_flush
				var slap = _itimediff(cur, ts_flush_temp)
				
				if (slap >= 10000 || slap < -10000) {
					ts_flush_temp = cur
					slap = 0
				}
				
				if (slap >= 0) cur
				else {
					val tm_flush = _itimediff(ts_flush_temp, cur)
					var tm_packet = Integer.MAX_VALUE
					
					@tailrec
					def loopList(list: ListBuffer[Segment]): Long = {
						list.headOption match {
							case Some(seg) =>
								val diff = _itimediff(seg.resendts, cur)
								if (diff <= 0) cur
								else {
									if (diff < tm_packet) tm_packet = diff
									
									loopList(list.tail)
								}
							case None =>
								var minimal = if (tm_packet < tm_flush) tm_packet else tm_flush
								if (minimal >= interval) minimal = interval
								
								cur + minimal
						}
					}
					
					loopList(snd_buf)
				}
		}
	}
	
	/**
	  * change MTU size, default is 1400
	  *
	  * @param mtu
	  * @return
	  */
	def setMtu(mtu: Int): Int = {
		if (mtu < 50 || mtu < IKCP_OVERHEAD) return -1
		this.buffer = IoBuffer((mtu + IKCP_OVERHEAD) * 3)
		this.mtu = mtu
		mss = mtu - IKCP_OVERHEAD
		
		0
	}
	
	/**
	  * conv
	  *
	  * @param conv
	  */
	def setConv(conv: Int): Unit = {
		this.conv = conv
	}
	
	/**
	  * conv
	  *
	  * @return
	  */
	def getConv(): Int = conv
	
	/**
	  * interval per update
	  *
	  * @param interval
	  * @return
	  */
	def interval(interval: Int): Int = {
		if (interval > 5000) this.interval = 5000
		else if (interval < 10) this.interval = 10
		else this.interval = interval
		0
	}
	
	def getInterval=interval
	/**
	  * fastest: ikcp_nodelay(kcp, 1, 20, 2, 1) nodelay: 0:disable(default),
	  * 1:enable interval: internal update timer interval in millisec, default is
	  * 100ms resend: 0:disable fast resend(default), 1:enable fast resend nc:
	  * 0:normal congestion control(default), 1:disable congestion control
	  *
	  * @param nodelay
	  * @param interval
	  * @param resend
	  * @param nc
	  * @return
	  */
	def noDelay(nodelay: Boolean, interval: Int, resend: Int, nc: Boolean): Int = {
		this.nodelay = nodelay
		nocwnd = nc
		
		if (nodelay) rx_minrto = IKCP_RTO_NDL
		else rx_minrto = IKCP_RTO_MIN
		
		this.interval = if (interval > 5000) 5000
		else if (interval < 10) 10 else interval
		
		if (resend >= 0) fastresend = resend
		
		0
	}
	
	/**
	  * set maximum window size: sndwnd=32, rcvwnd=32 by default
	  *
	  * @param sndwnd
	  * @param rcvwnd
	  * @return
	  */
	def wndSize(sndwnd: Int, rcvwnd: Int): Int = {
		if (sndwnd > 0) snd_wnd = sndwnd
		if (rcvwnd > 0) rcv_wnd = rcvwnd
		0
	}
	
	/**
	  * get how many packet is waiting to be sent
	  *
	  * @return
	  */
	def waitSnd(): Int = snd_buf.size + snd_queue.size
	
	def getSndWnd(): Int = snd_wnd
	
	def setNextUpdate(nextUpdate: Long): Unit = {
		this.nextUpdate = nextUpdate
	}
	
	def getNextUpdate: Long = nextUpdate
	
	def isStream: Boolean = stream
	
	def setStream(stream: Boolean): Unit = {
		this.stream = stream
	}
	
	def setMinRto(min: Int): Unit = {
		rx_minrto = min
	}
	
	/**
	  * 释放内存
	  */
	def release(): Unit = {
		rcv_buf.clear()
		rcv_queue.clear()
		
		snd_buf.clear()
		snd_queue.clear()
		
		acklist.clear()
	}
	
	def writeKcp(data: ByteBuffer)
}