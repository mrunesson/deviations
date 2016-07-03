import java.time.OffsetDateTime

import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.Http
import akka.http.scaladsl.coding.Gzip
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
import spray.json.{DefaultJsonProtocol, DeserializationException, JsString, JsValue, JsonFormat}

trait Protocols extends DefaultJsonProtocol {
  implicit object OffsetDateTimeJsonFormat extends JsonFormat[OffsetDateTime] {
    def write(x: OffsetDateTime) = JsString(x.toString)
    def read(value: JsValue) = value match {
      case JsString(x) =>
        try {
          OffsetDateTime.parse(x)
        } catch {
          case _: Throwable =>
            throw new DeserializationException(
              "Expected OffsetDateTime as '2016-04-05T10:12:03.62+02:00', but got " + x)
        }
      case x => throw new DeserializationException(
        "Expected OffsetDateTime as '2016-04-05T10:12:03.62+02:00', but got " + x)
    }
  }

  implicit val stopLocationFormat = jsonFormat6(StopLocation.apply)
  implicit val locationListFormat = jsonFormat2(LocationList.apply)
  implicit val nearestLocationFormat = jsonFormat1(NearestLocation.apply)
  implicit val deviationFormat = jsonFormat8(Deviation.apply)
  implicit val deviationsFormat = jsonFormat3(Deviations.apply)
}

trait Service extends Protocols {
  implicit val system: ActorSystem
  implicit def executor: ExecutionContextExecutor
  implicit val materializer: Materializer

  def config: Config
  val logger: LoggingAdapter

  lazy val connectionFlow: Flow[HttpRequest, HttpResponse, Any] =
    Http().outgoingConnection(config.getString("services.sl.server"),
      config.getInt("services.sl.port"))

  def httpRequest(request: HttpRequest): Future[HttpResponse] = {
    logger.info(s"Requesting path ${request.uri}")
    Source.single(request).via(connectionFlow).runWith(Sink.head)
  }

  def fetchNearByStops(coord: Coordinate): Future[Array[StopLocation]] =
    httpRequest(NearByStops.getRequest(coord))
      .flatMap { response =>
        response.status match {
          case OK => Unmarshal(response.entity).to[NearestLocation].map(NearByStops.getStopLocation(_))
          case BadRequest => throw new CoordinateException(s"$coord: incorrect Coordinates")
          case _ => Unmarshal(response.entity).to[String].flatMap { entity =>
            val error = s"SL nearbystops API request failed with status code ${response.status} " +
              s"and entity ${entity}"
            logger.error(error)
            throw new HttpResponseException(Option(response), error)
          }
        }
      }

  def fetchDeviations(stopLocation: StopLocation): Future[Array[Deviation]] =
    httpRequest(Deviations.getRequest(NearByStops.getSiteId(stopLocation)))
      .flatMap { response =>
        response.status match {
          case OK => Unmarshal(response.entity).to[Deviations].map(Deviations.getDeviationArray(_))
          case BadRequest => throw new DeviationsException(s"${stopLocation.id}: incorrect site Id")
          case _ => Unmarshal(response.entity).to[String].flatMap { entity =>
            val error = s"SL deviations API request failed with status code ${response.status} " +
              s"and entity $entity"
            logger.error(error)
            throw new HttpResponseException(Option(response), error)
          }
        }
      }

  def fetchDeviations(stops: Array[StopLocation]): Future[Array[Deviation]] = {
    val asyncs: Seq[Future[Array[Deviation]]] = stops.map(x => fetchDeviations(x))

    Future.sequence(asyncs).map(r => r.fold(Array[Deviation]()){
      (s:Array[Deviation], i:Array[Deviation]) => s ++ i
    }).flatMap(x => Future(x.toSet.toArray))
  }

  def calculateDeviation(coord: Coordinate): ToResponseMarshallable =
    fetchNearByStops(coord) flatMap {
      fetchDeviations(_)
    }

  val routes =
    logRequestResult("deviation-service") {
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
