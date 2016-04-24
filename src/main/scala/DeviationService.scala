import java.io.IOException

import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.{ActorMaterializer, Materializer}
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

import scala.concurrent.{ExecutionContextExecutor, Future}
import spray.json.DefaultJsonProtocol

trait Protocols extends DefaultJsonProtocol {
  implicit val stopLocationFormat = jsonFormat6(StopLocation.apply)
  implicit val locationListFormat = jsonFormat2(LocationList.apply)
  implicit val nearestLocationFormat = jsonFormat1(NearestLocation.apply)
}

trait Service extends Protocols {
  implicit val system: ActorSystem
  implicit def executor: ExecutionContextExecutor
  implicit val materializer: Materializer

  def config: Config
  lazy val key = config.getString("services.nearbystops.key")

  val logger: LoggingAdapter

  lazy val connectionFlow: Flow[HttpRequest, HttpResponse, Any] =
    Http().outgoingConnection(config.getString("services.sl.server"),
      config.getInt("services.sl.port"))

  def httpRequest(request: HttpRequest): Future[HttpResponse] = {
    logger.info(s"Requesting path ${request.uri}")
    Source.single(request).via(connectionFlow).runWith(Sink.head)
  }

  def fetchNearByStops(coord: Coordinate): Future[Either[String, Array[StopLocation]]] =
    httpRequest(NearByStops.getRequest(coord))
      .flatMap { response =>
      response.status match {
        case OK => Unmarshal(response.entity).to[NearestLocation].map(NearByStops.getStopLocation(_))
        case BadRequest => Future.successful(Left(s"$coord: incorrect Coordinates"))
        case _ => Unmarshal(response.entity).to[String].flatMap { entity =>
          val error = s"SL nearbystops API request failed with status code ${response.status} and entity $entity"
          logger.error(error)
          Future.failed(new IOException(error))
        }
      }
    }

  def calculateDeviation(coord: Coordinate): ToResponseMarshallable =
    fetchNearByStops(coord).map[ToResponseMarshallable] {
      case Right(stopLocation) => stopLocation
      case Left(errorMessage) => BadRequest -> errorMessage
    }

  val routes = logRequestResult("deviation-service") {
    pathPrefix("deviation") {
      (get & path(Segment)) { arg =>
        complete {
          try {
            calculateDeviation(Coordinate(arg))
          } catch {
            case ex: IllegalArgumentException => BadRequest -> ex.getMessage
          }
        }
      }
    }
  }
}

object DeviationService extends App with Service {
  override implicit val system = ActorSystem()
  override implicit val executor = system.dispatcher
  override implicit val materializer = ActorMaterializer()

  override val config = ConfigFactory.load()
  override val logger = Logging(system, getClass)

  Http().bindAndHandle(routes, config.getString("http.interface"), config.getInt("http.port"))
}
