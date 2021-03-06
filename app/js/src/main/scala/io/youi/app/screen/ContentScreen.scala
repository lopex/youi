package io.youi.app.screen

import reactify.{Val, Var}
import io.youi.{History, dom}
import io.youi.dom._
import io.youi.net.Parameters
import io.youi.stream.StreamURL
import org.scalajs.dom._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

trait ContentScreen extends Screen with PathActivation {
  private def pageTag: html.Element = dom.byTag[html.Element]("page").head

  private val contentOptionVar = Var[Option[html.Span]](None)
  protected val contentOption: Val[Option[html.Span]] = Val(contentOptionVar)
  protected def content: html.Span = contentOption().getOrElse(throw new RuntimeException("Content not set!"))

  override protected def load(): Future[Unit] = super.load().flatMap { _ =>
    // On load check to see if the screen tag is loaded already
    dom.byTag[html.Element]("screen").headOption.foreach { screen =>
      loadScreen(screen)
    }

    if (contentOption.isEmpty) {      // Content hasn't been loaded yet
      val url = History
        .url()
        .copy(path = path, parameters = Parameters.empty)
        .withParam("part", "true")
        .withParam("selector", "screen")
      scribe.info(s"Loading content $url...")
      StreamURL.stream(url).map { htmlString =>
        scribe.info(s"Content loaded successfully (${htmlString.length} characters)")
        val screen = dom.fromString[html.Element](htmlString).headOption.getOrElse(throw new RuntimeException(s"No content found in: [$htmlString] for URL: $url"))
        loadScreen(screen)
      }
    } else {                          // Content has already been loaded either by page load or by previous load
      scribe.info("Content is already loaded!")
      Future.successful(())
    }
  }

  private def loadScreen(screen: html.Element): Unit = {
    val span = dom.create[html.Span]("span")
    screen.remove()
    val size = screen.childNodes.length
    (0 until size).map(screen.childNodes.item).foreach { child =>
      child match {
        case e: Element => e.remove()
        case _ => // Ignore nodes
      }
      span.appendChild(child)
    }
    pageTag.appendChild(span)
    contentOptionVar := Some(span)
  }

  protected def showContent(): Unit = contentOption.foreach { c =>
    if (Option(c.parentElement).isEmpty) {
      pageTag.appendChild(c)
    }
  }
  protected def hideContent(): Unit = contentOption.foreach { c =>
    if (Option(c.parentElement).nonEmpty) {
      c.remove()
    }
  }

  override protected def activate(): Future[Unit] = super.activate().map { _ =>
    showContent()
  }

  override protected def deactivate(): Future[Unit] = super.deactivate().map { _ =>
    hideContent()
  }
}