import akka.http.scaladsl.model.HttpResponse

case class HttpResponseException(response: Option[HttpResponse], msg: String) extends Exception(msg)
