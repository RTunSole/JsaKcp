package jsa.test

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{Executors, TimeUnit}

import jsa.core.schedule.{ITask, Schedule}
import jsa.io.message.ByteMessage
import jsa.kcp.session.KcpSession

/**
  * Don't say anything,just do it !
  * Created by RTunSole on 2018/5/24
  */
object TestClient extends App {
	val schedule = Schedule()
	val conv=new AtomicInteger()
	
	//val executor = Executors.newFixedThreadPool(10) //SingleThreadExecutor()
	(0 until 1).foreach(_=>start)
	
	
	def start = {
		val client = new Client()

		client.noDelay(true, 40, 0, true)
		client.wndSize(512, 512)
		client.setMtu(300)
		client.setMinRto(100)
		client.setConv(conv.getAndIncrement())

		//val host="192.168.188.154" //8008
		//val host="192.168.188.115" 9000
		val host="127.0.0.1"
		client.connect(new InetSocketAddress(host, 8000))
        client.getSession().setAttribute("start",System.currentTimeMillis())
		schedule.startWithFixedDelay(0, 40, TimeUnit.MILLISECONDS, FrameAct(client.getSession().asInstanceOf[KcpSession]))
		val data=ByteBuffer.allocate(194)
        (0 until 194).foreach(i=>data.put(i.toByte))

		val cc=data.array()

		var count=0
		schedule.startAtFixedRate(0, 20, TimeUnit.MILLISECONDS, new ITask {
			override val taskId: String = "test_______robot"
			
			override def doTask(): Unit = {
				if(count<300){
					val b=ByteBuffer.allocate(198)
					b.putInt(count)
					b.put(cc)
					val time=System.currentTimeMillis()
					//println(s"rtt :${i} ${time}")
					client.getSession().setAttribute(count,time)
					
					val bytes = b.array()
					client.write(ByteMessage(1,bytes))
					
					count +=1
				}else{
					schedule.cancelTask(taskId)
				}
			}
		})
		
		/*(0 until 300).foreach(i=>{
			val b=ByteBuffer.allocate(200)
			b.putInt(i)
			b.put(cc)
			val time=System.currentTimeMillis()
			//println(s"rtt :${i} ${time}")
			println(s"${time}")
			client.getSession().setAttribute(i,time)
			
			val bytes = b.array()
			client.write(ByteMessage(1,bytes))
			
			//println(s"send :${i}")
			Thread.sleep(20)
		})*/
		
	}
	
	def shutdown()={
		schedule.shutdown
	}
}
