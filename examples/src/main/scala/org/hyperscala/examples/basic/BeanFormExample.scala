package org.hyperscala.examples.basic

import org.hyperscala.web.{FormSupport, ActionForm, AJAXForm}
import org.hyperscala.html._
import org.powerscala.property._
import org.hyperscala.bean.BeanForm
import org.powerscala.property.event.PropertyChangeEvent
import tag.{Br, Script, Div}
import org.hyperscala.web.site.Webpage

/**
 * @author Matt Hicks <mhicks@powerscala.org>
 */
class BeanFormExample extends Webpage with FormSupport {
  title := "BeanForm Example"

  head.contents += new Script(src = "/js/jquery-1.7.2.js")

  val messages = new Div {
    style.padding.bottom := 10.px
  }
  body.contents += messages

  val form = new BeanForm[Person]("person", Person("John Doe", "john@doe.com", "Some", "Where")) with AJAXForm with ActionForm {
    property.listeners.synchronous {
      case evt: PropertyChangeEvent => {
        messages.contents += "Person changed from %s to %s".format(evt.oldValue, evt.newValue)
        messages.contents += new Br
      }
    }
  }
  body.contents += form
}

case class Person(name: String = "", email: String = "", city: String = "", state: String = "")