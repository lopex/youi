package io.youi.server.session

import io.youi.Unique
import io.youi.http.cookie.ResponseCookie
import io.youi.http.{Connection, Headers, HttpConnection, HttpResponse}
import io.youi.net.Protocol

import scala.concurrent.Future
import scribe.Execution.global

/**
  * SessionManager must be implemented in order to have support for sessions
  *
  * @tparam Session the type of session
  */
trait SessionManager[Session] {
  /**
    * Functional use of a Session via a transaction that is fully managed with the result being updated to the manager
    *
    * @param connection the HttpConnection to work with
    * @param f the functionality to work with and potentially modify a session instance
    * @return Future[Unit] since Connection cannot modify the state of HttpConnection
    */
  def withConnection(connection: Connection)
                    (f: SessionTransaction[Session] => Future[SessionTransaction[Session]] = t => Future.successful(t)): Future[Session] = {
    val httpConnection = connection.store[HttpConnection]("httpConnection")
    session(httpConnection, f, requestModifiable = false).map(_.session)
  }

  /**
    * Functional use of a Session via a transaction that is fully managed with the result being updated to the manager
    *
    * @param connection the HttpConnection to work with
    * @param f the functionality to work with and potentially modify a session instance
    * @return potentially modified HttpConnection
    */
  def withHttpConnection(connection: HttpConnection)
                        (f: SessionTransaction[Session] => Future[SessionTransaction[Session]] = t => Future.successful(t)): Future[SessionTransaction[Session]] = {
    session(connection, f, requestModifiable = true).map(_.copy(sessionModifiable = false))
  }

  /**
    * Manages the entire transaction process of working with a session
    *
    * @param connection the HttpConnection to work with
    * @param f the transaction handling function to apply
    * @param requestModifiable true if the request can be modified
    * @return the final, modified HttpConnection
    */
  protected def session(connection: HttpConnection,
                        f: SessionTransaction[Session] => Future[SessionTransaction[Session]],
                        requestModifiable: Boolean): Future[SessionTransaction[Session]] = {
    getOrCreateSessionId(connection) match {
      case (modifiedConnection, sessionId) => {
        // Create our SessionTransaction and build our session
        val transactionFuture = loadBySessionId(sessionId, modifiedConnection).flatMap {
          case Some(transaction) => Future.successful(transaction)
          case None => createBySessionId(sessionId, modifiedConnection)
        }.map(_.copy(requestModifiable = requestModifiable))
        // Apply the transaction to the supplied function
        val appliedTransaction = transactionFuture.flatMap(f)
        // Save the changes to the transaction
        appliedTransaction.flatMap(save)
      }
    }
  }

  /**
    * Loads a session by id if available and creates a SessionTransaction wrapper
    *
    * @param sessionId the session id to load from
    * @param connection the HttpConnection to work with
    * @return a future SessionTransaction[Session] if one is persisted for this manager
    */
  protected def loadBySessionId(sessionId: String,
                                connection: HttpConnection): Future[Option[SessionTransaction[Session]]]

  /**
    * Creates a new session by session id
    *
    * @param sessionId the session id to create from
    * @param connection the HttpConnection to work with
    * @return a future SessionTransaction[Session]
    */
  protected def createBySessionId(sessionId: String,
                                  connection: HttpConnection): Future[SessionTransaction[Session]] = {
    Future.successful(SessionTransaction[Session](sessionId, create(sessionId), connection))
  }

  /**
    * Simple create function used by createBySessionId. For more advanced usage, extend createBySessionId.
    *
    * @param sessionId the session id to create a Session for
    */
  protected def create(sessionId: String): Session

  /**
    * Saves a potentially modified Session to this manager
    *
    * @param transaction the transaction to persist from
    * @return a potentially modified HttpConnection
    */
  protected def save(transaction: SessionTransaction[Session]): Future[SessionTransaction[Session]]

  /**
    * Gets a session id if it already exists or creates a new one (and applies it on the HttpConnection) if it doesn't
    * already exist.
    *
    * @param connection the HttpConnection to use
    * @return a tuple with the potentially modified HttpConnection and the session id
    */
  protected def getOrCreateSessionId(connection: HttpConnection): (HttpConnection, String) = sessionId(connection) match {
    case Some(id) => connection -> id
    case None => {
      val id = generateSessionId
      applySessionId(id, connection) -> id
    }
  }

  /**
    * Generates a unique id for use when creating a new session
    */
  protected def generateSessionId: String = Unique()

  /**
    * Retrieves the session id from the request / response cookies if available.
    *
    * @param connection the HttpConnection to look in
    * @return the session id if found
    */
  protected def sessionId(connection: HttpConnection): Option[String] = {
    val config = connection.server.config.session
    connection.request.cookies.find(_.name == config.name()).map(_.value) match {
      case Some(id) => Some(id)
      case None => connection.response.cookies.find(_.name == config.name()).map(_.value) match {
        case Some(id) => Some(id)
        case None => None
      }
    }
  }

  /**
    * Applies a new session id to an HttpConnection. Creates a cookie and sets it on the HttpResponse.
    *
    * @param id the session id to apply
    * @param connection the HttpConnection to use
    * @return modified HttpConnection
    */
  protected def applySessionId(id: String, connection: HttpConnection): HttpConnection = {
    val config = connection.server.config.session
    if (config.secure() && !config.forceSecure() && connection.request.url.protocol != Protocol.Https) {
      connection        // Don't set cookie if secure cookie is required and non-HTTPs
    } else {
      connection.modify { response =>
        val cookie = ResponseCookie(
          name = config.name(),
          value = id,
          maxAge = if (config.maxAge() == 0L) None else Some(config.maxAge()),
          domain = config.domain(),
          path = config.path(),
          secure = config.secure(),
          httpOnly = config.httpOnly(),
          sameSite = config.sameSite()
        )
        response.withHeader(Headers.Response.`Set-Cookie`(cookie))
      }
    }
  }
}