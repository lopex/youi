package io.youi.example.ui

import io.youi.app.screen.{PathActivation, Screen}
import io.youi.component._
import io.youi.image.Image
import io.youi.style.Position
import io.youi.task._
import io.youi.ui

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

object ImageExample extends Screen with PathActivation {
  override protected def title: String = "Image Example"
  override def path: String = "/examples/image.html"

  override protected def init(): Future[Unit] = Image("/images/icon.png").map { img =>
    ui.children += new ImageView {                       // Top-Left
      image := img
      position.`type` := Position.Absolute
      position.left := 50.0
      position.top := 50.0
      size.width := 100.0
      size.height := 100.0
    }
    ui.children += new ImageView {                       // Top-Right
      image := img
      position.`type` := Position.Absolute
      position.right := ui.size.width - 50.0
      position.top := 50.0
      opacity := 0.5
    }
    ui.children += new ImageView {                       // Bottom-Left
      image := img
      position.`type` := Position.Absolute
      position.left := 50.0
      position.bottom := ui.size.height - 50.0
    }
    ui.children += new ImageView {                       // Bottom-Right
      image := img
      position.`type` := Position.Absolute
      position.right := ui.size.width - 50.0
      position.bottom := ui.size.height - 50.0
      size.width := 300.0
      size.height := 300.0
    }
    ui.children += new ImageView {                       // Center
      image := img
      position.`type` := Position.Absolute
      position.center := ui.position.center
      position.middle := ui.position.middle
      forever {
        rotation to 1.0 in 1.seconds andThen(rotation := 0.0)
      }.start(this)
    }
  }
}