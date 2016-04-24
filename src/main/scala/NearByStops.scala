import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.model.HttpRequest
import com.typesafe.config.ConfigFactory

case class StopLocation(idx: String, name: String, id: String, lat: String, lon: String, dist: String)
case class LocationList(noNamespaceSchemaLocation: String, StopLocation: Array[StopLocation])
case class NearestLocation(LocationList: Option[LocationList])

object NearByStops {

  val config = ConfigFactory.load()
  lazy val key = config.getString("services.nearbystops.key")

  def getRequest(coord: Coordinate): HttpRequest = RequestBuilding.Get(
    s"/api2/nearbystops.json?key=${key}&originCoordLat=${coord.lat}&originCoordLong=${coord.long}")

  def getStopLocation(nearestLocation: NearestLocation): Either[String, Array[StopLocation]] =
    nearestLocation.LocationList match {
      case Some(locationList) => Right(locationList.StopLocation)
      case None => Left("SL cannot HTTP codes, this was not a guilty request.")
    }

}
