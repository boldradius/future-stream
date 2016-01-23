package com.boldradius.futurestream

import java.util.concurrent.{Executors, ExecutorService, ScheduledExecutorService, TimeUnit}

import org.scalatest._

import scala.concurrent.duration.Duration
import scala.concurrent.{Promise, Future, Await}
import scala.concurrent.duration.Duration.Inf
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Success

object Timer {
  val es = Executors.newScheduledThreadPool(1)
  def in(millis: Int) : Future[Unit] = {
    val p = Promise[Unit]
    es.schedule(new Runnable {
      override def run(): Unit = p.tryComplete(Success(()))
    }, millis, TimeUnit.MILLISECONDS)
    p.future
  }
}

object PrintTime {
  def apply[T](msg: Double => String)(t: => T): T = {
    val start = System.nanoTime()
    val v = t
    println(msg((System.nanoTime() - start).toDouble / 1e9))
    v
  }
}

class FutureStreamSpec extends FlatSpec with Matchers {
  def increasing(len: Int) = FutureStream.fromSeq(Stream.iterate(0)(_ + 1).take(len))
  def foldRate(numStreams: Int, periodMs: Int, length: Int) : Unit = {
    def fsp = increasing(length).flatMapValues(n => Empty(Timer.in(periodMs).map(_ => Element(n, End(())))))
    def fs = fsp.fold(0)(_ + _, (b, _) => b)
    PrintTime(t => f"FutureStream fold rate: ${numStreams.toDouble*length/t}%f, time: $t%f") {
      val fss = (1 to numStreams).map(_ => fs.end)
      Await.result(Future.sequence(fss), Inf)
    }
  }

  def streamFoldRate(numStreams: Int, periodMs: Int, length: Int) : Unit = {
    def fsp = increasing(length).flatMapValues(n => new Pause({Thread.sleep(periodMs); Element(n, End(()))}))
    def fs = fsp.fold(0)(_ + _, (b, _) => b)
    val reps = 1 to numStreams
    PrintTime(t => f"Stream fold rate: ${numStreams.toDouble*length/t}%f, time: $t%f") {
      val fss = reps.map { _ =>
        val t = new Thread(new Runnable {
          override def run(): Unit =
            Await.result(fs.end, Inf) // fs.end should return a Future.successful in this case
        })
        t.start()
        t
      }
      fss.foreach(_.join())
    }
  }


  //TODO: test that memory consumption is small if we don't hold on to the whole streams
  "FutureStream" should "allow thousands of concurrent instances" in {
      foldRate(numStreams = 40000, periodMs = 1, length = 200)
  }

  "Stream" should "allow thousands of concurrent instances" in {
     streamFoldRate(numStreams = 1900, periodMs = 1, length = 1000)  // Desktop version of OSX limited to 2048 task threads (sysctl kern.num_taskthreads)
  }

}


