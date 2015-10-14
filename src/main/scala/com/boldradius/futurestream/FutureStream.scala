package com.boldradius.futurestream

import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future}

//TODO: fold, producing a stream
sealed trait FutureStream[+A, +E] {
  def ++[B >: A, F](b: FutureStream[B, F])(implicit ec: ExecutionContext) : FutureStream[B, F] = flatMap(_ => b)
  def +:[B >: A](b: B) : FutureStream[B, E] = Element(b, this)

  def fold[B, F](b: B)(f: (B, A) => B, g: (B, E) => F)(implicit ec: ExecutionContext) : FutureStream[B, F] =
    this match {
      case p: Pause[A, E] => new Pause(p.resume.fold(b)(f, g))
      case Empty(fs) => Empty(fs.map(_.fold(b)(f, g)))
      case Element(h, t) =>
        val b2 = f(b, h)
        Element(b2, t.fold(b2)(f, g))
      case End(e) => End(g(b, e))
    }

  def mapValues[B](f: A => B)(implicit ec: ExecutionContext) : FutureStream[B, E] =
    this match {
      case p: Pause[A, E] => new Pause(p.resume.mapValues(f))
      case Empty(fs) => Empty(fs.map(_.mapValues(f)))
      case Element(h, t) => Element(f(h), t.mapValues(f))
      case e: End[E] => e
    }

  def flatMapValues[B,F](f: A => FutureStream[B, F])(implicit ec: ExecutionContext) : FutureStream[B, E] =
    flatMapCutK(f, identity, (_: F) => None)

  def flatMapWhileCutK[B >: A, F, G >: E](f: A => FutureStream[B, F], fg: F => Either[Boolean, G])(implicit ec: ExecutionContext) : FutureStream[B, G] = // TODO create enum
    this match {
      case p: Pause[A, E] => new Pause(p.resume.flatMapWhileCutK(f, fg))
      case Empty(fs) => Empty(fs.map(_.flatMapWhileCutK(f, fg)))
      case Element(h, t) => f(h).flatMap(fg(_).fold(cont => if (cont) t.flatMapWhileCutK(f, fg) else t, End(_)))
      case End(e) => End(e)
    }

  def flatMapCut[B, G >: E](f: A => FutureStream[B, Option[G]])(implicit ec: ExecutionContext) : FutureStream[B, G] =
    flatMapCutK(f, (e: E) => e, (og : Option[G]) => og)

  def flatMapCutK[B,F,G](f: A => FutureStream[B, F], eg: E => G, fg: F => Option[G])(implicit ec: ExecutionContext) : FutureStream[B, G] =
    this match {
      case p: Pause[A, E] => new Pause(p.resume.flatMapCutK(f, eg, fg))
      case Empty(fs) => Empty(fs.map(_.flatMapCutK(f, eg, fg)))
      case Element(h, t) => f(h).flatMap(fg(_).fold(t.flatMapCutK(f, eg, fg))(End(_)))
      case End(e) => End(eg(e))
    }

  def map[F](f: E => F)(implicit ec: ExecutionContext) : FutureStream[A, F] =
    this match {
      case p: Pause[A, E] => new Pause(p.resume.map(f))
      case Empty(fs) => Empty(fs.map(_.map(f)))
      case Element(h, t) => Element(h, t.map(f))
      case End(e) => End(f(e))
    }

  def flatMap[B >: A, F](f: E => FutureStream[B, F])(implicit ec: ExecutionContext) : FutureStream[B, F] =
    this match {
      case p: Pause[A, E] => new Pause(p.resume.flatMap(f))
      case Empty(fs) => Empty(fs.map(_.flatMap(f)))
      case Element(h, t) => Element(h, t.flatMap(f))
      case End(e) => f(e)
    }

  @tailrec final def drop(n : Int)(implicit ec: ExecutionContext) : FutureStream[A, E] =
    if (n <= 0) this
    else this match {
      case p: Pause[A, E] => new Pause(p.resume.drop2(n))
      case Empty(fs) => Empty(fs.map(_.drop2(n)))
      case Element(_, t) => t.drop(n - 1)
      case e: End[E] => e
    }
  private def drop2(n : Int)(implicit ec: ExecutionContext) : FutureStream[A, E] = drop(n) // For @tailrec drop() above

  @tailrec private def toRevList[B >: A](acc: List[B])(implicit ec: ExecutionContext): Future[List[B]] =
    this match {
      case p: Pause[A, E] => p.resume.toRevList(acc)
      case Empty(fs) => fs.flatMap(_.toRevList2(acc))
      case Element(h, t) => t.toRevList(h :: acc)
      case _: End[E] => Future.successful(acc)
    }
  private def toRevList2[B >: A](acc: List[B])(implicit ec: ExecutionContext): Future[List[B]] = toRevList(acc) // For @tailrec toRevList() above

  final def toList(implicit ec: ExecutionContext): Future[List[A]] =
    toRevList(Nil).map(_.reverse)

  @tailrec final def end(implicit ec: ExecutionContext): Future[E] =
    this match {
      case p: Pause[A, E] => p.resume.end
      case Empty(fs) => fs.flatMap(_.end2)
      case Element(h, t) => t.end
      case End(e) => Future.successful(e)
    }
  private def end2(implicit ec: ExecutionContext): Future[E] = end  // For @tailrec end() above


  final def take(n : Int)(implicit ec: ExecutionContext) : FutureStream[A, FutureStream[A, E]] =
    if (n <= 0) End(this)
    else this match {
      case p: Pause[A, E] => new Pause(p.resume.take(n))
      case Empty(fs) => Empty(fs.map(_.take(n)))
      case Element(h, t) => Element(h, t.take(n - 1))
      case e: End[E] => End(e)
    }

  type EitherEnd[+A, +E, +B, +F] = Either[(E, FutureStream[B, F]), (FutureStream[A, E], F)]

  /**
   * Merges two streams. Priority is given to the left stream if the resulting string is not consumed
   * fast enough to absorb the data produced by both. The stream ends with
   * all residual information as soon as either input stream ends.
   */
  def mergeLow[B, F](bs: FutureStream[B, F])(implicit ec: ExecutionContext) : FutureStream[Either[A, B], EitherEnd[A, E, B, F]] =
    this match {
      case pa: Pause[A, E] => new Pause(pa.resume mergeLow bs)
      case Empty(fas) => bs match {
        case pb: Pause[B, F] => new Pause(this mergeLow pb.resume)
        case Empty(fbs) =>
          Empty(Future.firstCompletedOf(List(fas.map(_ mergeLow bs), fbs.map(this mergeLow _)))) // TODO optimize
        case Element(b, t) => Element(Right(b), this mergeLow t)
        case End(f) => End(Right((this, f)))
      }
      case Element(a, t) => Element(Left(a), t mergeLow bs)
      case End(e) => End(Left((e, bs)))
    }
  /**
   * Merges two streams by pairing the corresponding elements of each. The stream ends with
   * all residual information as soon as either input stream ends.
   */
  def zip[B, F](bs: FutureStream[B, F])(implicit ec: ExecutionContext) : FutureStream[(A, B), EitherEnd[A, E, B, F]] =
    this match {
      case pa: Pause[A, E] => new Pause(pa.resume zip bs)
      case Empty(fas) => Empty(fas.map(_.zip(bs)))
      case Element(a, ta) => bs match {
        case pb: Pause[B, F] => new Pause(this zip pb.resume)
        case Empty(fbs) => Empty(fbs.map(this.zip(_)))
        case Element(b, tb) => Element((a, b), ta zip tb)
        case End(f) => End(Right((this, f)))
      }
      case End(e) => End(Left((e, bs)))
    }

  // TODO: Problem, may we should not have ++ on two FutureStreams, but this ++: instead
  def ++:[B >: A](s: Seq[B]) : FutureStream[B, E] =
    if (s.isEmpty) this
    else s.head +: new Pause(++:(s.tail))

  def prependStream[B >: A](s: Stream[B])(implicit ec: ExecutionContext) : FutureStream[B, E] =
    if (s.isEmpty) this
    else s.head +: new Pause(Empty(Future(prependStream(s.tail))))


}

object FutureStream {
  def empty = end(())
  def end[E](e: E) = End(e)
  def fromSeq[A, E](l: Seq[A], e: E = Unit): FutureStream[A, E] = l ++: End(e)
  /** If your stream does not block, prefer fromSeq which will be more efficient.*/
  def fromStream[A, E](s: Stream[A], e: E = Unit)(implicit ec: ExecutionContext): FutureStream[A, E] =
    end(e).prependStream(s)
}

final case class End[+E](e: E) extends FutureStream[Nothing, E]
final case class Element[+A, +E](head: A, tail: FutureStream[A, E]) extends FutureStream[A, E]
final case class Empty[+A, +E](v: Future[FutureStream[A, E]]) extends FutureStream[A, E]
final class Pause[+A, +E](expr: => FutureStream[A, E]) extends FutureStream[A, E] {
  lazy val resume = expr
}
