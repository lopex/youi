package io.youi.example

import io.youi.communication.Communication
import io.youi.net.URL
import io.youi.server.WebSocketClient
import io.youi.util.Time

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

object ReverseClientExample {
  val connection: WebSocketClient = new WebSocketClient(URL("http://localhost:8080/communication"))
  val simple: ClientSimpleCommunication = Communication.create[ClientSimpleCommunication](connection)

  def mainDisabled(args: Array[String]): Unit = {
    connection.connect()
    try {
      val future = simple.reverse("This is a test!")
      val result = Await.result(future, 5.seconds)
      scribe.info(s"Receive: $result")
    } finally {
      connection.dispose()
    }
//    reRun()

    Thread.sleep(120000)
  }

  private def reRun(): Future[Unit] = simple.reverse("This is a test!").flatMap { result =>
    scribe.info(s"Receive: $result")
    Time.delay(2.seconds).flatMap(_ => reRun())
  }
}