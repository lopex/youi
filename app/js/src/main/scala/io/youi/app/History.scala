package io.youi.app

import com.outr.reactify.{Channel, Val, Var}
import io.youi.net.URL
import org.scalajs.dom._

import scala.scalajs.js

/**
  * Convenience functionality for working with browser history.
  */
object History {
  private val currentURL = Var[URL](URL(document.location.href))
  val url: Val[URL] = Val(currentURL)
  val stateChange: Channel[HistoryStateChange] = Channel[HistoryStateChange]

  window.addEventListener("popstate", (evt: PopStateEvent) => {
    val urlString = document.location.href
    val newURL = URL(urlString)
    if (newURL != url()) {
      currentURL := newURL
      stateChange := HistoryStateChange(newURL, evt.state, StateType.Pop)
    }
  })

  def set(path: String): Unit = set(url.withPath(path))

  def set(url: URL): Unit = {
    document.location.href = url.toString
  }

  def push(path: String, state: js.Any = null): Unit = push(url.withPath(path), state)

  def push(url: URL, state: js.Any = null): Unit = {
    val urlString = url.toString
    window.history.pushState(state, urlString, urlString)
    currentURL := url
    stateChange := HistoryStateChange(url, state, StateType.Push)
  }

  def replace(path: String, state: js.Any = null): Unit = replace(url.withPath(path), state)

  def replace(url: URL, state: js.Any): Unit = {
    val urlString = url.toString
    window.history.replaceState(state, urlString, urlString)
    currentURL := url
    stateChange := HistoryStateChange(url, state, StateType.Replace)
  }

  def back(delta: Int = 1): Unit = window.history.back(delta)

  def forward(delta: Int = 1): Unit = window.history.forward(delta)
}

case class HistoryStateChange(url: URL, state: js.Any, stateType: StateType)

sealed trait StateType

object StateType {
  case object Push extends StateType
  case object Replace extends StateType
  case object Pop extends StateType
}