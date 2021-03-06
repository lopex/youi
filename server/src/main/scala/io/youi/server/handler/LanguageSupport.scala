package io.youi.server.handler

import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.{Matcher, Pattern}

import io.youi.Priority
import io.youi.http.content.Content
import io.youi.http.cookie.ResponseCookie
import io.youi.http.{Headers, HttpConnection}
import io.youi.net.ContentType
import profig.{Profig, ProfigLookupPath}

import scala.concurrent.Future

/**
  * LanguageSupport adds simple multi-lingual support to HTML files
  *
  * Language files must be defined either in the resources path, or in the files path. They may include country for
  * greater control over the language information.  The files must be structured like the following:
  *  - language-LANGUAGE (ex. language-en)
  *  - language-LANGUAGE_COUNTRY (ex. language-en_US)
  *
  * The file type may be: json, properties, yml, yaml, hocon, xml, conf, or config.
  *
  * @param default the default locale to fall-back on
  */
class LanguageSupport(val default: Locale = Locale.ENGLISH) extends HttpHandler {
  private lazy val emptyConfig = new Profig(None)
  private val languageConfig = new ConcurrentHashMap[String, Profig]
  private val cookieName = "languageOverride"

  override def handle(connection: HttpConnection): Future[HttpConnection] = {
    val updated = connection
      .response
      .content
      .filter(_.contentType == ContentType.`text/html`)
      .map(_.asString)
      .map(translate(connection, _))
      .getOrElse(connection)
    Future.successful(updated)
  }

  def translate(connection: HttpConnection, html: String): HttpConnection = {
    var c = connection

    val request = connection.request
    val paramLocales = request.url.param("lang").map(parseLocale).toList
    val cookieLocales = request.cookies.find(_.name == cookieName).map(c => parseLocale(c.value)).toList
    val headerLocales = request.headers.get(Headers.Request.`Accept-Language`).flatMap(_.split(',')).map(parseLocale)
    val locales = (paramLocales ::: cookieLocales ::: headerLocales ::: List(default)).distinct

    // Param override - set a cookie to store it
    if (paramLocales.nonEmpty) {
      c = c.modify { response =>
        val localesString = paramLocales.map {
          case l if l.getCountry.nonEmpty => s"${l.getLanguage}-${l.getCountry}"
          case l => l.getLanguage
        }.mkString(", ")
        val OneYear = 60 * 60 * 24 * 365
        response.withHeader(Headers.Response.`Set-Cookie`(ResponseCookie(cookieName, localesString, maxAge = Some(OneYear))))
      }
    }

    val localeStrings = locales.map(_.toString)
    val existingConfig = localeStrings.flatMap { locale =>
      Option(languageConfig.get(locale))
    }.headOption

    val config = existingConfig match {
      case Some(cfg) => cfg
      case None => firstConfig(localeStrings)
    }

    val matcher = """[{]([a-zA-Z0-9._-]+?)[}]""".r
    val updatedHTML = matcher.replaceAllIn(html, m => {
      config(m.group(1)).opt[String] match {
        case Some(replacement) => Matcher.quoteReplacement(replacement)
        case None => s"{${m.group(1)}}"
      }
    })

    c.modify { response =>
      response.withContent(Content.string(updatedHTML, ContentType.`text/html`))
    }
  }

  /**
    * Clears the configuration cache so it will reload on the next request
    */
  def clear(): Unit = languageConfig.clear()

  override def priority: Priority = Priority.Fallback

  private def firstConfig(localeStrings: List[String]): Profig = if (localeStrings.isEmpty) {
    emptyConfig
  } else {
    val locale = localeStrings.head
    val lookupPaths = ProfigLookupPath.paths(List(s"language-$locale"))
    val config = new Profig(None)
    config.load(lookupPaths: _*)
    if (config.json.asObject.get.nonEmpty) {
      languageConfig.put(locale, config)
      config
    } else {
      firstConfig(localeStrings.tail)
    }
  }

  private def parseLocale(s: String): Locale = if (s.contains(';')) {
    parseLocale(s.substring(0, s.indexOf(';')))
  } else {
    val value = s.trim
    val split = value.indexOf('-') match {
      case -1 => value.indexOf('_')
      case i => i
    }
    if (split != -1) {
      val language = value.substring(0, split).toLowerCase
      val country = value.substring(split + 1).toUpperCase
      new Locale(language, country)
    } else {
      new Locale(value.toLowerCase)
    }
  }
}