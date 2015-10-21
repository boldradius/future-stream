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
  def stream = Stream.iterate(0)(_ + 1).take(200)
  def fsp = FutureStream.fromSeq(stream).flatMapValues(n => Empty(Timer.in(50).map(_ => Element(n, End(())))))
  def fs = fsp.fold(0)(_ + _, (b, _) => b)

  //TODO: test that memory consumption is small if we don't hold on to the whole streams
  "FutureStream" should "allow thousands of concurrent instances" in {
    val reps = 1 to 20000
    //val fss = reps.map(_ => fsp.toList)
    //Await.result(Future.sequence(fss), Inf) should === (reps.map(_ => stream))
    PrintTime("FutureStream time: " + _) {
      val fss = reps.map(_ => fs.end)
      println(Await.result(Future.sequence(fss), Inf))
    }
  }
}


class StreamSpec extends FlatSpec with Matchers {
  def stream = Stream.iterate(0)(_ + 1).take(200)
  def fsp = FutureStream.fromSeq(stream).flatMapValues(n => new Pause({Thread.sleep(50); Element(n, End(()))}))
  def fs = fsp.fold(0)(_ + _, (b, _) => b)

  "Stream" should "allow thousands of concurrent instances" in {
    val reps = 1 to 1900   // Desktop version of OSX limited to 2048 task threads (sysctl kern.num_taskthreads)
    PrintTime("Stream time: " + _) {
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
}

