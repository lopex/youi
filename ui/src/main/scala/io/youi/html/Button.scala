package io.youi.html

import com.outr.reactify.{Channel, Var}
import io.youi.dom
import org.scalajs.dom.Event
import org.scalajs.dom.html.{Button => HTMLButton}

class Button extends Component {
  override protected[youi] val element: HTMLButton = dom.create[HTMLButton]("button")

  lazy val text: Var[String] = prop(element.textContent, value => element.textContent = value, mayCauseResize = true)

  lazy val click: Channel[Event] = events("click", stopPropagation = false)

  init()
}