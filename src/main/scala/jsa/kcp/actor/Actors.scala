package jsa.kcp.actor

import java.util.concurrent.Executors

import jsa.core.actor.JsaActor

/**
  * 注意 区分 函数和方法的区别
  *
  * //下面的定义是错的，原因是：函数的返回类型（:Option[JsaActor]）应该由 => 后面的来决定，在前面声明错误
  * //def actors4=(event:Event[_]):Option[JsaActor]=>Some(new JsaActor(Executors.newSingleThreadExecutor()))
  *
  * Don't say anything,just do it
  * Created by RTunSole on 2016/9/12.
  */
object Actors {
	val executor = Executors.newFixedThreadPool(4) //SingleThreadExecutor()
	//private val actors = Array((0 until 16).map(_=>new JsaActor(executor)):_*)
	private val actors = (0 until 16).map(_=>new JsaActor(executor))
	
	/*private val actors = new Array[JsaActor](16)
	(0 until actors.length).foreach(i => {
		actors(i) = new JsaActor(executor)
	})*/
	
	def get(index: Int): JsaActor = {
		actors(Math.abs(index % actors.length))
	}
	
	def shutdown(): Unit ={
		executor.shutdown()
	}
	
	def main(args: Array[String]): Unit = {
		val cc=55555555555L
		println("length:"+cc.toInt% 16)
		val actors = (0 until 16).map(_=>new JsaActor(executor))
		println("length:"+actors.length)
		(0 until actors.length).foreach(i => {
			//actors(i) = new JsaActor(executor)
			println("count:"+actors(i))
		})
	}
}
