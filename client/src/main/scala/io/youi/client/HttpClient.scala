package io.youi.client

import java.io.File
import java.net.URI

import io.circe.{Decoder, Encoder}
import io.circe.parser._
import io.circe.syntax._
import io.youi.http.{Content, FileContent, Headers, HttpRequest, HttpResponse, Method, Status, StringContent}
import io.youi.net.{ContentType, URL}
import org.apache.http.{HttpResponse => ApacheHttpResponse}
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase
import org.apache.http.concurrent.FutureCallback
import org.apache.http.impl.nio.client.{CloseableHttpAsyncClient, HttpAsyncClients}
import org.apache.http.nio.entity.{NFileEntity, NStringEntity}
import org.powerscala.io._

import scala.concurrent.{Future, Promise}
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Asynchronous HttpClient for simple request response support.
  *
  * Adds support for simple restful request/response JSON support.
  *
  * @param saveDirectory the directory to save response content of a non-textual type
  */
class HttpClient(saveDirectory: File = new File(System.getProperty("java.io.tmpdir"))) {
  private val asyncClient: CloseableHttpAsyncClient = {
    val client = HttpAsyncClients.createDefault()
    client.start()
    client
  }

  /**
    * Sends an HttpRequest and receives an asynchronous HttpResponse future.
    *
    * @param request the request to send
    * @return Future[HttpResponse]
    */
  def send(request: HttpRequest): Future[HttpResponse] = {
    val req: HttpEntityEnclosingRequestBase = new HttpEntityEnclosingRequestBase {
      setURI(new URI(request.url.toString))

      override def getMethod: String = request.method.value
    }
    request.headers.map.foreach {
      case (key, values) => values.foreach { value =>
        req.addHeader(key, value)
      }
    }
    def ct(contentType: ContentType) = org.apache.http.entity.ContentType.getByMimeType(contentType.mimeType)
    val content = request.content.map {
      case c: StringContent => new NStringEntity(c.value, ct(c.contentType))
      case c: FileContent => new NFileEntity(c.file, ct(c.contentType))
      case c => throw new RuntimeException(s"Unsupported request content: $c")
      // TODO: support form data
    }
    content.foreach(req.setEntity)
    val promise = Promise[HttpResponse]
    asyncClient.execute(req, new FutureCallback[ApacheHttpResponse] {
      override def cancelled(): Unit = promise.failure(new RuntimeException("Cancelled!"))

      override def failed(exc: Exception): Unit = promise.failure(exc)

      override def completed(result: ApacheHttpResponse): Unit = {
        var headers = Headers.empty
        result.getAllHeaders.foreach { header =>
          headers = headers.withHeader(header.getName, header.getValue)
        }
        val contentType = Headers.`Content-Type`.value(headers).getOrElse(ContentType.`application/octet-stream`)
        val contentLength = Headers.`Content-Length`.value(headers)
        val content: Option[Content] = if (contentLength.contains(0)) {
          None
        } else if (contentType.`type` == "text" || contentType.subType == "json") {
          val body = IO.stream(result.getEntity.getContent, new StringBuilder).toString
          Some(Content.string(body, contentType))
        } else {
          val file = File.createTempFile("youi", "client", saveDirectory)
          Some(Content.file(file, contentType))
        }

        promise.success(HttpResponse(
          Status(result.getStatusLine.getStatusCode, result.getStatusLine.getReasonPhrase),
          headers,
          content
        ))
      }
    })

    promise.future
  }

  /**
    * Default error handler for restful bad response statuses.
    *
    * @tparam Response the type of response
    * @return throws a RuntimeException when an error occurs
    */
  protected def defaultErrorHandler[Response]: HttpResponse => Response = (response: HttpResponse) => {
    throw new RuntimeException(s"Error from server: ${response.status.message} (${response.status.code})")
  }

  /**
    * Builds on the send method by supporting basic restful calls that take a case class as the request and returns a
    * case class as the response.
    *
    * @param url the URL of the endpoint
    * @param request the request instance
    * @param headers the headers if any to provide
    * @param errorHandler error handling support if the response status is not Success
    * @param encoder circe encoding of the Request
    * @param decoder circe decoding of the Response
    * @tparam Request the request type
    * @tparam Response the response type
    * @return Future[Response]
    */
  def restful[Request, Response](url: URL,
                                 request: Request,
                                 headers: Headers = Headers.empty,
                                 errorHandler: HttpResponse => Response = defaultErrorHandler[Response])
                                (implicit encoder: Encoder[Request], decoder: Decoder[Response]): Future[Response] = {
    val requestJson = request.asJson.spaces2
    send(HttpRequest(
      method = Method.Post,
      url = url,
      headers = headers,
      content = Some(StringContent(requestJson, ContentType.`application/json`))
    )).map { response =>
      val responseJson = response.content.getOrElse(throw new RuntimeException(s"No content received in response.")) match {
        case content: StringContent => content.value
        case content => throw new RuntimeException(s"$content not supported")
      }
      if (response.status.isSuccess) {
        decode[Response](responseJson) match {
          case Left(error) => throw new RuntimeException(s"JSON decoding error: $responseJson", error)
          case Right(result) => result
        }
      } else {
        errorHandler(response)
      }
    }
  }

  /**
    * Disposes and cleans up this client instance.
    */
  def dispose(): Unit = asyncClient.close()
}