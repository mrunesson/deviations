import java.time.OffsetDateTime

import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.model.HttpRequest
import com.typesafe.config.ConfigFactory


case class Deviation(Created: OffsetDateTime,
                     MainNews: Boolean,
                     Header: String,
                     Details: String,
                     Scope: String,
                     FromDateTime: String,
                     UpToDateTime: String,
                     Updated: OffsetDateTime)

case class Deviations(StatusCode: Int, Message: Option[String], ResponseData: Array[Deviation])

object Deviations {

  val config = ConfigFactory.load()
  lazy val key = config.getString("services.deviations.key")

  def getRequest(siteId: String): HttpRequest =
    RequestBuilding.Get(s"/api2/deviations.json?key=$key&siteId=$siteId")

  def getDeviationArray(deviations: Deviations): Array[Deviation] =
    deviations.ResponseData
}

case class DeviationsException(msg: String) extends Exception(msg)


