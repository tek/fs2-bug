package fs2bug

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

import cats.effect.{ContextShift, IO, Timer}
import cats.implicits._
import fs2.{Chunk, Pull, Stream}
import utest._

object StreamProgress
{
  def logMessage(prefix: String)(totalBytes: Long): Long => String = {
      val totalSize = totalBytes / 1000000L
      progressBytes =>
        val progress = progressBytes / 1000000L
        val percent = Either.catchNonFatal(100 * progress / totalSize).getOrElse("???")
        s"$prefix: $progress/$totalSize M [$percent%]"
  }

  def logProgress
  (msg: Long => String)
  (stepSize: Long, progress: Long, lastLog: Long, chunkSize: Long)
  : IO[Long] = {
    val newProgress = progress + chunkSize
    val nextStep = lastLog + stepSize
    if (newProgress > nextStep) IO(println(msg(newProgress))).as(nextStep)
    else IO.pure(lastLog)
  }

  def logged
  (prefix: String)
  (stepSize: Long, totalSize: Long)
  : Stream[IO, Byte] => Pull[IO, Byte, Unit] = {
    val msg: Long => String = logMessage(prefix)(totalSize)
    def spin(bytes: Stream[IO, Byte])(progress: Long, lastLog: Long): Pull[IO, Byte, Unit] =
      bytes.pull.uncons.flatMap {
        case Some((chunk, tail)) =>
          for {
            _ <- Pull.output(chunk)
            newLastLog <- Pull.eval(logProgress(msg)(stepSize, progress, lastLog, chunk.size))
            _ <- spin(tail)(progress + chunk.size, newLastLog)
          } yield ()
        case None => Pull.done
      }
    spin(_)(0, 0)
  }

  def apply
  (prefix: String)
  (stepSize: Long, totalSize: Long)
  : Stream[IO, Byte] => Stream[IO, Byte] =
    _.through(logged(prefix)(stepSize, totalSize)(_).stream)
}

object Resources
{
  implicit def ec: ExecutionContext =
    ExecutionContext.global

  implicit def cs: ContextShift[IO] =
    IO.contextShift(ec)

  implicit def timer: Timer[IO] =
    IO.timer(ec)
}

object BugTest
extends TestSuite
{
  import Resources.{cs, timer}

  def tests: Tests =
    Tests {
      "bug" - {
        (Stream.awakeEvery[IO](1.second) *> Stream.eval(IO(println(1)))).compile.drain.start.unsafeRunSync
        val byteChunk = Chunk.bytes(Array.fill(10000)('a'))
        Stream.unfoldChunk(0)(i => if (i == 150000) None else Some((byteChunk, i + 1)))
          .covary[IO]
          .through(StreamProgress("serve")(150000000, 1500000000))
          .compile
          .to[Array]
          .void
          .unsafeRunSync
      }
    }
}
