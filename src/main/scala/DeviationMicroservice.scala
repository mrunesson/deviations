import java.io.IOException

import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding
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

case class Coordinate(lat: Double, long: Double)

case class StopLocation(idx: String, name: String, id: String, lat: String, lon: String, dist: String)
case class LocationList(noNamespaceSchemaLocation: String, StopLocation: Array[StopLocation])
case class NearestLocation(LocationList: Option[LocationList])

case class Deviation(deviation: String)

trait Protocols extends DefaultJsonProtocol {
  implicit val deviationFormat = jsonFormat1(Deviation.apply)
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

  def round(x: Double, n: Int) = ((x * math.pow(10,n) + 0.5).floor / math.pow(10,n))

  lazy val connectionFlow: Flow[HttpRequest, HttpResponse, Any] =
    Http().outgoingConnection(config.getString("services.sl.server"),
      config.getInt("services.sl.port"))

  def httpRequest(request: HttpRequest): Future[HttpResponse] = {
    logger.info(s"Requesting ${request.uri}")
    Source.single(request).via(connectionFlow).runWith(Sink.head)
  }

  def fetchCloseStops(coord: Coordinate): Future[Either[String, Array[StopLocation]]] = {
    httpRequest(RequestBuilding.Get(
        s"/api2/nearbystops.json?key=${key}&originCoordLat=${coord.lat}&originCoordLong=${coord.long}"))
      .flatMap { response =>
      response.status match {
        case OK => Unmarshal(response.entity).to[NearestLocation].map(_.LocationList match {
          case Some(locationList) => Right(locationList.StopLocation)
          case None => Left("SL cannot HTTP codes.")
        })
        case BadRequest => Future.successful(Left(s"$coord: incorrect Coordinates"))
        case _ => Unmarshal(response.entity).to[String].flatMap { entity =>
          val error = s"SL nearbystops API request failed with status code ${response.status} and entity $entity"
          logger.error(error)
          Future.failed(new IOException(error))
        }
      }
    }
  }

  def calculateDeviation(coord: Coordinate): ToResponseMarshallable =
    fetchCloseStops(coord).map[ToResponseMarshallable] {
      case Right(stopLocation) => stopLocation
      case Left(errorMessage) => BadRequest -> errorMessage
    }

  def argumentToCoordinate(s: String): Either[String, Coordinate] =
    s.split(",") match {
      case Array(f1, f2) => Right(Coordinate.tupled((round(f1.toDouble,4), round(f2.toDouble, 4))))
      case _  => Left(s"Incorrect coordinate $s")
    }

  val routes = {
    logRequestResult("deviation-microservice") {
      pathPrefix("deviation") {
        (get & path(Segment)) { arg =>
          complete {
            argumentToCoordinate(arg).fold(
              error => BadRequest -> error,
              success => calculateDeviation(success)
            )
          }
        }
      }
    }
  }
}

object DeviationMicroservice extends App with Service {
  override implicit val system = ActorSystem()
  override implicit val executor = system.dispatcher
  override implicit val materializer = ActorMaterializer()

  override val config = ConfigFactory.load()
  override val logger = Logging(system, getClass)

  Http().bindAndHandle(routes, config.getString("http.interface"), config.getInt("http.port"))
}
