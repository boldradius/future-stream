package com.boldradius.futurestream

import java.util.concurrent.{Executors, ExecutorService, ScheduledExecutorService, TimeUnit}

import org.scalatest._

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

class FutureStreamSpec extends FlatSpec with Matchers {
  def stream = Stream.iterate(0)(_ + 1).take(1000)
  def fsp = FutureStream.fromStream(stream).flatMapValues(n => Empty(Timer.in(50).map(_ => Element(n, End(())))))
  def fs = fsp.fold(0)(_ + _, (b, _) => b)

  //TODO: test that memory consumption is small if we don't hold on to the whole streams
  "FutureStream" should "allow thousands of concurrent instances" in {
    val reps = 1 to 10000
    //val fss = reps.map(_ => fsp.toList)
    //Await.result(Future.sequence(fss), Inf) should === (reps.map(_ => stream))
    val fss = reps.map(_ => fs.end)
    println(Await.result(Future.sequence(fss), Inf))
  }
}
