package io.youi.example.screen

import io.youi.dom._
import io.youi.example.ClientExampleApplication._
import io.youi.net._
import org.scalajs.dom.{Event, html, window}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object CommunicationScreen extends ExampleScreen {
  def connectedInput: html.Input = content.byId[html.Input]("communicationConnected")
  def connectedButton: html.Button = content.byId[html.Button]("communicationConnectButton")
  def reverseInput: html.Input = content.byId[html.Input]("communicationReverse")
  def reverseButton: html.Button = content.byId[html.Button]("communicationReverseButton")
  def timeInput: html.Input = content.byId[html.Input]("communicationTime")
  def timeButton: html.Button = content.byId[html.Button]("communicationTimeButton")
  def nameInput: html.Input = content.byId[html.Input]("communicationName")
  def nameButton: html.Button = content.byId[html.Button]("communicationNameButton")
  def counterInput: html.Input = content.byId[html.Input]("communicationCounter")
  def counterButton: html.Button = content.byId[html.Button]("communicationCounterButton")
  def broadcastInput: html.Input = content.byId[html.Input]("communicationBroadcast")
  def broadcastButton: html.Button = content.byId[html.Button]("communicationBroadcastButton")

  override def path: Path = path"/communication.html"

  override protected def load(): Future[Unit] = super.load().map { _ =>
    configure()
  }

  private def configure(): Unit = {
    cc.connection.connected.attachAndFire { c =>
      connectedInput.value = if (c) "Yes" else "No"
    }
    connectedButton.addEventListener("click", (evt: Event) => {
      evt.preventDefault()
      evt.stopPropagation()
      if (cc.connection.connected()) {
        cc.disconnect()
      } else {
        cc.connect()
      }
    })

    reverseButton.addEventListener("click", (evt: Event) => {
      evt.preventDefault()
      evt.stopPropagation()
      val value = reverseInput.value.trim
      if (value.isEmpty) {
        window.alert("Reverse value must not be empty!")
      } else {
        s.reverse(value).foreach { reversed =>
          reverseInput.value = reversed
        }
      }
    })

    timeButton.addEventListener("click", (evt: Event) => {
      evt.preventDefault()
      evt.stopPropagation()
      c.time.foreach { time =>
        timeInput.value = time.toString
      }
    })

    nameInput.value = c.name().getOrElse("")
    nameButton.addEventListener("click", (evt: Event) => {
      evt.preventDefault()
      evt.stopPropagation()
      c.name := Some(nameInput.value)
    })
    c.name.attach { name =>
      val s = name.getOrElse("")
      if (nameInput.value != s) {
        nameInput.value = s
      }
    }

    counterButton.addEventListener("click", (evt: Event) => {
      evt.preventDefault()
      evt.stopPropagation()
      c.counter.foreach { count =>
        counterInput.value = count.toString
      }
    })

    broadcastButton.addEventListener("click", (evt: Event) => {
      evt.preventDefault()
      evt.stopPropagation()
      c.broadcast(broadcastInput.value)
    })
  }
}
