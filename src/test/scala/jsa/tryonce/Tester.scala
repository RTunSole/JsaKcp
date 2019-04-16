package jsa.tryonce

import java.nio.{ByteBuffer, ByteOrder}
import java.util

import scala.collection.mutable.ListBuffer

/**
  * Don't say anything,just do it !
  * Created by RTunSole on 2018/9/30
  */
object Tester extends App {
	testByteBuffer()

    def testListBuffer()={
        val lb=new ListBuffer[Int]()
		
        lb +=10
        lb +=15
        lb +=20

        println(lb.head)

        val javaLB=new util.LinkedList[Int]()

        javaLB.add(10)
        javaLB.add(15)
        javaLB.add(20)

        println(javaLB.getFirst)
    }
	def testByteBuffer()={
		val aa="abc".getBytes
		val bb="def".getBytes
		val c=aa++bb
		println(new String(c))
		
		val buff=ByteBuffer.allocate(100).order(ByteOrder.LITTLE_ENDIAN)
		
		buff.putInt(10)
		buff.putShort(5)
		
		buff.position(4)
		buff.limit(6)
		println(s"remaining ${buff.remaining()} ${buff.slice().get()}========")
		println(s"${buff.getShort()} ${buff.remaining()} ========")
		buff.flip()
		//buff.putShort(15)
		//buff.rewind()
		
		println(s"${buff.getInt} ${buff.getShort} ${buff.remaining()} ========")
		//buff.flip()
  
		val data=buff.array()
        val test=ByteBuffer.allocate(100).order(ByteOrder.LITTLE_ENDIAN)
		test.put(data)
		test.flip()
        println(s"${test.getInt} ${test.getShort} ${test.remaining()}")
	}

	def testEither()={
		case class Data(id:Int,name:String)
		
		def get(id:Int):Either[Data,Data]={
			if(id >0) Right(Data(1,"tome"))
			else Left(Data(2,"tony"))
		}
		
		def check(data:Data)={
			println(data.name)
			data
		}

		def check2(data:Data)={
			println(data.id)
		}

		val result=get(1)
    		.right.map(check)
    		.fold(check2,check2)

		println(result)
		//List(1,2,3)
	}
}
