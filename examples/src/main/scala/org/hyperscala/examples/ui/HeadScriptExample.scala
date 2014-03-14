package org.hyperscala.examples.ui

import org.hyperscala.html._
import org.hyperscala.examples.Example
import org.hyperscala.jquery.Gritter
import org.hyperscala.realtime._
import org.hyperscala.ui.module.HeadScript

/**
 * @author Matt Hicks <matt@outr.com>
 */
class HeadScriptExample extends Example {
  page.require(Gritter)
  page.require(HeadScript)

  contents += new tag.P {
    contents += "Notice that by simply requiring 'HeadScript' on the page, all inlined JavaScript has been migrated into the head of the document and uses jQuery binding instead of inline calls."
  }

  contents += new tag.Button(content = "Click me!") {
    clickEvent.onRealtime {
      case evt => Gritter.add("Clicked", "The button was clicked!")
    }
  }
}